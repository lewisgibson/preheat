package com.lewisgibson.preheat.mixin.barrier;

import com.lewisgibson.preheat.dh.AsyncDisconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nothing reaches Distant Horizons' world before a background close of it has finished: a new level, a singleplayer world, quitting. */
@Mixin(value = Minecraft.class, remap = false)
public class MinecraftBarrierMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void preheat$beforeSetLevel(ClientLevel newLevel, CallbackInfo ci) {
        AsyncDisconnect.await("a new level");
    }

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void preheat$beforeSingleplayer(CallbackInfo ci) {
        AsyncDisconnect.await("a singleplayer world");
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void preheat$beforeQuit(CallbackInfo ci) {
        AsyncDisconnect.await("quitting");
    }
}
