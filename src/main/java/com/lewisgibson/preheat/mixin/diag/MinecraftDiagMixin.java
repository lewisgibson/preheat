package com.lewisgibson.preheat.mixin.diag;

import com.lewisgibson.preheat.diag.DisconnectTrace;
import com.lewisgibson.preheat.diag.FrameWatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame timing after a dimension change, and the timing marks of leaving a world. Observes only. */
@Mixin(value = Minecraft.class, remap = false)
public class MinecraftDiagMixin {
    @Shadow
    public ClientLevel level;

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void preheat$frame(boolean advanceGameTime, CallbackInfo ci) {
        FrameWatch.GAME.onFrame();
        DisconnectTrace.GAME.onFrame();
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void preheat$disconnectStart(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        DisconnectTrace.GAME.disconnecting(this.level != null, FrameWatch.GAME);
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("RETURN"))
    private void preheat$disconnectEnd(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        DisconnectTrace.GAME.mark("disconnect end");
    }
}
