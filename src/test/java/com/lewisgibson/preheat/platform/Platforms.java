package com.lewisgibson.preheat.platform;

import org.jspecify.annotations.Nullable;

/** Lets tests in other packages stand in for the mod loader (and hand it back). */
public final class Platforms {
    private Platforms() {}

    public static void use(@Nullable Platform p) {
        Platform.Holder.use(p);
    }
}
