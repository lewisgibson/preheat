package com.lewisgibson.preheat.keep;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The graphics card's free video memory, where the driver reports it: NVIDIA through GL_NVX_gpu_memory_info (free and
 * total), AMD through GL_ATI_meminfo (free only). Null elsewhere, and for a software renderer (Mesa's llvmpipe offers
 * both extensions with meaningless numbers) or any reading that does not hold together. Render thread only;
 * {@link LwjglVideoMemory} is the game's.
 */
public final class VideoMemory {
    static final int GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
    static final int GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    /** The driver calls a reading makes. */
    public interface Gl {
        @Nullable
        String renderer();

        boolean nvxMemoryInfo();

        boolean atiMemInfo();

        /** The GL error, which also clears it. */
        int error();

        int integer(int pname);

        void integers(int pname, int[] into);
    }

    private final Gl gl;
    private long firstTotalKb;
    private @Nullable Boolean usable;

    public VideoMemory(Gl gl) {
        this.gl = gl;
    }

    public KeptBudget.@Nullable Memory read() {
        try {
            Boolean u = usable;
            if (u == null) {
                String renderer = String.valueOf(gl.renderer()).toLowerCase(Locale.ROOT);
                u = !(renderer.contains("llvmpipe")
                        || renderer.contains("softpipe")
                        || renderer.contains("swrast")
                        || renderer.contains("lavapipe")
                        || renderer.contains("swiftshader")
                        || renderer.contains("basic render"));
                usable = u;
            }
            if (!u) {
                return null;
            }
            if (gl.nvxMemoryInfo()) {
                gl.error();
                long total = gl.integer(GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
                long free = gl.integer(GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
                if (gl.error() != 0) {
                    return null;
                }
                KeptBudget.Memory m = KeptBudget.Memory.of(free, total, firstTotalKb);
                if (m != null && firstTotalKb == 0) {
                    firstTotalKb = total;
                }
                return m;
            }
            if (gl.atiMemInfo()) {
                gl.error();
                int[] info = new int[4];
                gl.integers(TEXTURE_FREE_MEMORY_ATI, info);
                if (gl.error() != 0) {
                    return null;
                }
                return KeptBudget.Memory.of(info[0], 0, 0);
            }
        } catch (RuntimeException | LinkageError e) {
            // No answer is the same as a driver that does not report it.
        }
        return null;
    }
}
