package com.lewisgibson.preheat.cache;

/** Why the shader cache is not being used. Each reason has its own wording on screen; the log keeps the detail. */
public enum CacheOff {
    /** The graphics driver cannot save compiled shaders (it offers no binary formats). */
    UNSUPPORTED_DRIVER("preheat.cache.off.unsupported"),
    /** The game stopped twice in a row while loading saved shaders, so the cache waits until it is deleted. */
    PAUSED("preheat.cache.off.paused"),
    /** The driver turned down saved shaders this session; they are compiled until the next launch. */
    REFUSED("preheat.cache.off.refused");

    /** The language key of its wording. */
    public final String key;

    CacheOff(String key) {
        this.key = key;
    }
}
