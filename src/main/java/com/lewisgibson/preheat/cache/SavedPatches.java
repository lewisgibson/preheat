package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Saved patches: before Iris patches a program's stages ({@code TransformPatcher.transform}), the result saved by an
 * earlier launch is used if there is one; otherwise Iris patches as always and the result is saved. Render thread only,
 * and only while the shader cache is in use. Nothing here ever throws into Iris: an error turns this feature off
 * ({@link Features#fail}) and Iris patches as it always does. {@link PatchCache} wires it to Iris.
 *
 * @param <T> Iris's stage type
 */
public final class SavedPatches<T extends Enum<T>> {
    // Per thread, so an after() can only ever pair with its own thread's before(); the game has one SavedPatches.
    private static final ThreadLocal<@Nullable String> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<@Nullable Map<String, @Nullable String>> EXPECTED = new ThreadLocal<>();

    private final Class<T> stageType;
    private final BinaryCache cache;
    private final BooleanSupplier onRenderThread;
    private final BooleanSupplier zZeroToOne;
    private final BooleanSupplier debugOptions;
    private final boolean verify;

    private int verified;
    private int differed;
    private @Nullable String mods;

    /**
     * @param stageType Iris's stage type, whose names the saved stages are stored under
     * @param cache the shader cache the patches are saved beside
     * @param onRenderThread whether the caller is on the render thread
     * @param zZeroToOne the render device's depth range, which Iris's patches depend on
     * @param debugOptions whether Iris's debug options are on, which its patches depend on too
     * @param verify with {@code -Dpreheat.verifyPatchCache=true}, a saved patch is not used: Iris patches anyway and the
     *     two are compared, and every difference is logged. The lab and the real-game harness use it to prove saved
     *     patches are exactly what Iris would make.
     */
    public SavedPatches(
            Class<T> stageType,
            BinaryCache cache,
            BooleanSupplier onRenderThread,
            BooleanSupplier zZeroToOne,
            BooleanSupplier debugOptions,
            boolean verify) {
        this.stageType = stageType;
        this.cache = cache;
        this.onRenderThread = onRenderThread;
        this.zZeroToOne = zZeroToOne;
        this.debugOptions = debugOptions;
        this.verify = verify;
    }

    /** {@link #before} for a graphics program's stages; a program with none is Iris's alone. */
    public @Nullable Map<T, String> beforeGraphics(
            String name,
            @Nullable String vertex,
            @Nullable String geometry,
            @Nullable String tessControl,
            @Nullable String tessEval,
            @Nullable String fragment,
            Object parameters) {
        if (vertex == null && geometry == null && tessControl == null && tessEval == null && fragment == null) {
            return null;
        }
        Map<String, @Nullable String> stages = new LinkedHashMap<>();
        stages.put("VERTEX", vertex);
        stages.put("GEOMETRY", geometry);
        stages.put("TESS_CONTROL", tessControl);
        stages.put("TESS_EVAL", tessEval);
        stages.put("FRAGMENT", fragment);
        return before(name, stages, parameters);
    }

    /** {@link #before} for a compute program; one without a source is Iris's alone. */
    public @Nullable Map<T, String> beforeCompute(String name, @Nullable String compute, Object parameters) {
        if (compute == null) {
            return null;
        }
        Map<String, @Nullable String> stages = new LinkedHashMap<>();
        stages.put("COMPUTE", compute);
        return before(name, stages, parameters);
    }

    /** Before Iris patches: the saved result for these stages, or null to let Iris patch (and save what it makes). */
    public @Nullable Map<T, String> before(String name, Map<String, @Nullable String> stages, Object parameters) {
        PENDING.remove();
        EXPECTED.remove();
        try {
            if (!Features.on(Feature.PATCH_CACHE) || !onRenderThread.getAsBoolean()) {
                return null;
            }
            Path dir = cache.usableDir();
            if (dir == null) {
                return null;
            }
            String described = PatchKey.describe(parameters);
            if (described == null) {
                return null;
            }
            String key = key(name, stages, described);
            Map<String, @Nullable String> saved = CachePrefetch.takenPatch(dir, key);
            if (saved == null) {
                saved = PatchedSources.load(dir, key);
            }
            if (verify && saved != null) {
                EXPECTED.set(saved);
                return null;
            }
            Map<T, String> result = saved == null ? null : toIris(saved);
            cache.countPatch(result != null);
            if (result == null) {
                PENDING.set(key);
            } else {
                cache.touchLater(dir.resolve(key + PatchedSources.SUFFIX));
            }
            return result;
        } catch (RuntimeException | LinkageError e) {
            Features.fail(Feature.PATCH_CACHE, e);
            return null;
        }
    }

    /** After Iris patched: saves the result under the key {@link #before} worked out. */
    public void after(@Nullable Map<T, String> result) {
        String key = PENDING.get();
        PENDING.remove();
        Map<String, @Nullable String> wanted = EXPECTED.get();
        EXPECTED.remove();
        if (wanted != null && result != null) {
            verify(wanted, result);
            return;
        }
        if (key == null || result == null) {
            return;
        }
        Features.guard(Feature.PATCH_CACHE, () -> {
            Map<String, @Nullable String> plain = plain(result);
            cache.savePatch(key, () -> PatchedSources.encode(plain));
        });
    }

    private static <T extends Enum<T>> Map<String, @Nullable String> plain(Map<T, String> stages) {
        Map<String, @Nullable String> plain = new LinkedHashMap<>();
        for (Map.Entry<T, String> e : stages.entrySet()) {
            plain.put(e.getKey().name(), e.getValue());
        }
        return plain;
    }

    private void verify(Map<String, @Nullable String> saved, Map<T, String> made) {
        verified++;
        if (!PatchedSources.sameStages(saved, plain(made))) {
            differed++;
            Preheat.LOG.error(
                    "PREHEAT Patch cache check: a saved patch differs from what Iris made ({} of {})",
                    differed,
                    verified);
        } else if (verified % 100 == 0) {
            Preheat.LOG.info(
                    "PREHEAT Patch cache check: {} saved patches match what Iris made, {} differ", verified, differed);
        }
    }

    private String key(String name, Map<String, @Nullable String> stages, String described) {
        List<String> parts = new ArrayList<>(cache.context());
        parts.add("patch-v1");
        parts.add(mods());
        parts.add("zZeroToOne " + zZeroToOne.getAsBoolean());
        parts.add("debug print " + debugOptions.getAsBoolean());
        parts.add(name);
        parts.add(described);
        for (Map.Entry<String, @Nullable String> e : stages.entrySet()) {
            parts.add(e.getKey());
            String v = e.getValue();
            parts.add(v == null ? "\u0000absent" : v);
        }
        return ProgramBinaryStore.key(parts);
    }

    /** Every installed mod and its version, worked out once: a mod added, removed or updated is another key. */
    private synchronized String mods() {
        String m = mods;
        if (m == null) {
            m = ProgramBinaryStore.key(Platform.get().mods());
            mods = m;
            Preheat.LOG.debug("PREHEAT Patch cache: mod list {}", m);
        }
        return m;
    }

    private @Nullable Map<T, String> toIris(Map<String, @Nullable String> saved) {
        Map<T, String> out = new EnumMap<>(stageType);
        for (Map.Entry<String, @Nullable String> e : saved.entrySet()) {
            T type;
            try {
                type = Enum.valueOf(stageType, e.getKey());
            } catch (IllegalArgumentException unknown) {
                return null;
            }
            out.put(type, e.getValue());
        }
        return out;
    }
}
