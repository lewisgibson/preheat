package com.lewisgibson.preheat.feature;

import com.lewisgibson.preheat.Sha256;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Saving Iris's patches rests on how Iris patches, which the mixin check cannot see: that {@code TransformPatcher}
 * keeps a session-long cache of patches keyed by {@code CacheKey} (so Iris itself treats a patch as a function of
 * that key), what {@code CacheKey} is built from, and that {@code transformInternal} assigns the parameters' {@code name}
 * and {@code type} before reading them (the two fields {@code PatchKey} leaves out). This fingerprint captures those,
 * and the patch cache stays off on an Iris whose fingerprint has not been checked and added to {@link #KNOWN}.
 */
public final class PatchFingerprint {
    static final String PATCHER = "net/irisshaders/iris/pipeline/transform/TransformPatcher";
    static final String KEY = PATCHER + "$CacheKey";
    static final String PARAMETERS = "net/irisshaders/iris/pipeline/transform/parameter/Parameters";

    /** Fingerprints of the Iris versions checked against, written by CompatibilityMatrixTest. */
    static final Set<String> KNOWN = Set.of(
            // Iris 1.11.4 (26.2) to 1.11.7 (26.3), from build/reports/compat/fingerprints.txt.
            "020b90be0563");

    private PatchFingerprint() {}

    /** The fingerprint of the Iris in {@code source}, or null when a class or method it covers is missing. */
    public static @Nullable String of(ShapeCheck.ClassSource source) {
        ClassNode patcher = source.get(PATCHER);
        ClassNode key = source.get(KEY);
        if (patcher == null || key == null) {
            return null;
        }
        MethodNode transform = PipelineFingerprint.method(patcher, "transform");
        MethodNode compute = PipelineFingerprint.method(patcher, "transformCompute");
        MethodNode internal = PipelineFingerprint.method(patcher, "transformInternal");
        MethodNode keyInit = PipelineFingerprint.method(key, "<init>");
        if (transform == null || compute == null || internal == null || keyInit == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add("transform " + PipelineFingerprint.calls(transform, null));
        parts.add("transformCompute " + PipelineFingerprint.calls(compute, null));
        parts.add("key " + PipelineFingerprint.fields(keyInit, KEY, true) + " "
                + PipelineFingerprint.calls(keyInit, null));
        parts.add("internal assigns " + PipelineFingerprint.fields(internal, PARAMETERS, true));
        byte[] hash = Sha256.of(String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, 6);
    }

    /** Null when saved patches may be used with this Iris, else why not. */
    public static @Nullable String problem(ShapeCheck.ClassSource source) {
        String fp = of(source);
        if (fp == null) {
            // Nothing of Preheat's refers to CacheKey or transformInternal, so the shape check would not notice them
            // gone.
            return "this Iris no longer patches shaders the way Preheat was checked against (TransformPatcher changed)";
        }
        if (KNOWN.contains(fp)) {
            return null;
        }
        return "this Iris patches shaders in a way Preheat has not been checked against (fingerprint " + fp + ")";
    }
}
