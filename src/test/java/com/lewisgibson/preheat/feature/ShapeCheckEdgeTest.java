package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** ShapeCheck on small hand-built classes, for the paths the real mixins never take. */
class ShapeCheckEdgeTest {
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final ShapeCheck.ClassSource source = classes::get;

    private ClassNode cls(String name, String superName, String... interfaces) {
        ClassNode c = new ClassNode();
        c.name = name;
        c.superName = superName;
        c.interfaces = List.of(interfaces);
        classes.put(name, c);
        return c;
    }

    private static MethodNode method(ClassNode owner, int access, String name, String desc) {
        MethodNode m = new MethodNode(access, name, desc, null, null);
        owner.methods.add(m);
        return m;
    }

    private static AnnotationNode annotation(String desc, Object... keyValues) {
        AnnotationNode a = new AnnotationNode(desc);
        for (int i = 0; i < keyValues.length; i += 2) {
            a.visit((String) keyValues[i], keyValues[i + 1]);
        }
        return a;
    }

    private static ClassNode mixinOf(String targetDescriptorOrName, boolean byName) {
        ClassNode m = new ClassNode();
        m.name = "test/TheMixin";
        AnnotationNode mixin = new AnnotationNode("Lorg/spongepowered/asm/mixin/Mixin;");
        if (byName) {
            mixin.values = new java.util.ArrayList<>(List.of("targets", List.of(targetDescriptorOrName)));
        } else {
            mixin.values = new java.util.ArrayList<>(List.of("value", List.of(Type.getType(targetDescriptorOrName))));
        }
        m.invisibleAnnotations = new java.util.ArrayList<>(List.of(mixin));
        return m;
    }

    @Test
    void aClassWithoutMixinIsSaidSoAndAMissingTargetIsReported() {
        ClassNode plain = new ClassNode();
        plain.name = "test/Plain";
        assertEquals(List.of("Plain is not a mixin"), ShapeCheck.checkMixin(plain, source));
        assertEquals(List.of("Gone is missing"), ShapeCheck.checkMixin(mixinOf("test.Gone", true), source));
    }

    @Test
    void accessorsAndInvokersReportEveryShapeOfChange() {
        ClassNode target = cls("test/Target", "java/lang/Object");
        FieldNode count = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "count", "I", null, null);
        target.fields.add(count);
        method(target, Opcodes.ACC_PUBLIC, "<init>", "(I)V");
        ClassNode mixin = mixinOf("Ltest/Target;", false);

