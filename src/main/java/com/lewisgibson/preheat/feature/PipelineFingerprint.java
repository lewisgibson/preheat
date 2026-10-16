package com.lewisgibson.preheat.feature;

import com.lewisgibson.preheat.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Keeping pipelines depends on more than the shapes the mixin check sees: {@code Reactivation} redoes what a new
 * pipeline's constructor sets up globally, and dropping one mirrors {@code destroy()}. This fingerprint captures exactly
 * that: the global rendering settings the constructor sets, the fields it creates, what {@code destroy()} releases, and
 * what {@code PipelineManager.preparePipeline} does. A change to any of them in a new Iris gives a new fingerprint,
 * and keeping pipelines stays off until that Iris has been checked and its fingerprint added to {@link #KNOWN}.
 * Unrelated changes inside Iris leave it as it is.
 */
public final class PipelineFingerprint {
    static final String PIPELINE = "net/irisshaders/iris/pipeline/IrisRenderingPipeline";
    static final String MANAGER = "net/irisshaders/iris/pipeline/PipelineManager";
    static final String SETTINGS = "net/irisshaders/iris/shaderpack/materialmap/WorldRenderingSettings";

    /** Fingerprints of the Iris versions checked against, written by CompatibilityMatrixTest. */
    static final Set<String> KNOWN = Set.of(
            // Iris 1.11.4 (26.2) to 1.11.7 (26.3), from build/reports/compat/fingerprints.txt.
            "4b564a0bc212");

    /** Set to {@code true} to keep pipelines on an Iris whose fingerprint is not known (at the player's own risk). */
    static final String TRUST_PROPERTY = "preheat.trustUnknownIris";

    private PipelineFingerprint() {}

    /** The fingerprint of the Iris in {@code source}, or null when a class or method it covers is missing. */
    public static @Nullable String of(ShapeCheck.ClassSource source) {
        ClassNode pipeline = source.get(PIPELINE);
        ClassNode manager = source.get(MANAGER);
        if (pipeline == null || manager == null) {
            return null;
        }
        MethodNode init = method(pipeline, "<init>");
        MethodNode destroy = method(pipeline, "destroy");
        MethodNode prepare = method(manager, "preparePipeline");
        if (init == null || destroy == null || prepare == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add("init sets " + calls(init, SETTINGS));
        parts.add("init creates " + fields(init, PIPELINE, true));
        parts.add("destroy releases " + fields(destroy, PIPELINE, false) + " " + calls(destroy, null));
        parts.add("prepare " + calls(prepare, null));
        byte[] hash = Sha256.of(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, 6);
    }

    /** Null when keeping pipelines may run on this Iris, else why not. */
    public static @Nullable String problem(ShapeCheck.ClassSource source) {
        String fp = of(source);
        if (fp == null || KNOWN.contains(fp) || Boolean.getBoolean(TRUST_PROPERTY)) {
            // A missing class or method is reported by the shape check already.
            return null;
        }
        return "this Iris builds or destroys pipelines in a way Preheat has not been checked against (fingerprint " + fp
                + "; -D" + TRUST_PROPERTY + "=true overrides)";
    }

    static @Nullable MethodNode method(ClassNode c, String name) {
        for (MethodNode m : c.methods) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }

    /** The distinct methods called (owner.name), only on {@code owner} when given, sorted. */
    static String calls(MethodNode m, @Nullable String owner) {
        Set<String> out = new TreeSet<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode call && (owner == null || call.owner.equals(owner))) {
                out.add(call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name);
            }
        }
        return String.join(",", out);
    }

    /** The distinct fields of {@code owner} written (or read) in {@code m}, sorted. */
    static String fields(MethodNode m, String owner, boolean written) {
        Set<String> out = new TreeSet<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof FieldInsnNode f
                    && f.owner.equals(owner)
                    && (written ? f.getOpcode() == Opcodes.PUTFIELD : f.getOpcode() == Opcodes.GETFIELD)) {
                out.add(f.name);
            }
        }
        return String.join(",", out);
    }
}
