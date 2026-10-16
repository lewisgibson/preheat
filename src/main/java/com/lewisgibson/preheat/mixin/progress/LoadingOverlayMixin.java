package com.lewisgibson.preheat.mixin.progress;

import com.lewisgibson.preheat.progress.BuildProgress;
import com.lewisgibson.preheat.progress.ProgressReload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft's loading overlay, when Preheat put it up for a shader build, gets one line under its progress bar saying
 * what is being prepared ({@link ProgressReload} decides it). The bar, the logo, the colours and the fade are the
 * game's own. A Preheat overlay whose builds are over (or were abandoned) is finished here, so it can never stay up.
 */
@Mixin(value = LoadingOverlay.class, remap = false)
public class LoadingOverlayMixin {
    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    private long fadeOutStart;

    @Shadow
    private float currentProgress;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void preheat$finishStale(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        BuildProgress.finishIfStale(this.reload);
        this.currentProgress = ProgressReload.barFill(this.reload, this.currentProgress);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void preheat$line(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        float alpha = ProgressReload.lineAlpha(this.fadeOutStart, Util.getMillis());
        Component line = ProgressReload.lineToDraw(this.reload, alpha);
        if (line != null) {
            graphics.centeredText(
                    Minecraft.getInstance().font,
                    line,
                    graphics.guiWidth() / 2,
                    ProgressReload.lineY(graphics.guiHeight()),
                    ARGB.white(alpha));
        }
    }
}
