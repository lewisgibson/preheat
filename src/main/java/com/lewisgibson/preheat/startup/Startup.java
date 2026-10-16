package com.lewisgibson.preheat.startup;

import com.lewisgibson.preheat.keep.PipelineManagerExt;
import com.lewisgibson.preheat.progress.BuildProgress;
import java.util.Set;
import net.irisshaders.iris.Iris;

/**
 * The end of the game's loading: Iris builds the overworld's pipeline from the first title screen's init
 * ({@code Iris.onLoadingComplete}), and Preheat builds the other dimensions it is set to prepare right after it
 * ({@link StartupBuilds}). Only the calls into Iris and the loading screen are here.
 */
public final class Startup {
    private static final StartupBuilds BUILDS = new StartupBuilds(
            () -> Iris.getCurrentPack().isPresent(),
            () -> Iris.getCurrentPack().map(p -> p.getDimensionMap().keySet()).orElse(Set.of()),
            d -> manager().preheat$isBuilt(d),
            ds -> manager().preheat$prebuild(ds),
            BuildProgress::openWindow,
            BuildProgress::closeWindow);

    private Startup() {}

    private static PipelineManagerExt manager() {
        return (PipelineManagerExt) Iris.getPipelineManager();
    }

    /** Before Iris's own build. */
    public static void begin() {
        BUILDS.begin();
    }

    /** After Iris's own build. */
    public static void end() {
        BUILDS.end();
    }
}
