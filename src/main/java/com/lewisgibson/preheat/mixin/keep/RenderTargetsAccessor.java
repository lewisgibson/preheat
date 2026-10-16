package com.lewisgibson.preheat.mixin.keep;

import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RenderTargets.class, remap = false)
public interface RenderTargetsAccessor {
    @Accessor("fullClearRequired")
    void preheat$setFullClearRequired(boolean value);
}
