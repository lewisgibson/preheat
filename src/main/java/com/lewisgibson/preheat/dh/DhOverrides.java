package com.lewisgibson.preheat.dh;

import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons' side of a kept pipeline: what DHCompatInternal.clear() unbinds when a pipeline is destroyed,
 * without freeing its programs, and what a new DHCompatInternal starts with. {@link DhBridge} is the real one.
 */
public final class DhOverrides {
    private DhOverrides() {}

    /** A pipeline's DHCompatInternal, as far as keeping it touches it. */
    public interface Internal {
        @Nullable
        Object solidFramebuffer();

        @Nullable
        Object shadowFramebuffer();

        @Nullable
        Object genericShader();

        void unbindFramebuffer(Object framebuffer);

        void unbindShader(Object shader);

        /** No depth texture attached yet, and the pack's compatibility flag, as a new one starts with. */
        void resetForReuse();
    }

    /** Unbinds the overrides this pipeline set; {@code dh} is null when it has none. */
    public static void unbind(@Nullable Internal dh) {
        if (dh == null) {
            return;
        }
        Object solid = dh.solidFramebuffer();
        if (solid != null) {
            dh.unbindFramebuffer(solid);
        }
        Object shadow = dh.shadowFramebuffer();
        if (shadow != null) {
            dh.unbindFramebuffer(shadow);
        }
        Object generic = dh.genericShader();
        if (generic != null) {
            dh.unbindShader(generic);
        }
    }

    /** Puts this pipeline's state back to a new one's; {@code dh} is null when it has none. */
    public static void reset(@Nullable Internal dh) {
        if (dh != null) {
            dh.resetForReuse();
        }
    }
}
