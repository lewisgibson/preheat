package com.lewisgibson.preheat.mixin.dh;

import net.irisshaders.iris.compat.dh.DHCompatInternal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Applied only when Distant Horizons is loaded: DHCompatInternal names DH's own API types. */
@Mixin(value = DHCompatInternal.class, remap = false)
public interface DHCompatInternalAccessor {
    @Accessor("storedDepthTex")
    void preheat$setStoredDepthTex(int value);

    @Accessor("translucentDepthDirty")
    void preheat$setTranslucentDepthDirty(boolean value);
}
