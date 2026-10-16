package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class PipelineFingerprintTest {
    private static ClassNode cls(String name, MethodNode... methods) {
        ClassNode c = new ClassNode();
        c.name = name;
        for (MethodNode m : methods) {
            c.methods.add(m);
        }
        return c;
    }

    private static MethodNode method(String name, String... settings) {
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
        for (String s : settings) {
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PipelineFingerprint.SETTINGS, s, "()V"));
        }
        m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, PipelineFingerprint.PIPELINE, "renderTargets", "I"));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    private static ShapeCheck.ClassSource source(String... initSettings) {
        Map<String, ClassNode> classes = Map.of(
                PipelineFingerprint.PIPELINE,
                cls(PipelineFingerprint.PIPELINE, method("<init>", initSettings), method("destroy")),
                PipelineFingerprint.MANAGER,
                cls(PipelineFingerprint.MANAGER, method("preparePipeline")));
        return classes::get;
    }

    @Test
    void aNewGlobalSettingInTheConstructorIsANewFingerprintAndTurnsKeepingOff() {
        String before = PipelineFingerprint.of(source("setVertexFormat"));
        String after = PipelineFingerprint.of(source("setVertexFormat", "setSomethingNew"));
        assertNotNull(before);
        assertNotEquals(before, after);
        assertEquals(before, PipelineFingerprint.of(source("setVertexFormat")), "stable");
        String why = PipelineFingerprint.problem(source("setVertexFormat", "setSomethingNew"));
        assertNotNull(why);
        assertTrue(why.contains(after));
    }

    @Test
    void aTrustedOrMissingIrisIsNotAProblemHere() {
        System.setProperty(PipelineFingerprint.TRUST_PROPERTY, "true");
        try {
            assertNull(PipelineFingerprint.problem(source("setX")));
        } finally {
            System.clearProperty(PipelineFingerprint.TRUST_PROPERTY);
        }
        ShapeCheck.ClassSource empty = name -> null;
        assertNull(PipelineFingerprint.of(empty));
        assertNull(PipelineFingerprint.problem(empty), "the shape check reports missing classes");
        ShapeCheck.ClassSource noMethods = Map.of(
                PipelineFingerprint.PIPELINE, cls(PipelineFingerprint.PIPELINE),
                PipelineFingerprint.MANAGER, cls(PipelineFingerprint.MANAGER))::get;
        assertNull(PipelineFingerprint.of(noMethods));
    }

    @Test
    void eachClassAndMethodItCoversMustBeThere() {
        ClassNode manager = cls(PipelineFingerprint.MANAGER, method("preparePipeline"));
        assertNull(PipelineFingerprint.of(
                Map.of(PipelineFingerprint.PIPELINE, cls(PipelineFingerprint.PIPELINE, method("<init>")))::get));
        assertNull(PipelineFingerprint.of(Map.of(
                PipelineFingerprint.PIPELINE,
                cls(PipelineFingerprint.PIPELINE, method("<init>")),
                PipelineFingerprint.MANAGER,
                manager)::get));
        assertNull(PipelineFingerprint.of(Map.of(
                PipelineFingerprint.PIPELINE,
                cls(PipelineFingerprint.PIPELINE, method("<init>"), method("destroy")),
                PipelineFingerprint.MANAGER,
                cls(PipelineFingerprint.MANAGER))::get));
    }
}
