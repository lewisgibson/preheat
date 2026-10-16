package com.lewisgibson.preheat.build;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

/**
 * One pipeline build at a time: where its time goes ({@link BuildTimer}), the shader cache's numbers for it, and the
 * loading screen it drives, each only where its feature applied. {@link BuildTracker} wires it to the game.
 */
public final class BuildTracking {
    private static final int GL_LINK_STATUS = 0x8B82;

    private final BuildTimer timer;
    private final Runnable cacheBegin;
    private final Supplier<String> cacheEnd;
    private final Consumer<String> started;
    private final IntConsumer pulse;
    private final ObjIntConsumer<String> ended;

    /**
     * @param clock nanoseconds
     * @param cacheBegin the shader cache's numbers start from zero
     * @param cacheEnd the shader cache's part of the build's log line ("" for nothing to say)
     * @param started the loading screen hears a build began, for a dimension
     * @param pulse the loading screen hears how many programs the build has made so far
     * @param ended the loading screen hears a build ended, and its program count
     */
    public BuildTracking(
            LongSupplier clock,
            Runnable cacheBegin,
            Supplier<String> cacheEnd,
            Consumer<String> started,
            IntConsumer pulse,
            ObjIntConsumer<String> ended) {
        this.timer = new BuildTimer(clock, this::pulse);
        this.cacheBegin = cacheBegin;
        this.cacheEnd = cacheEnd;
        this.started = started;
        this.pulse = pulse;
        this.ended = ended;
    }

    /** The timer every compile, link and cached load reports to. */
    public BuildTimer timer() {
        return timer;
    }

    /** A pipeline build for {@code dimension} begins (a nested begin is ignored). */
    public void begin(String dimension) {
        if (timer.active()) {
            return;
        }
        timer.begin(dimension);
        if (Features.available(Feature.PROGRAM_CACHE)) {
            cacheBegin.run();
        }
        if (Features.available(Feature.LOADING_PROGRESS)) {
            Features.guard(Feature.LOADING_PROGRESS, () -> started.accept(dimension));
        }
    }

    /**
     * Iris's preparePipeline began (every frame): it builds when the dimension has no pipeline yet and a shader pack is
     * in use, and that is one tracked build. Nothing more is asked of Iris on the frames it does not build.
     */
    public void prepareStarted(Object dimension, boolean built, BooleanSupplier packOn) {
        if (!built && packOn.getAsBoolean()) {
            begin(String.valueOf(dimension));
        }
    }

    /** A query on a program (GlStateManager.glGetProgrami): asking for the link status is waiting for the link. */
    public void programQuery(int pname, boolean starting) {
        if (pname != GL_LINK_STATUS) {
            return;
        }
        if (starting) {
            timer.enter(BuildTimer.Step.LINK);
        } else {
            timer.exit(BuildTimer.Step.LINK);
        }
    }

    /** The build ends: the split and the cache's part of it go to the log in one line. */
    public void end() {
        String dimension = timer.label();
        if (dimension == null) {
            return;
        }
        int programs = timer.programs();
        String cache = Features.available(Feature.PROGRAM_CACHE) ? cacheEnd.get() : "";
        String split = timer.end();
        if (Features.available(Feature.LOADING_PROGRESS)) {
            Features.guard(Feature.LOADING_PROGRESS, () -> ended.accept(dimension, programs));
        }
        if (Features.on(Feature.DIAGNOSTICS)) {
            Preheat.LOG.info(
                    "PREHEAT Built the {} shaders ({}) in {}{}",
                    KnownDimensions.display(dimension),
                    dimension,
                    split,
                    cache.isEmpty() ? "" : "; " + cache);
        }
    }

    private void pulse() {
        if (Features.available(Feature.LOADING_PROGRESS)) {
            Features.guard(Feature.LOADING_PROGRESS, () -> pulse.accept(timer.programs()));
        }
    }
}
