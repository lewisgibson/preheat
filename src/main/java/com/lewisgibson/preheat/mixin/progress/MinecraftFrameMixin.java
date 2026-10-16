package com.lewisgibson.preheat.mixin.progress;

import com.lewisgibson.preheat.progress.FrameRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Knows when a frame is being drawn, so a loading-screen frame is only ever drawn between frames. */
@Mixin(value = Minecraft.class, remap = false)
public class MinecraftFrameMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void preheat$frameStart(boolean advanceGameTime, CallbackInfo ci) {
        FrameRenderer.enterFrame();
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void preheat$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
        FrameRenderer.exitFrame();
    }
}
