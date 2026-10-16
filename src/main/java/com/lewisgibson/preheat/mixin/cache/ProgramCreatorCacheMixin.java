package com.lewisgibson.preheat.mixin.cache;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Asks the driver to keep each ProgramBuilder program's binary, so it can be saved after the link. */
@Mixin(value = ProgramCreator.class, remap = false)
public class ProgramCreatorCacheMixin {
    @ModifyArg(
            method = "create",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;glLinkProgram(I)V"))
    private static int preheat$retrievable(int program) {
        Features.guard(Feature.PROGRAM_CACHE, () -> ProgramBinaries.retrievable(program));
        return program;
    }
}