        MethodNode getter = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "get", "()I");
        getter.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", "value", "count"));
        MethodNode unnamed = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "x", "()I");
        unnamed.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;"));
        MethodNode ctor = method(mixin, Opcodes.ACC_STATIC, "make", "(I)Ltest/Target;");
        ctor.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", "value", "<init>"));
        MethodNode gone = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "()V");
        gone.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", "value", "run"));
        MethodNode invokerUnnamed = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "y", "()V");
        invokerUnnamed.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Invoker;"));
        MethodNode shadowed = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "missing", "()V");
        shadowed.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/Shadow;"));
        MethodNode other = method(mixin, Opcodes.ACC_PRIVATE, "plain", "()V");
        other.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/Unique;"));
        FieldNode shadowField = new FieldNode(Opcodes.ACC_PRIVATE, "absent", "J", null, null);
        shadowField.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/Shadow;"));
        mixin.fields.add(shadowField);

        assertEquals(
                List.of(
                        "Target.absent is missing",
                        "Target.count changed between static and instance",
                        "x names no field",
                        "Target.run()V is missing",
                        "y names no method",
                        "Target.missing()V is missing"),
                ShapeCheck.checkMixin(mixin, source));
    }

    @Test
    void injectorsWithSelectorsDescriptorsAndPoints() {
        ClassNode target = cls("test/Target", "java/lang/Object");
        MethodNode tick = method(target, Opcodes.ACC_PUBLIC, "tick", "()V");
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "test/Gl", "link", "(I)V", false));
        ClassNode mixin = mixinOf("Ltest/Target;", false);
        AnnotationNode at = annotation(
                "Lorg/spongepowered/asm/mixin/injection/At;", "value", "INVOKE", "target", "Ltest/Gl;compile(I)V");
        AnnotationNode head = annotation("Lorg/spongepowered/asm/mixin/injection/At;", "value", "HEAD");
        AnnotationNode invokeWithoutTarget =
                annotation("Lorg/spongepowered/asm/mixin/injection/At;", "value", "INVOKE");
        MethodNode handler = method(
                mixin, Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        AnnotationNode inject = annotation(
                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                "method",
                List.of("tick()V", "nothing"),
                "at",
                List.of(at, head, invokeWithoutTarget));
        AnnotationNode modify =
                annotation("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", "method", List.of("tick"), "at", head);
        AnnotationNode noSelectors = annotation("Lorg/spongepowered/asm/mixin/injection/Redirect;");
        handler.visibleAnnotations = List.of(inject, modify, noSelectors);
        assertEquals(
                List.of("Target.tick no longer calls Gl.compile", "Target.nothing is missing"),
                ShapeCheck.checkMixin(mixin, source));
        assertNull(ShapeCheck.injectionPoint(head, tick));
        assertEquals(List.of(), ShapeCheck.select(target, "tick(I)V"));
    }

    @Test
    void linksReportMissingMethodsFieldsTypesConstantsAndHandles() {
        ClassNode game = cls("net/minecraft/Game", "java/lang/Object", "java/lang/Runnable");
        game.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "here", "I", null, null));
        method(game, Opcodes.ACC_PUBLIC, "run", "()V");
        cls("net/minecraft/Sub", "net/minecraft/Game");
        ClassNode ours = new ClassNode();
        ours.name = "ours/Helper";
        MethodNode m = method(ours, Opcodes.ACC_STATIC, "go", "()V");
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Sub", "run", "()V", false));
        m.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "net/minecraft/Sub", "toString", "()Ljava/lang/String;", false));
        m.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "[Lnet/minecraft/Game;", "clone", "()Ljava/lang/Object;", false));
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/Sub", "gone", "()V", false));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/Sub", "here", "I"));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/Sub", "away", "I"));
        m.instructions.add(new TypeInsnNode(Opcodes.NEW, "net/minecraft/Absent"));
        m.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "[I"));
        m.instructions.add(new LdcInsnNode(Type.getObjectType("net/minecraft/AlsoAbsent")));
        m.instructions.add(new LdcInsnNode(Type.getType("[I")));
        m.instructions.add(new LdcInsnNode("a string"));
        Handle missing = new Handle(Opcodes.H_INVOKEVIRTUAL, "net/minecraft/Game", "vanished", "()V", false);
        Handle field = new Handle(Opcodes.H_GETFIELD, "net/minecraft/Game", "here", "I", false);
        m.instructions.add(new InvokeDynamicInsnNode("x", "()V", missing, missing, field, "constant"));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        assertEquals(
                List.of(
                        "Sub.gone()V is missing",
                        "Sub.away is missing",
                        "Absent is missing",
                        "AlsoAbsent is missing",
                        "Game.vanished()V is missing"),
                ShapeCheck.checkLinks(ours, source, name -> name.startsWith("net/minecraft/")));
    }

    @Test
    void methodsAreFoundThroughSuperclassesInterfacesAndTheJdk() {
        cls("test/Base", "java/util/AbstractList");
        cls("test/Leaf", "test/Base", "test/Iface");
        ClassNode iface = cls("test/Iface", null);
        method(iface, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "fromIface", "()V");
        assertTrue(ShapeCheck.hasMethod(source, "test/Leaf", "fromIface", "()V"));
        assertTrue(ShapeCheck.hasMethod(source, "test/Leaf", "size", "()I"), "AbstractCollection.size, from the JDK");
        assertTrue(
                ShapeCheck.hasMethod(source, "test/Leaf", "forEach", "(Ljava/util/function/Consumer;)V"),
                "Iterable.forEach, a default method the class hierarchy does not declare");
        assertFalse(ShapeCheck.hasMethod(source, "test/Leaf", "forEach", "()V"), "the JDK's own, by descriptor");
        assertFalse(ShapeCheck.hasMethod(source, "test/Leaf", "size", "()J"), "the JDK's own, by descriptor");
        assertFalse(ShapeCheck.hasMethod(source, "test/Leaf", "fromIface", "(I)V"), "by descriptor too");
        assertFalse(ShapeCheck.hasMethod(source, "test/Leaf", "<init>", "()V"), "constructors are not inherited");
        assertFalse(ShapeCheck.hasMethod(source, "test/Missing", "x", "()V"));
        cls("test/Odd", "javax/does/not/Exist");
        assertFalse(ShapeCheck.hasMethod(source, "test/Odd", "x", "()V"), "an unloadable JDK-like type has nothing");
        cls("test/Internal", "jdk/does/not/Exist");
        assertFalse(ShapeCheck.hasMethod(source, "test/Internal", "x", "()V"), "nor does a jdk/ one");
        cls("test/LoopA", "test/LoopB");
        cls("test/LoopB", "test/LoopA");
        assertFalse(ShapeCheck.hasMethod(source, "test/LoopA", "x", "()V"), "a cycle ends");
    }

    @Test
    void helpers() {
        assertEquals("Thing", ShapeCheck.simple("a/b/Thing"));
        assertEquals("Thing", ShapeCheck.simple("Thing"));
        assertNull(ShapeCheck.value(new AnnotationNode("Lx;"), "value"));
        assertEquals(List.of(), ShapeCheck.annotations(null, null));
        assertEquals(List.of(), ShapeCheck.targets(new AnnotationNode("Lx;")));
    }

    @Test
    void aSetterAccessorReadsItsFieldTypeFromItsParameterAndAnyOtherVoidOneHasNone() {
        cls("test/Target", "java/lang/Object").fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "count", "I", null, null));
        ClassNode mixin = mixinOf("Ltest/Target;", false);
        MethodNode setter = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "set", "(I)V");
        setter.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", "value", "count"));
        MethodNode odd = method(mixin, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "odd", "()V");
        odd.visibleAnnotations = List.of(annotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", "value", "count"));
        assertEquals(List.of("Target.count (void) is missing"), ShapeCheck.checkMixin(mixin, source));
    }

    @Test
    void anInjectorWithoutAnAtChecksOnlyItsTargets() {
        ClassNode target = cls("test/Target", "java/lang/Object");
        method(target, Opcodes.ACC_PUBLIC, "tick", "()V");
        ClassNode mixin = mixinOf("Ltest/Target;", false);
        MethodNode handler = method(
                mixin, Opcodes.ACC_PRIVATE, "h", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V");
        handler.visibleAnnotations =
                List.of(annotation("Lorg/spongepowered/asm/mixin/injection/Inject;", "method", List.of("tick")));
        assertEquals(List.of(), ShapeCheck.checkMixin(mixin, source));
    }

    @Test
    void anInjectHandlerThatNoLongerFitsItsTargetSaysHow() {
        String ci = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
        String cir = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
        MethodNode instanceHandler = new MethodNode(Opcodes.ACC_PRIVATE, "h", "(" + ci + ")V", null, null);
        MethodNode staticTarget = new MethodNode(Opcodes.ACC_STATIC, "tick", "()V", null, null);
        assertEquals("it is now static", ShapeCheck.injectSignature(instanceHandler, staticTarget));

        MethodNode staticHandler = new MethodNode(Opcodes.ACC_STATIC, "h", "(" + ci + ")V", null, null);
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        assertNull(ShapeCheck.injectSignature(staticHandler, constructor), "a constructor hook may be static");

        MethodNode returnable = new MethodNode(Opcodes.ACC_PRIVATE, "h", "(" + cir + ")V", null, null);
        MethodNode voidTarget = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        assertEquals("it no longer returns a value", ShapeCheck.injectSignature(returnable, voidTarget));

        MethodNode withInt = new MethodNode(Opcodes.ACC_PRIVATE, "h", "(I" + ci + ")V", null, null);
        MethodNode takesLong = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "(J)V", null, null);
        assertEquals("its parameters are now (J)V", ShapeCheck.injectSignature(withInt, takesLong));
        MethodNode takesInt = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "(I)V", null, null);
        assertNull(ShapeCheck.injectSignature(withInt, takesInt));
    }

    @Test
    void anInvokePointMatchesOwnerNameAndDescriptor() {
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "test/Gl", "link", "(I)V", false));
        tick.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "test/Other", "link", "(J)V", false));
        AnnotationNode sameNameOtherDescriptor = annotation(
                "Lorg/spongepowered/asm/mixin/injection/At;", "value", "INVOKE", "target", "Ltest/Gl;link(J)V");
        assertEquals("calls Gl.link", ShapeCheck.injectionPoint(sameNameOtherDescriptor, tick));
    }

    @Test
    void lookupsCopeWithHandMadeClassesAndMissingParents() {
        ClassNode noInterfaces = cls("test/NoInterfaces", "test/GoneParent");
        noInterfaces.interfaces = null;
        method(noInterfaces, Opcodes.ACC_PUBLIC, "here", "()V");
        assertTrue(ShapeCheck.hasMethod(source, "test/NoInterfaces", "here", "()V"));
        assertFalse(ShapeCheck.hasMethod(source, "test/NoInterfaces", "elsewhere", "()V"), "the parent is not there");

        ClassNode root = cls("net/minecraft/Root", null);
        root.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "value", "I", null, null));
        ClassNode ours = new ClassNode();
        ours.name = "ours/Helper";
        MethodNode m = method(ours, Opcodes.ACC_STATIC, "go", "()V");
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/Root", "value", "I"));
        m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/Root", "value", "J"));
        assertEquals(
                List.of("Root.value is missing"),
                ShapeCheck.checkLinks(ours, source, name -> name.startsWith("net/minecraft/")),
                "a field is found by name and type, and the search stops at a class with no parent");
    }
}
