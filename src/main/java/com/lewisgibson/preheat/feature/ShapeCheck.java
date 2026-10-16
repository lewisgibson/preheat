package com.lewisgibson.preheat.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Reads a mixin class the way Mixin will apply it and checks that every member it names exists, with the right
 * descriptor, in the target classes actually installed: the methods it injects into and the calls its injection points
 * name, the fields and methods it shadows, and the fields and methods its accessors and invokers reach. It also checks
 * that every call Preheat's own helper classes make into Iris, Minecraft, Sodium or Distant Horizons resolves.
 *
 * Nothing here loads a class: it reads class files only, so it runs before any mixin applies and in unit tests
 * against plain jars. Each problem is one plain sentence; an empty list means the mixin will apply cleanly.
 */
public final class ShapeCheck {
    /** Class files by internal name ("net/minecraft/client/Minecraft"); null when the class is not there. */
    @FunctionalInterface
    public interface ClassSource {
        @Nullable
        ClassNode get(String internalName);
    }

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECTION_PACKAGE = "Lorg/spongepowered/asm/mixin/injection/";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String CALLBACK_INFO = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_INFO_RETURNABLE =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    private ShapeCheck() {}

    /** Every reason {@code mixin} would not apply cleanly to the classes in {@code source}. */
    public static List<String> checkMixin(ClassNode mixin, ClassSource source) {
        List<String> problems = new ArrayList<>();
        AnnotationNode mixinAnnotation = find(annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations), MIXIN);
        if (mixinAnnotation == null) {
            problems.add(simple(mixin.name) + " is not a mixin");
            return problems;
        }
        List<String> targets = targets(mixinAnnotation);
        for (String targetName : targets) {
            ClassNode target = source.get(targetName);
            if (target == null) {
                problems.add(simple(targetName) + " is missing");
                continue;
            }
            for (FieldNode f : mixin.fields) {
                if (find(annotations(f.visibleAnnotations, f.invisibleAnnotations), SHADOW) != null
                        && field(target, f.name, f.desc) == null) {
                    problems.add(simple(targetName) + "." + f.name + " is missing");
                }
            }
            for (MethodNode m : mixin.methods) {
                checkMethod(m, target, source, problems);
            }
        }
        return problems;
    }

    private static void checkMethod(MethodNode m, ClassNode target, ClassSource source, List<String> problems) {
        for (AnnotationNode a : annotations(m.visibleAnnotations, m.invisibleAnnotations)) {
            if (a.desc.equals(SHADOW)) {
                if (method(target, m.name, m.desc, source) == null) {
                    problems.add(simple(target.name) + "." + m.name + m.desc + " is missing");
                }
            } else if (a.desc.equals(ACCESSOR)) {
                checkAccessor(m, a, target, problems);
            } else if (a.desc.equals(INVOKER)) {
                checkInvoker(m, a, target, source, problems);
            } else if (a.desc.startsWith(INJECTION_PACKAGE)) {
                checkInjector(m, a, target, problems);
            }
        }
    }

    private static void checkAccessor(MethodNode m, AnnotationNode a, ClassNode target, List<String> problems) {
        if (!(value(a, "value") instanceof String name)) {
            problems.add(m.name + " names no field");
            return;
        }
        Type returns = Type.getReturnType(m.desc);
        Type[] args = Type.getArgumentTypes(m.desc);
        String desc =
                returns.getSort() == Type.VOID && args.length == 1 ? args[0].getDescriptor() : returns.getDescriptor();
        FieldNode f = field(target, name, desc);
        if (f == null) {
            problems.add(
                    simple(target.name) + "." + name + " (" + Type.getType(desc).getClassName() + ") is missing");
        } else if (((f.access & Opcodes.ACC_STATIC) != 0) != ((m.access & Opcodes.ACC_STATIC) != 0)) {
            problems.add(simple(target.name) + "." + name + " changed between static and instance");
        }
    }

    private static void checkInvoker(
            MethodNode m, AnnotationNode a, ClassNode target, ClassSource source, List<String> problems) {
        if (!(value(a, "value") instanceof String name)) {
            problems.add(m.name + " names no method");
            return;
        }
        String desc = "<init>".equals(name)
                ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(m.desc))
                : m.desc;
        if (method(target, name, desc, source) == null) {
            problems.add(simple(target.name) + "." + name + desc + " is missing");
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkInjector(MethodNode handler, AnnotationNode a, ClassNode target, List<String> problems) {
        Object selectors = value(a, "method");
        if (!(selectors instanceof List<?> list)) {
            return;
        }
        List<AnnotationNode> ats = new ArrayList<>();
        Object at = value(a, "at");
        if (at instanceof AnnotationNode one) {
            ats.add(one);
        } else if (at instanceof List<?> many) {
            ats.addAll((List<AnnotationNode>) many);
        }
        for (Object o : list) {
            String selector = (String) o;
            List<MethodNode> matched = select(target, selector);
            if (matched.isEmpty()) {
                problems.add(simple(target.name) + "." + selector + " is missing");
                continue;
            }
            for (MethodNode t : matched) {
                if (a.desc.equals(INJECT)) {
                    String bad = injectSignature(handler, t);
                    if (bad != null) {
                        problems.add(simple(target.name) + "." + t.name + " changed: " + bad);
                    }
                }
                for (AnnotationNode point : ats) {
                    String bad = injectionPoint(point, t);
                    if (bad != null) {
                        problems.add(simple(target.name) + "." + t.name + " no longer " + bad);
                    }
                }
            }
        }
    }

    /** Null when an @Inject handler fits the target method, else what does not fit. */
    static @Nullable String injectSignature(MethodNode handler, MethodNode target) {
        boolean handlerStatic = (handler.access & Opcodes.ACC_STATIC) != 0;
        boolean targetStatic = (target.access & Opcodes.ACC_STATIC) != 0;
        if (handlerStatic != targetStatic && !"<init>".equals(target.name)) {
            return handlerStatic ? "it is no longer static" : "it is now static";
        }
        Type[] h = Type.getArgumentTypes(handler.desc);
        int ci = -1;
        for (int i = 0; i < h.length; i++) {
            if (h[i].getSort() == Type.OBJECT
                    && (h[i].getInternalName().equals(CALLBACK_INFO)
                            || h[i].getInternalName().equals(CALLBACK_INFO_RETURNABLE))) {
                ci = i;
                break;
            }
        }
        if (ci < 0) {
            return "the handler takes no CallbackInfo";
        }
        // A constructor's descriptor always returns void, so it never "returns a value" here.
        boolean returnsValue = Type.getReturnType(target.desc).getSort() != Type.VOID;
        boolean returnable = h[ci].getInternalName().equals(CALLBACK_INFO_RETURNABLE);
        if (returnsValue != returnable) {
            return returnsValue ? "it now returns a value" : "it no longer returns a value";
        }
        Type[] t = Type.getArgumentTypes(target.desc);
        if (ci > 0) {
            if (ci != t.length) {
                return "its parameters are now " + Type.getMethodDescriptor(Type.VOID_TYPE, t);
            }
            for (int i = 0; i < ci; i++) {
                if (!h[i].equals(t[i])) {
                    return "its parameters are now " + Type.getMethodDescriptor(Type.VOID_TYPE, t);
                }
            }
        }
        return null;
    }

    /** Null when the @At finds its place in the target method, else what is missing. */
    static @Nullable String injectionPoint(AnnotationNode at, MethodNode target) {
        String kind = (String) value(at, "value");
        if (!"INVOKE".equals(kind)) {
            return null;
        }
        String ref = (String) value(at, "target");
        if (ref == null) {
            return null;
        }
        int semi = ref.indexOf(';');
        int paren = ref.indexOf('(');
        String owner = ref.substring(1, semi);
        String name = ref.substring(semi + 1, paren);
        String desc = ref.substring(paren);
        for (AbstractInsnNode insn : target.instructions) {
            if (insn instanceof MethodInsnNode mi
                    && mi.owner.equals(owner)
                    && mi.name.equals(name)
                    && mi.desc.equals(desc)) {
                return null;
            }
        }
        return "calls " + simple(owner) + "." + name;
    }

    /**
     * Every reference from {@code ours} into a class {@code foreign} accepts that does not resolve in {@code source}:
     * a class, a method (searched through superclasses and interfaces) or a field.
     */
    public static List<String> checkLinks(ClassNode ours, ClassSource source, Predicate<String> foreign) {
        Set<String> problems = new java.util.LinkedHashSet<>();
        for (MethodNode m : ours.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode mi && foreign.test(owner(mi.owner))) {
                    if (method(source, owner(mi.owner), mi.name, mi.desc) == null) {
                        problems.add(simple(mi.owner) + "." + mi.name + mi.desc + " is missing");
                    }
                } else if (insn instanceof FieldInsnNode fi && foreign.test(fi.owner)) {
                    if (field(source, fi.owner, fi.name, fi.desc) == null) {
                        problems.add(simple(fi.owner) + "." + fi.name + " is missing");
                    }
                } else if (insn instanceof TypeInsnNode ti && foreign.test(owner(ti.desc))) {
                    if (source.get(owner(ti.desc)) == null) {
                        problems.add(simple(ti.desc) + " is missing");
                    }
                } else if (insn instanceof LdcInsnNode ldc
                        && ldc.cst instanceof Type type
                        && type.getSort() == Type.OBJECT
                        && foreign.test(type.getInternalName())
                        && source.get(type.getInternalName()) == null) {
                    problems.add(simple(type.getInternalName()) + " is missing");
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle h
                                && foreign.test(h.getOwner())
                                && h.getTag() >= Opcodes.H_INVOKEVIRTUAL
                                && method(source, h.getOwner(), h.getName(), h.getDesc()) == null) {
                            problems.add(simple(h.getOwner()) + "." + h.getName() + h.getDesc() + " is missing");
                        }
                    }
                }
            }
        }
        return new ArrayList<>(problems);
    }

    /** An array type's element class, or the class itself. */
    private static String owner(String internalName) {
        if (!internalName.startsWith("[")) {
            return internalName;
        }
        Type t = Type.getType(internalName).getElementType();
        return t.getSort() == Type.OBJECT ? t.getInternalName() : "java/lang/Object";
    }

    /** True when the class {@code internalName} has a method with this name and descriptor (declared or inherited). */
    public static boolean hasMethod(ClassSource source, String internalName, String name, String desc) {
        return method(source, internalName, name, desc) != null;
    }

    private static @Nullable MethodNode method(ClassSource source, String owner, String name, String desc) {
        return method(source.get(owner), name, desc, source);
    }

    private static @Nullable MethodNode method(@Nullable ClassNode c, String name, String desc, ClassSource source) {
        Set<String> seen = new HashSet<>();
        Set<String> jdk = new HashSet<>();
        List<ClassNode> queue = new ArrayList<>();
        if (c != null) {
            queue.add(c);
        }
        while (!queue.isEmpty()) {
            ClassNode n = queue.remove(0);
            if (!seen.add(n.name)) {
                continue;
            }
            for (MethodNode m : n.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    return m;
                }
            }
            if ("<init>".equals(name)) {
                return null;
            }
            addIfPresent(queue, jdk, source, n.superName);
            if (n.interfaces != null) {
                for (String i : n.interfaces) {
                    addIfPresent(queue, jdk, source, i);
                }
            }
        }
        // Inherited from a JDK type (Object.toString, Iterable.forEach, ...): ask the JDK itself.
        for (String j : jdk) {
            if (jdkHasMethod(j, name, desc)) {
                return new MethodNode();
            }
        }
        return null;
    }

    private static boolean jdkHasMethod(String internalName, String name, String desc) {
        try {
            Class<?> k = Class.forName(internalName.replace('/', '.'), false, ShapeCheck.class.getClassLoader());
            for (Class<?> c = k; c != null; c = c.getSuperclass()) {
                for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) {
                        return true;
                    }
                }
            }
            for (java.lang.reflect.Method m : k.getMethods()) {
                if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        return false;
    }

    private static @Nullable FieldNode field(ClassSource source, String owner, String name, String desc) {
        ClassNode c = source.get(owner);
        while (c != null) {
            FieldNode f = field(c, name, desc);
            if (f != null) {
                return f;
            }
            c = c.superName == null ? null : source.get(c.superName);
        }
        return null;
    }

    private static @Nullable FieldNode field(ClassNode c, String name, String desc) {
        for (FieldNode f : c.fields) {
            if (f.name.equals(name) && f.desc.equals(desc)) {
                return f;
            }
        }
        return null;
    }

    private static void addIfPresent(
            List<ClassNode> queue, Set<String> jdk, ClassSource source, @Nullable String name) {
        if (name != null && isJdk(name)) {
            jdk.add(name);
        } else if (name != null) {
            ClassNode n = source.get(name);
            if (n != null) {
                queue.add(n);
            }
        }
    }

    private static boolean isJdk(String name) {
        return name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/");
    }

    /** The target methods a Mixin selector names: "name" (every overload) or "name(desc)". */
    static List<MethodNode> select(ClassNode target, String selector) {
        String name = selector;
        String desc = null;
        int paren = selector.indexOf('(');
        if (paren >= 0) {
            name = selector.substring(0, paren);
            desc = selector.substring(paren);
        }
        List<MethodNode> out = new ArrayList<>();
        for (MethodNode m : target.methods) {
            if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) {
                out.add(m);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<String> targets(AnnotationNode mixin) {
        List<String> out = new ArrayList<>();
        Object v = value(mixin, "value");
        if (v instanceof List<?> types) {
            for (Type t : (List<Type>) types) {
                out.add(t.getInternalName());
            }
        }
        Object names = value(mixin, "targets");
        if (names instanceof List<?> strings) {
            for (String s : (List<String>) strings) {
                out.add(s.replace('.', '/'));
            }
        }
        return out;
    }

    /** Mixin's annotations are a mix of runtime-visible (@Inject, @Shadow, @Accessor) and class-retention (@Mixin) ones. */
    static List<AnnotationNode> annotations(
            @Nullable List<AnnotationNode> visible, @Nullable List<AnnotationNode> invisible) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) {
            all.addAll(visible);
        }
        if (invisible != null) {
            all.addAll(invisible);
        }
        return all;
    }

    static @Nullable AnnotationNode find(List<AnnotationNode> annotations, String desc) {
        for (AnnotationNode a : annotations) {
            if (a.desc.equals(desc)) {
                return a;
            }
        }
        return null;
    }

    static @Nullable Object value(AnnotationNode a, String key) {
        if (a.values != null) {
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if (key.equals(a.values.get(i))) {
                    return a.values.get(i + 1);
                }
            }
        }
        return null;
    }

    static String simple(String internalName) {
        String s = internalName.replace('/', '.');
        int dot = s.lastIndexOf('.');
        return dot < 0 ? s : s.substring(dot + 1);
    }
}
