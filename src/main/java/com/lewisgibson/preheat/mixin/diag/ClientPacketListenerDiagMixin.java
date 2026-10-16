package com.lewisgibson.preheat.mixin.diag;

import com.lewisgibson.preheat.diag.DisconnectTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The connection's close, as two marks of the disconnect trace. */
@Mixin(value = ClientPacketListener.class, remap = false)
public class ClientPacketListenerDiagMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void preheat$closeStart(CallbackInfo ci) {
        DisconnectTrace.GAME.connectionClosing(Minecraft.getInstance().level != null);
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void preheat$closeEnd(CallbackInfo ci) {
        DisconnectTrace.GAME.mark("connection close end");
    }
}
