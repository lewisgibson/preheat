package com.lewisgibson.preheat.mixin.cache;

import net.irisshaders.iris.pipeline.programs.PartialShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PartialShader.class, remap = false)
public interface PartialShaderAccessor {
    @Accessor("program")
    int preheat$program();
}
