package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The pack's preprocessing for a dimension (its ProgramSet, built on first use each launch). */
@Mixin(value = ShaderPack.class, remap = false)
public class ShaderPackMixin {
    @Inject(method = "getProgramSet", at = @At("HEAD"))
    private void preheat$start(CallbackInfoReturnable<ProgramSet> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.PREPROCESS);
    }

    @Inject(method = "getProgramSet", at = @At("RETURN"))
    private void preheat$end(CallbackInfoReturnable<ProgramSet> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.PREPROCESS);
    }
}
