package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.pipeline.programs.PartialShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris's gbuffer and shadow programs: each compile, and the link, and one program counted per link. */
@Mixin(value = ShaderCreator.class, remap = false)
public class ShaderCreatorTrackMixin {
    @Inject(method = "link", at = @At("HEAD"))
    private static void preheat$linkStart(CallbackInfoReturnable<PartialShader> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.LINK);
    }

    @Inject(method = "link", at = @At("RETURN"))
    private static void preheat$linkEnd(CallbackInfoReturnable<PartialShader> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.LINK);
        BuildTracker.TIMER.program(false);
    }

    @Inject(method = "createShader", at = @At("HEAD"))
    private static void preheat$compileStart(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.COMPILE);
    }

    @Inject(method = "createShader", at = @At("RETURN"))
    private static void preheat$compileEnd(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.COMPILE);
    }
}
