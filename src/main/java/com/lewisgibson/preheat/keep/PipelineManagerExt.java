package com.lewisgibson.preheat.keep;

import java.util.List;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

/** What Preheat adds to Iris's PipelineManager. */
public interface PipelineManagerExt {
    /**
     * Destroys every kept pipeline but the active one, now: Iris is about to delete every storage buffer (its fallback
     * after a pipeline failed to build), and a kept pipeline would otherwise come back with deleted buffers.
     */
    void preheat$dropInactive(String why);

    /** Builds the pipelines of these dimensions that are not kept yet, into the kept set, without making any active; render thread only. */
    void preheat$prebuild(List<NamespacedId> dimensions);

    /** Whether a pipeline for this dimension exists now. */
    boolean preheat$isBuilt(NamespacedId dimension);
}
