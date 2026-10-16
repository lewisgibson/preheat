package com.lewisgibson.preheat.startup;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.cache.CachePrefetch;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.keep.Prebuild;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

/**
 * What happens around Iris's first build at the end of the game's loading ({@link Startup} wires it to Iris and the
 * loading screen): which builds the loading screen counts, the prebuild of the other dimensions, and the loading screen
 * going away whatever happened.
 */
public final class StartupBuilds {
    private final BooleanSupplier packOn;
    private final Supplier<Set<NamespacedId>> packDimensions;
    private final Predicate<NamespacedId> built;
    private final Consumer<List<NamespacedId>> prebuild;
    private final Consumer<List<String>> openWindow;
    private final Runnable closeWindow;

    /**
     * @param packOn whether a shader pack is in use
     * @param packDimensions the dimensions the pack in use names (asked only when one is)
     * @param built whether a dimension's pipeline is built already
     * @param prebuild builds these dimensions' pipelines into the kept set
     * @param openWindow puts the loading screen up for these builds, in order
     * @param closeWindow takes the loading screen down
     */
    public StartupBuilds(
            BooleanSupplier packOn,
            Supplier<Set<NamespacedId>> packDimensions,
            Predicate<NamespacedId> built,
            Consumer<List<NamespacedId>> prebuild,
            Consumer<List<String>> openWindow,
            Runnable closeWindow) {
        this.packOn = packOn;
        this.packDimensions = packDimensions;
        this.built = built;
        this.prebuild = prebuild;
        this.openWindow = openWindow;
        this.closeWindow = closeWindow;
    }

    /** Before Iris's own build: the loading screen opens for the Overworld and the dimensions prepared after it. */
    public void begin() {
        if (!Features.available(Feature.LOADING_PROGRESS) || !packOn.getAsBoolean()) {
            return;
        }
        Features.guard(Feature.LOADING_PROGRESS, () -> {
            List<String> planned = new ArrayList<>();
            if (!built.test(DimensionId.OVERWORLD)) {
                planned.add(String.valueOf(DimensionId.OVERWORLD));
            }
            if (Features.on(Feature.PREBUILD)) {
                for (NamespacedId d : Prebuild.forPack(packDimensions.get())) {
                    if (!built.test(d)) {
                        planned.add(String.valueOf(d));
                    }
                }
            }
            openWindow.accept(planned);
        });
    }

    /** After Iris's own build: the prebuild, then the read-ahead and the loading screen go. */
    public void end() {
        try {
            if (Features.on(Feature.PREBUILD) && packOn.getAsBoolean()) {
                Features.guard(Feature.PREBUILD, () -> prebuild.accept(Prebuild.forPack(packDimensions.get())));
            }
        } finally {
            // The startup builds were what the read-ahead was for; later builds read their files as they go.
            CachePrefetch.releaseAll();
            try {
                closeWindow.run();
            } catch (RuntimeException | LinkageError e) {
                Preheat.LOG.warn("PREHEAT Could not close the shader loading screen", e);
            }
        }
    }
}
