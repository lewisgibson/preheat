package com.lewisgibson.preheat.keep;

import java.util.Iterator;

/** Recognises Iris's own dimension-change hook as the caller of PipelineManager.destroyPipeline. */
public final class DimensionChange {
    /** Iris's handler on Minecraft.updateLevelInEngines; Mixin keeps the name as the suffix of the merged method. */
    static final String IRIS_HANDLER_SUFFIX = "iris$resetPipeline";

    static final String DESTROY = "destroyPipeline";

    private DimensionChange() {}

    /**
     * True when the destroyPipeline on the stack was called directly by Iris's dimension-change handler. Every other
     * caller (a shader reload, a pack change, Distant Horizons toggling, the debug screen) destroys as before.
     */
    public static boolean isCaller() {
        return StackWalker.getInstance()
                .walk(frames -> callerIsHandler(
                        frames.map(StackWalker.StackFrame::getMethodName).iterator()));
    }

    static boolean callerIsHandler(Iterator<String> methodNames) {
        boolean seenDestroy = false;
        while (methodNames.hasNext()) {
            String name = methodNames.next();
            if (seenDestroy) {
                return name.endsWith(IRIS_HANDLER_SUFFIX);
            }
            seenDestroy = name.equals(DESTROY);
        }
        return false;
    }
}
