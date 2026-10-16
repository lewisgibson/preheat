package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iris's GLSL patching (glsl-transformer), Java on the render thread. */
@Mixin(value = TransformPatcher.class, remap = false)
public class TransformPatcherMixin {
    @Inject(
            method = {
                "patchDepth",
                "patchVanilla",
                "patchDHTerrain",
                "patchDHGeneric",
                "patchSodium",
                "patchComposite",
                "patchCompute"
            },
            at = @At("HEAD"))
    private static void preheat$start(CallbackInfoReturnable<Object> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.TRANSFORM);
    }

    @Inject(
            method = {
                "patchDepth",
                "patchVanilla",
                "patchDHTerrain",
                "patchDHGeneric",
                "patchSodium",
                "patchComposite",
                "patchCompute"
            },
            at = @At("RETURN"))
    private static void preheat$end(CallbackInfoReturnable<Object> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.TRANSFORM);
    }
}
