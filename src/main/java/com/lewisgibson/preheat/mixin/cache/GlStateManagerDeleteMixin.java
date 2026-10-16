package com.lewisgibson.preheat.mixin.cache;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A program deleted before the build ends must not be saved later: its name can be given to another program in the
 * meantime (Iris deletes unused shadow programs mid-build), and that program's binary would be filed under this one's
 * key.
 */
@Mixin(value = GlStateManager.class, remap = false)
public class GlStateManagerDeleteMixin {
    @Inject(method = "glDeleteProgram", at = @At("HEAD"))
    private static void preheat$forget(int program, CallbackInfo ci) {
        Features.guard(Feature.PROGRAM_CACHE, () -> ProgramBinaries.forgetPending(program));
    }
}
