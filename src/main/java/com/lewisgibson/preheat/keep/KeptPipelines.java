package com.lewisgibson.preheat.keep;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Keeping each dimension's pipeline across dimension changes: the decisions {@code PipelineManagerMixin} makes about
 * Iris's {@code PipelineManager}, kept out of the mixin so they can be tested against a fake manager. {@code K} is a
 * dimension id and {@code P} a pipeline; the {@link Host} is the manager and everything that touches GL.
 *
 * <p>Iris's own hook on a dimension change destroys every pipeline. On that call path the active pipeline is only put
 * aside, and a kept pipeline of a dimension switched off in the settings is destroyed. When a kept pipeline comes back
 * it is put back in the state a new one starts in, and then inactive kept pipelines are let go while video memory is
 * short ({@link KeptBudget}). If putting a pipeline aside or bringing one back ever throws, keeping turns itself off for
 * the session and the caller falls back to Iris's own behaviour.
 */
// Pipelines are compared by identity on purpose: a kept pipeline is the very object holding its GL resources.
@SuppressWarnings("ReferenceEquality")
public final class KeptPipelines<K, P> {
    /**
     * The pipeline manager, and everything that reaches GL or Iris. PipelineManagerMixin implements it on Iris's class,
     * so every method carries Preheat's prefix and cannot collide with Iris's or another mod's.
     */
    public interface Host<K, P> {
        /** Iris's map of built pipelines per dimension, live. */
        Map<K, P> preheat$pipelines();

        @Nullable
        P preheat$active();

        void preheat$setActive(@Nullable P pipeline);

        /** Releases what Iris's destroy would release, without freeing anything ({@link Reactivation#deactivate}). */
        void preheat$deactivate(P pipeline);

        /** Puts a kept pipeline back in the state a new one starts in ({@link Reactivation#reactivate}). */
        void preheat$reactivate(P pipeline);

        void preheat$resetTextureState();

        /** Destroys one pipeline as Iris's destroyPipeline does for each. */
        void preheat$destroy(K id, P pipeline);

        /** Tells Sodium the pipeline changed, as Iris's own destroy would have. */
        void preheat$signalSodium();

        /** Whether the settings keep this dimension. */
        boolean preheat$dimensionOn(K id);

        /** The Overworld, the Nether and the End, which are let go last. */
        boolean preheat$vanilla(K id);

        /** Free video memory as the card reports it, or null. */
        KeptBudget.@Nullable Memory preheat$memory();

        /**
         * Builds a dimension's pipeline (not adding it anywhere). On failure it deletes the storage buffers the attempt
         * made (Iris's fallback would otherwise delete everyone's) and throws; textures a half-built pipeline made cannot
         * be destroyed safely and are left to the driver.
         */
        P preheat$build(K id) throws Exception;

        long preheat$nanoTime();
    }

    private final Host<K, P> host;
    /** When each kept pipeline was last made active, for letting the one used longest ago go first. */
    private final Map<K, Long> lastUsed = new HashMap<>();
    /** The video memory each kept pipeline took when it was built, in MB (measured where the card reports it). */
    private final Map<K, Long> sizeMb = new HashMap<>();

    private long changeStart;
    private boolean reactivating;
    private boolean activating;
    private KeptBudget.@Nullable Memory beforeBuild;

    public KeptPipelines(Host<K, P> host) {
        this.host = host;
    }

    /**
     * Iris's dimension-change hook is about to destroy every pipeline: puts the active one aside instead. Returns true
     * when Iris's destroy must not run; false when keeping is off or failed (it then destroys them all, as always).
     */
    public boolean onDimensionChange() {
        if (!Features.on(Feature.KEEP_PIPELINES)) {
            return false;
        }
        try {
            changeStart = host.preheat$nanoTime();
            P leaving = host.preheat$active();
            if (leaving != null) {
                host.preheat$deactivate(leaving);
            }
            host.preheat$resetTextureState();
            host.preheat$setActive(null);
            K leavingId = keyOf(leaving);
            if (leavingId != null && !host.preheat$dimensionOn(leavingId)) {
                destroyOne(leavingId, "its dimension is switched off in Preheat's settings");
            }
            host.preheat$signalSodium();
            return true;
        } catch (RuntimeException | LinkageError e) {
            Features.fail(Feature.KEEP_PIPELINES, e);
            changeStart = 0;
            return false;
        }
    }

