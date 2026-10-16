package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTracker;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The wait for a link to finish (asking for its status), charged to linking during a build; outside one it is ignored. */
@Mixin(value = GlStateManager.class, remap = false)
public class GlStateManagerMixin {
    @Inject(method = "glGetProgrami", at = @At("HEAD"))
    private static void preheat$start(int program, int pname, CallbackInfoReturnable<Integer> cir) {
        BuildTracker.programQuery(pname, true);
    }

    @Inject(method = "glGetProgrami", at = @At("RETURN"))
    private static void preheat$end(int program, int pname, CallbackInfoReturnable<Integer> cir) {
        BuildTracker.programQuery(pname, false);
    }
}
