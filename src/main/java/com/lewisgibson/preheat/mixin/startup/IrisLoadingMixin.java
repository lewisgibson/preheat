package com.lewisgibson.preheat.mixin.startup;

import com.lewisgibson.preheat.startup.Startup;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris builds the overworld pipeline as the game finishes loading (the first title screen's init). The loading screen
 * opens before that build, and the other dimensions Preheat prepares are built right after it.
 */
@Mixin(value = Iris.class, remap = false)
public class IrisLoadingMixin {
    @Inject(method = "onLoadingComplete", at = @At("HEAD"))
    private static void preheat$beforeFirstBuild(CallbackInfo ci) {
        Startup.begin();
    }

    @Inject(method = "onLoadingComplete", at = @At("TAIL"))
    private static void preheat$afterFirstBuild(CallbackInfo ci) {
        Startup.end();
    }
}
