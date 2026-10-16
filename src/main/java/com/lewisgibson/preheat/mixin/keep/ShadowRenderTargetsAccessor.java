package com.lewisgibson.preheat.mixin.keep;

import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ShadowRenderTargets.class, remap = false)
public interface ShadowRenderTargetsAccessor {
    @Accessor("fullClearRequired")
    void preheat$setFullClearRequired(boolean value);
}
