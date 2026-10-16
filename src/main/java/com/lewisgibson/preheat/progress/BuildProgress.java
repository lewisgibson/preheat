package com.lewisgibson.preheat.progress;

import java.util.List;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.jspecify.annotations.Nullable;

/** The game's {@link LoadingScreen}: Iris's shader pack name and Minecraft's own loading overlay. */
public final class BuildProgress {
    private static final LoadingScreen<ProgressReload> SCREEN = new LoadingScreen<>(
            () -> String.valueOf(Iris.getCurrentPackName()),
            FrameRenderer.FRAMES,
            ProgressReload::new,
            reload -> Minecraft.getInstance()
                    .gui
                    .setOverlay(new LoadingOverlay(Minecraft.getInstance(), reload, error -> {}, false)));

    private BuildProgress() {}

    public static void openWindow(List<String> laterDimensions) {
        SCREEN.openWindow(laterDimensions);
    }

    public static void buildStarted(String dimension) {
        SCREEN.buildStarted(dimension);
    }

    public static void pulse(int programsSoFar) {
        SCREEN.pulse(programsSoFar);
    }

    public static void buildEnded(String dimension, int programs) {
        SCREEN.buildEnded(dimension, programs);
    }

    public static void closeWindow() {
        SCREEN.closeWindow();
    }

    /** A Preheat overlay left up after its builds (an error, or a build that never ended) is finished now. */
    public static void finishIfStale(ReloadInstance overlayReload) {
        SCREEN.finishIfStale(overlayReload);
    }

    /** The line to draw under the bar of {@code overlayReload}, or null when that overlay is not Preheat's. */
    public static @Nullable Component line(ReloadInstance overlayReload) {
        return ProgressReload.lineOf(overlayReload);
    }
}
