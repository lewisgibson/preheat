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

class PatchFingerprintTest {
    private static ClassNode cls(String name, MethodNode... methods) {
        ClassNode c = new ClassNode();
        c.name = name;
        for (MethodNode m : methods) {
            c.methods.add(m);
        }
        return c;
    }

    private static MethodNode method(String name, String owner, String call) {
        MethodNode m = new MethodNode(Opcodes.ACC_STATIC, name, "()V", null, null);
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Map", call, "()V"));
        m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, "name", "Ljava/lang/String;"));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        return m;
    }

    private static ShapeCheck.ClassSource source(String transformCall) {
        Map<String, ClassNode> classes = Map.of(
                PatchFingerprint.PATCHER,
                cls(
                        PatchFingerprint.PATCHER,
                        method("transform", PatchFingerprint.PATCHER, transformCall),
                        method("transformCompute", PatchFingerprint.PATCHER, "containsKey"),
                        method("transformInternal", PatchFingerprint.PARAMETERS, "get")),
                PatchFingerprint.KEY,
                cls(PatchFingerprint.KEY, method("<init>", PatchFingerprint.KEY, "hashCode")));
        return classes::get;
    }

    @Test
    void aChangeInHowIrisCachesItsPatchesIsANewFingerprintAndTurnsTheCacheOff() {
        String before = PatchFingerprint.of(source("containsKey"));
        String after = PatchFingerprint.of(source("clear"));
        assertNotNull(before);
        assertNotEquals(before, after);
        assertEquals(before, PatchFingerprint.of(source("containsKey")), "stable");
        String why = PatchFingerprint.problem(source("clear"));
        assertNotNull(why);
        assertTrue(why.contains(after));
    }

    @Test
    void aMissingClassOrMethodTurnsTheCacheOff() {
        // Nothing of Preheat's names CacheKey or transformInternal, so the shape check would not see them go.
        ShapeCheck.ClassSource empty = name -> null;
        assertNull(PatchFingerprint.of(empty));
        assertNotNull(PatchFingerprint.problem(empty));
        ShapeCheck.ClassSource noMethods = Map.of(
                PatchFingerprint.PATCHER, cls(PatchFingerprint.PATCHER),
                PatchFingerprint.KEY, cls(PatchFingerprint.KEY))::get;
        assertNull(PatchFingerprint.of(noMethods));
        assertNotNull(PatchFingerprint.problem(noMethods));
    }

    @Test
    void theCheckedIrisVersionsAreKnown() {
        assertTrue(PatchFingerprint.KNOWN.contains("020b90be0563"));
    }

    @Test
    void eachClassAndMethodItCoversMustBeThere() {
        MethodNode transform = method("transform", PatchFingerprint.PATCHER, "get");
        MethodNode compute = method("transformCompute", PatchFingerprint.PATCHER, "get");
        MethodNode internal = method("transformInternal", PatchFingerprint.PARAMETERS, "get");
        ClassNode key = cls(PatchFingerprint.KEY, method("<init>", PatchFingerprint.KEY, "get"));
        ClassNode emptyKey = cls(PatchFingerprint.KEY);
        assertNull(PatchFingerprint.of(Map.of(PatchFingerprint.PATCHER, cls(PatchFingerprint.PATCHER))::get), "no key");
        assertNull(PatchFingerprint.of(Map.of(
                PatchFingerprint.PATCHER, cls(PatchFingerprint.PATCHER, transform), PatchFingerprint.KEY, key)::get));
        assertNull(
                PatchFingerprint.of(Map.of(
                        PatchFingerprint.PATCHER,
                        cls(PatchFingerprint.PATCHER, transform, compute),
                        PatchFingerprint.KEY,
                        key)::get));
        assertNull(PatchFingerprint.of(Map.of(
                PatchFingerprint.PATCHER,
                cls(PatchFingerprint.PATCHER, transform, compute, internal),
                PatchFingerprint.KEY,
                emptyKey)::get));
        assertNotNull(PatchFingerprint.of(Map.of(
                PatchFingerprint.PATCHER,
                cls(PatchFingerprint.PATCHER, transform, compute, internal),
                PatchFingerprint.KEY,
                key)::get));
    }
}
