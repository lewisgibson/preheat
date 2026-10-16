package com.lewisgibson.preheat.mixin.barrier;

import com.lewisgibson.preheat.dh.AsyncDisconnect;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A new play connection: none of its packets reach Distant Horizons before an earlier background close has finished. */
@Mixin(value = ClientPacketListener.class, remap = false)
public class ClientPacketListenerBarrierMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void preheat$barrier(CallbackInfo ci) {
        AsyncDisconnect.await("a new connection");
    }
}
