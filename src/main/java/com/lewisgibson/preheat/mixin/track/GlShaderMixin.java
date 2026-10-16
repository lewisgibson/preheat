package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import net.irisshaders.iris.gl.shader.GlShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every shader compiled through GlShader: Iris's builder programs and Distant Horizons' LOD programs. */
@Mixin(value = GlShader.class, remap = false)
public class GlShaderMixin {
    @Inject(method = "createShader", at = @At("HEAD"))
    private static void preheat$compileStart(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.enter(BuildTimer.Step.COMPILE);
    }

    @Inject(method = "createShader", at = @At("RETURN"))
    private static void preheat$compileEnd(CallbackInfoReturnable<Integer> cir) {
        BuildTracker.TIMER.exit(BuildTimer.Step.COMPILE);
    }
}
