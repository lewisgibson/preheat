package com.lewisgibson.preheat.progress;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.jspecify.annotations.Nullable;

/**
 * What Minecraft's own loading overlay is shown with while shaders build: its progress bar follows the build and it
 * is done when the last build is. The overlay then fades out over the title screen exactly as it does after loading
 * resources, so the whole thing is the game's own screen with one more line on it.
 */
public final class ProgressReload implements ReloadInstance, LoadingScreen.Overlay {
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private volatile float progress;
    private volatile Component line = Component.empty();

    @Override
    public CompletableFuture<?> done() {
        return done;
    }

    @Override
    public float getActualProgress() {
        return progress;
    }

    /** The bar's fill (0 to 1) and the line under it. */
    public void update(float fraction, Component text) {
        progress = Math.max(0f, Math.min(1f, fraction));
        line = text;
    }

    @Override
    public void update(float fraction, ProgressPlan.Line line) {
        update(fraction, text(line));
    }

    public Component line() {
        return line;
    }

    /** Every build is over: the overlay fades out on its next tick. */
    @Override
    public void finish() {
        progress = 1f;
        done.complete(null);
    }

    @Override
    public boolean finished() {
        return isDone();
    }

    /** The line in the game's own loading style ("Downloading resource pack 2/5"): "Preparing Nether shaders 43/303". */
    static Component text(ProgressPlan.Line line) {
        if (line.dimension() == null) {
            return Component.translatable("preheat.loading.generic");
        }
        return line.totalKnown()
                ? Component.translatable("preheat.loading.line", line.dimension(), line.done(), line.total())
                : Component.translatable("preheat.loading.lineFirst", line.dimension());
    }

    /**
     * The overlay's bar: it eases towards its target a little each frame, but a shader build draws about ten frames a
     * second rather than sixty, so on Preheat's overlay the bar is set straight to where the build is.
     */
    public static float barFill(Object overlayProgress, float current) {
        return overlayProgress instanceof ProgressReload p ? p.getActualProgress() : current;
    }

    /** The line's opacity: whole until the overlay starts fading out ({@code fadeOutStart} -1), then gone in a second. */
    public static float lineAlpha(long fadeOutStart, long nowMillis) {
        return fadeOutStart > -1L ? 1f - Math.min(1f, (nowMillis - fadeOutStart) / 1000f) : 1f;
    }

    /** The line to draw under the bar, or null: not Preheat's overlay, nothing to say, or faded out. */
    public static @Nullable Component lineToDraw(Object overlayProgress, float alpha) {
        Component line = lineOf(overlayProgress);
        return line == null || line.getString().isEmpty() || alpha <= 0.02f ? null : line;
    }

    /** The bar sits at 83.25% of the height; the line goes just under it, as the game's own text would. */
    public static int lineY(int height) {
        return (int) (height * 0.8325) + 12;
    }

    /** The line to draw under the bar of an overlay showing {@code overlayProgress}, or null when it is not Preheat's. */
    public static @Nullable Component lineOf(Object overlayProgress) {
        return overlayProgress instanceof ProgressReload p ? p.line() : null;
    }
}
