package com.lewisgibson.preheat.testmod.mixin;

import com.lewisgibson.preheat.testmod.TestMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void testmod$frame(CallbackInfo ci) {
        TestMod.onFrame();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void testmod$tick(CallbackInfo ci) {
        TestMod.onTick();
    }

    // Priority does not matter for timing: HEAD to RETURN of the outermost call covers every injected handler.
    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void testmod$switchStart(CallbackInfo ci) {
        TestMod.onLevelSwitchStart();
    }

    @Inject(method = "updateLevelInEngines", at = @At("RETURN"))
    private void testmod$switchEnd(CallbackInfo ci) {
        TestMod.onLevelSwitchEnd();
    }
}