    /** Before Iris's preparePipeline (which it calls every frame): notes whether a real change is happening. */
    public void beforePrepare(K dimension) {
        reactivating = false;
        activating = false;
        beforeBuild = null;
        if (!Features.on(Feature.KEEP_PIPELINES)) {
            return;
        }
        try {
            Map<K, P> pipelines = host.preheat$pipelines();
            P active = host.preheat$active();
            // Kept pipelines of dimensions switched off since they were built go now, the destination's excepted.
            for (K id : new ArrayList<>(pipelines.keySet())) {
                if (!id.equals(dimension) && pipelines.get(id) != active && !host.preheat$dimensionOn(id)) {
                    destroyOne(id, "its dimension is switched off in Preheat's settings");
                }
            }
            P kept = pipelines.get(dimension);
            reactivating = kept != null && kept != active;
            activating = kept == null || kept != active;
            beforeBuild = kept == null ? host.preheat$memory() : null;
        } catch (RuntimeException | LinkageError e) {
            // Iris calls this every frame: nothing may reach it. Keeping is off; Iris's own destroy takes over.
            Features.fail(Feature.KEEP_PIPELINES, e);
            reactivating = false;
            activating = false;
        }
    }

    /**
     * After Iris's preparePipeline: brings a kept pipeline back and applies the memory budget. Returns false when
     * bringing it back failed; keeping is then off, and the caller destroys everything and builds this dimension afresh.
     */
    public boolean afterPrepare(K dimension) {
        boolean reused = reactivating;
        reactivating = false;
        if (reused) {
            try {
                P active = host.preheat$active();
                if (active != null) {
                    host.preheat$reactivate(active);
                }
            } catch (RuntimeException | LinkageError e) {
                Features.fail(Feature.KEEP_PIPELINES, e);
                changeStart = 0;
                return false;
            }
        }
        boolean changed = activating;
        activating = false;
        if (changed && Features.on(Feature.KEEP_PIPELINES)) {
            Features.guard(Feature.KEEP_PIPELINES, () -> afterActivate(dimension, reused));
        }
        if (changeStart != 0) {
            if (Features.on(Feature.DIAGNOSTICS)) {
                Preheat.LOG.info(
                        "PREHEAT Pipeline for {} ready in {} ms ({}, {} kept)",
                        dimension,
                        Preheat.ms(host.preheat$nanoTime() - changeStart),
                        reused ? "reused" : "built",
                        host.preheat$pipelines().size());
            }
            changeStart = 0;
        }
        return true;
    }

    /** Records use and size, then lets inactive kept pipelines go while video memory is short. */
    private void afterActivate(K dimension, boolean reused) {
        lastUsed.put(dimension, host.preheat$nanoTime());
        KeptBudget.Memory now = host.preheat$memory();
        KeptBudget.Memory before = beforeBuild;
        beforeBuild = null;
        if (!reused && before != null && now != null) {
            sizeMb.put(dimension, Math.max(0, before.availableMb() - now.availableMb()));
        }
        enforceBudget(now);
    }

    private void enforceBudget(KeptBudget.@Nullable Memory memory) {
        List<KeptBudget.Kept<K>> inactive = new ArrayList<>();
        P active = host.preheat$active();
        for (Map.Entry<K, P> e : host.preheat$pipelines().entrySet()) {
            if (e.getValue() != active) {
                K id = e.getKey();
                inactive.add(new KeptBudget.Kept<>(
                        id, host.preheat$vanilla(id), lastUsed.getOrDefault(id, 0L), sizeMb.getOrDefault(id, 0L)));
            }
        }
        for (K id : KeptBudget.toDrop(inactive, memory)) {
            destroyOne(
                    id,
                    memory == null
                            ? "only a few modded dimensions are kept on this graphics card"
                            : "video memory is low (" + memory.availableMb() + " MB free)");
        }
        if (memory != null && Features.on(Feature.DIAGNOSTICS)) {
            Preheat.LOG.info(
                    "PREHEAT Video memory: {} MB free of {} MB, {} pipelines kept ({})",
                    memory.availableMb(),
                    memory.totalMb(),
                    host.preheat$pipelines().size(),
                    sizeMb);
        }
    }

