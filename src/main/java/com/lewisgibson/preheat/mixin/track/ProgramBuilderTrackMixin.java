package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One program counted for each composite, deferred, final, shadowcomp, setup and compute program that was compiled. */
@Mixin(value = ProgramBuilder.class, remap = false)
public class ProgramBuilderTrackMixin {
    @Inject(
            method = {"begin", "beginCompute"},
            at = @At("RETURN"))
    private static void preheat$counted(CallbackInfoReturnable<ProgramBuilder> cir) {
        BuildTracker.TIMER.program(false);
    }
}
