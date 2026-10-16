package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class InjectionCheckTest {
    private static MethodNode handler(String name, String annotation) {
        MethodNode m = new MethodNode(Opcodes.ACC_PRIVATE, name, "()V", null, null);
        m.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode(annotation)));
        return m;
    }

    @Test
    void anInjectorThatFoundNothingToHookIsReported() {
        ClassNode mixin = new ClassNode();
        mixin.methods.add(handler("preheat$load", "Lorg/spongepowered/asm/mixin/injection/Inject;"));
        mixin.methods.add(handler("preheat$retrievable", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;"));
        mixin.methods.add(handler("preheat$helper", "Lorg/spongepowered/asm/mixin/Unique;"));

        ClassNode target = new ClassNode();
        target.name = "a/Target";
        MethodNode link = new MethodNode(Opcodes.ACC_PUBLIC, "link", "()V", null, null);
        link.instructions.add(
                new MethodInsnNode(Opcodes.INVOKESTATIC, "a/Target", "handler$zza000$preheat$load", "()V"));
        target.methods.add(link);

        assertEquals(List.of("preheat$retrievable"), InjectionCheck.notPlaced(mixin, target));
        link.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "a/Target", "preheat$retrievable", "()V"));
        assertEquals(List.of(), InjectionCheck.notPlaced(mixin, target));
    }

    @Test
    void injectorsAreFoundInEitherAnnotationListAndCallsElsewhereDoNotCount() {
        ClassNode mixin = new ClassNode();
        MethodNode invisible = new MethodNode(Opcodes.ACC_PRIVATE, "preheat$hidden", "()V", null, null);
        invisible.invisibleAnnotations =
                new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Redirect;")));
        mixin.methods.add(invisible);
        MethodNode other = new MethodNode(Opcodes.ACC_PRIVATE, "preheat$plain", "()V", null, null);
        other.invisibleAnnotations =
                new ArrayList<>(List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/Unique;")));
        mixin.methods.add(other);

        ClassNode target = new ClassNode();
        target.name = "a/Target";
        MethodNode link = new MethodNode(Opcodes.ACC_PUBLIC, "link", "()V", null, null);
        link.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "a/Elsewhere", "preheat$hidden", "()V"));
        link.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        target.methods.add(link);
        assertEquals(List.of("preheat$hidden"), InjectionCheck.notPlaced(mixin, target), "a call on another class");
    }
}