    /** Destroys every kept pipeline but the active one, now. */
    public void dropInactive(String why) {
        P keep = host.preheat$active();
        for (Map.Entry<K, P> e : new ArrayList<>(host.preheat$pipelines().entrySet())) {
            if (e.getValue() != keep) {
                destroyOne(e.getKey(), why);
            }
        }
    }

    /**
     * Builds the pipelines of these dimensions that are not built yet into the kept set, without making any active,
     * stopping at the first that fails or does not fit in video memory. Every attempt, finished or not, set Iris's
     * global state for itself, so {@code active} gets its own back afterwards.
     */
    public void prebuild(List<K> dimensions, P active) {
        if (!Features.on(Feature.KEEP_PIPELINES)) {
            return;
        }
        boolean attempted = false;
        try {
            for (K dimension : dimensions) {
                if (host.preheat$pipelines().containsKey(dimension)) {
                    continue;
                }
                attempted = true;
                if (!prebuildOne(dimension)) {
                    break;
                }
            }
        } finally {
            if (attempted) {
                host.preheat$reactivate(active);
            }
        }
    }

    /**
     * {@link #prebuild} from Iris's active pipeline: only when it is Iris's own ({@code iris}) and a shader pack is in
     * use, as nothing else has a pipeline to give its state back to.
     */
    public void prebuildFrom(List<K> dimensions, @Nullable P active, boolean iris, boolean packOn) {
        if (active != null && iris && packOn) {
            prebuild(dimensions, active);
        }
    }

    /** Deletes the storage buffers a failed build added to Iris's list, newest first, and only those. */
    public static <B> void dropAddedSince(List<B> buffers, int before, Consumer<B> destroy) {
        while (buffers.size() > before) {
            destroy.accept(buffers.remove(buffers.size() - 1));
        }
    }

    private boolean prebuildOne(K dimension) {
        KeptBudget.Memory before = host.preheat$memory();
        long typical = sizeMb.values().stream().mapToLong(Long::longValue).max().orElse(0);
        // Without a reading there is always room (KeptBudget.roomFor), so only a reading can say no.
        if (before != null && !KeptBudget.roomFor(typical, before)) {
            Preheat.LOG.info(
                    "PREHEAT Not preparing {} at startup: video memory is low ({} MB free); it will be built on the first visit",
                    dimension,
                    before.availableMb());
            return false;
        }
        long t0 = host.preheat$nanoTime();
        P built;
        try {
            built = host.preheat$build(dimension);
        } catch (Exception | LinkageError e) {
            Preheat.LOG.warn(
                    "PREHEAT Could not prepare the pipeline for {} at startup; it will be built on the first visit",
                    dimension,
                    e);
            return false;
        }
        host.preheat$pipelines().put(dimension, built);
        try {
            host.preheat$deactivate(built);
        } catch (RuntimeException | LinkageError e) {
            // Half set aside is no state to keep: let it go, and keeping with it.
            destroyOne(dimension, "it could not be set aside after it was prepared");
            Features.fail(Feature.KEEP_PIPELINES, e);
            return false;
        }
        KeptBudget.Memory after = host.preheat$memory();
        if (before != null && after != null) {
            sizeMb.put(dimension, Math.max(0, before.availableMb() - after.availableMb()));
        }
        Preheat.LOG.info(
                "PREHEAT Prepared the pipeline for {} at startup in {} ms ({} kept)",
                dimension,
                Preheat.ms(host.preheat$nanoTime() - t0),
                host.preheat$pipelines().size());
        return true;
    }

    private @Nullable K keyOf(@Nullable P p) {
        if (p == null) {
            return null;
        }
        for (Map.Entry<K, P> e : host.preheat$pipelines().entrySet()) {
            if (e.getValue() == p) {
                return e.getKey();
            }
        }
        return null;
    }

    private void destroyOne(K id, String why) {
        P victim = host.preheat$pipelines().remove(id);
        lastUsed.remove(id);
        sizeMb.remove(id);
        if (victim == null) {
            return;
        }
        long t0 = host.preheat$nanoTime();
        host.preheat$resetTextureState();
        host.preheat$destroy(id, victim);
        Preheat.LOG.info(
                "PREHEAT Dropped the kept pipeline for {} in {} ms ({})",
                id,
                Preheat.ms(host.preheat$nanoTime() - t0),
                why);
    }

    /** The measured size of each kept pipeline, in MB, for the tests. */
    Map<K, Long> sizes() {
        return Map.copyOf(sizeMb);
    }
}
