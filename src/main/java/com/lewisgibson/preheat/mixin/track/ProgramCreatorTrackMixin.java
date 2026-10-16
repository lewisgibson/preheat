package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The link of every program built through ProgramBuilder. */
@Mixin(value = ProgramCreator.class, remap = false)
public class ProgramCreatorTrackMixin {
    @Inject(method = "create", at = @At("HEAD"))
    private static void preheat$linkStart(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.LINK);
    }

    @Inject(method = "create", at = @At("RETURN"))
    private static void preheat$linkEnd(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.LINK);
    }
}
