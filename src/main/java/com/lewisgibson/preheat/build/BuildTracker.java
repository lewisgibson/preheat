package com.lewisgibson.preheat.build;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.progress.BuildProgress;
import java.util.function.BooleanSupplier;

/**
 * The game's one {@link BuildTracking}, wired to the shader cache and the loading screen. Every compile, link and
 * cached load that ends calls its timer's pulse.
 */
public final class BuildTracker {
    private static final BuildTracking TRACKING = new BuildTracking(
            System::nanoTime,
            ProgramBinaries::beginBuild,
            ProgramBinaries::endBuild,
            BuildProgress::buildStarted,
            BuildProgress::pulse,
            BuildProgress::buildEnded);

    public static final BuildTimer TIMER = TRACKING.timer();

    private BuildTracker() {}

    /** A pipeline build for {@code dimension} begins (a nested begin is ignored). */
    public static void begin(String dimension) {
        TRACKING.begin(dimension);
    }

    public static void programQuery(int pname, boolean starting) {
        TRACKING.programQuery(pname, starting);
    }

    public static void prepareStarted(Object dimension, boolean built, BooleanSupplier packOn) {
        TRACKING.prepareStarted(dimension, built, packOn);
    }

    /** The build ends: the split and the cache's part of it go to the log in one line. */
    public static void end() {
        TRACKING.end();
    }
}
