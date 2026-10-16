package com.lewisgibson.preheat.gpu;

/**
 * Whether the shader cache may run on a driver, and what is known about that driver's program binaries. Every driver
 * gets the same safety checks at runtime (the format list, the file's checksum before the driver sees it, the link
 * status and the program's interface after loading, a session limit on refusals, the crash guard); this is only the
 * part decided from the driver's identity and its format count.
 */
public record CachePolicy(boolean allowed, String reason, String note) {
    /** Refusals in one session after which the cache stops loading: the driver evidently rejects them all. */
    public static final int MAX_REFUSALS = 3;

    public static CachePolicy decide(GpuInfo gpu, int binaryFormats) {
        if (binaryFormats <= 0) {
            return new CachePolicy(
                    false, "the driver offers no program binary formats (GL_NUM_PROGRAM_BINARY_FORMATS is 0)", "");
        }
        return switch (gpu.driver()) {
            case NVIDIA -> new CachePolicy(true, "", "NVIDIA binaries are compiled GPU code; tested");
            case MESA ->
                new CachePolicy(
                        true,
                        "",
                        "Mesa's binary is an intermediate form it compiles again on load and Mesa has its own shader cache, so the gain is mainly when that cache is cold");
            case AMD -> new CachePolicy(true, "", "AMD's own driver; not yet tested by Preheat");
            case INTEL -> new CachePolicy(true, "", "Intel's own driver; not yet tested by Preheat");
            case APPLE -> new CachePolicy(true, "", "Apple's OpenGL; not yet tested by Preheat");
            case OTHER -> new CachePolicy(true, "", "an unrecognised driver; not yet tested by Preheat");
        };
    }
}
