package com.lewisgibson.preheat.mixin.dh;

import com.lewisgibson.preheat.dh.AsyncDisconnect;
import com.lewisgibson.preheat.diag.DisconnectTrace;
import com.mojang.blaze3d.systems.RenderSystem;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DH calls onClientOnlyDisconnected from ClientPacketListener.close on the render thread, and it closes the whole DH
 * world there: the levels, the thread pools, every SQLite database, then System.gc(). For a multiplayer world the
 * same call is made on a background thread instead, unchanged, and the render thread goes on to the menu.
 */
@Mixin(value = ClientApi.class, remap = false)
public class ClientApiMixin {
    @Inject(method = "onClientOnlyDisconnected", at = @At("HEAD"), cancellable = true)
    private void preheat$closeInBackground(CallbackInfo ci) {
        if (!AsyncDisconnect.shouldDefer(
                AsyncDisconnect.enabled(),
                AsyncDisconnect.insideWorker(),
                RenderSystem.isOnRenderThread(),
                Minecraft.getInstance().getSingleplayerServer() != null)) {
            DisconnectTrace.GAME.mark(AsyncDisconnect.closeNote(
                    AsyncDisconnect.insideWorker(), Thread.currentThread().getName()));
            return;
        }
        ci.cancel();
        DisconnectTrace.GAME.mark("distant horizons close moved to the background");
        ClientApi api = (ClientApi) (Object) this;
        AsyncDisconnect.start(api::onClientOnlyDisconnected);
    }
}
