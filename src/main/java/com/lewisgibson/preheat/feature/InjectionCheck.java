package com.lewisgibson.preheat.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks, after a mixin was applied, that each of its injectors is actually called from the target. Injectors are
 * declared with {@code require = 0} (the mixin config's default), so a point another mod already changed does not stop
 * the game: the injector simply finds nothing, and this check turns that feature off instead. The startup shape check
 * reads the classes before any mod transformed them, so it cannot see this.
 */
public final class InjectionCheck {
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;");

    private InjectionCheck() {}

    /** The injectors of {@code mixin} that nothing in {@code target} calls after applying it. */
    public static List<String> notPlaced(ClassNode mixin, ClassNode target) {
        Set<String> called = new HashSet<>();
        for (MethodNode m : target.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof MethodInsnNode call && call.owner.equals(target.name)) {
                    called.add(call.name);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (MethodNode handler : mixin.methods) {
            if (!isInjector(handler)) {
                continue;
            }
            boolean placed = false;
            for (String name : called) {
                if (name.equals(handler.name) || name.endsWith("$" + handler.name)) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                missing.add(handler.name);
            }
        }
        return missing;
    }

    private static boolean isInjector(MethodNode m) {
        return anyInjector(m.visibleAnnotations) || anyInjector(m.invisibleAnnotations);
    }

    private static boolean anyInjector(@Nullable List<AnnotationNode> annotations) {
        if (annotations != null) {
            for (AnnotationNode a : annotations) {
                if (INJECTORS.contains(a.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
