package com.lewisgibson.preheat.progress;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.platform.Platform;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The loading screen while shaders build. Between the game finishing its own loading and the title screen appearing
 * ({@link com.lewisgibson.preheat.startup.Startup}), each build puts Minecraft's own loading overlay back up, driven by
 * an {@link Overlay}: the Mojang screen, its progress bar following the build, and one line under it, "Preparing Nether
 * shaders 34/101". Frames are drawn by {@link Frames} as the build goes. When the last build ends the overlay fades out
 * over the title screen as it always does.
 *
 * <p>Builds anywhere else (a dimension visited for the first time, a shader reload in a world) draw no frames: the
 * window is only kept answering. {@link BuildProgress} wires it to the game.
 *
 * @param <O> the overlay's progress
 */
public final class LoadingScreen<O extends LoadingScreen.Overlay> {
    /** What the loading overlay shows: the bar's fill and the line under it. */
    public interface Overlay {
        void update(float fraction, ProgressPlan.Line line);

        /** Every build is over: the overlay fades out. */
        void finish();

        boolean finished();
    }

    /** The startup window: its builds, and the overlay once one is up. */
    private static final class Window<O> {
        final ProgressPlan plan = new ProgressPlan();

        @Nullable
        O overlay;
    }

    private final Supplier<String> pack;
    private final Frames<?> frames;
    private final Supplier<O> create;
    private final Consumer<O> show;
    private @Nullable Window<O> window;
    private @Nullable BuildCounts counts;

    /**
     * @param pack the shader pack in use, by name
     * @param frames draws the frames
     * @param create a new overlay's progress
     * @param show puts the loading overlay up with that progress
     */
    LoadingScreen(Supplier<String> pack, Frames<?> frames, Supplier<O> create, Consumer<O> show) {
        this.pack = pack;
        this.frames = frames;
        this.create = create;
        this.show = show;
    }

    private BuildCounts counts() {
        BuildCounts c = counts;
        if (c == null) {
            c = new BuildCounts(Platform.get().gameDir().resolve("preheat").resolve("build-counts.properties"));
            counts = c;
        }
        return c;
    }

    /** The startup window opens: builds from now until {@link #closeWindow} show on the loading screen. */
    public void openWindow(List<String> laterDimensions) {
        Window<O> w = new Window<>();
        for (String d : laterDimensions) {
            w.plan.add(d, counts().get(pack.get(), d));
        }
        window = w;
    }

    /** A build begins. In the startup window it puts the loading overlay up (once) and draws its first frame. */
    public void buildStarted(String dimension) {
        frames.reset();
        Window<O> w = window;
        if (w == null) {
            return;
        }
        w.plan.start(dimension, counts().get(pack.get(), dimension));
        O overlay = w.overlay;
        if (overlay != null) {
            overlay.update(w.plan.fraction(), w.plan.line());
        } else if (Features.on(Feature.LOADING_PROGRESS) && frames.canRender()) {
            O created = create.get();
            created.update(w.plan.fraction(), w.plan.line());
            w.overlay = created;
            show.accept(created);
            frames.frameNow();
        }
    }

    /** A program was made: the bar moves and a frame is drawn if one is due (or the window is pumped). */
    public void pulse(int programsSoFar) {
        Window<O> w = window;
        O overlay = w == null ? null : w.overlay;
        if (w != null && overlay != null) {
            w.plan.progress(programsSoFar);
            overlay.update(w.plan.fraction(), w.plan.line());
            frames.frameIfDue();
        } else {
            frames.pumpIfDue();
        }
    }

    /** A build ended: its program count is remembered for next time. */
    public void buildEnded(String dimension, int programs) {
        if (programs > 0) {
            counts().put(pack.get(), dimension, programs);
        }
        Window<O> w = window;
        if (w != null) {
            w.plan.finish(dimension, programs);
            O overlay = w.overlay;
            if (overlay != null) {
                overlay.update(w.plan.fraction(), w.plan.line());
            }
        }
    }

    /** The startup window closes: the overlay fades out on the next tick. */
    public void closeWindow() {
        Window<O> w = window;
        window = null;
        O overlay = w == null ? null : w.overlay;
        if (overlay != null) {
            overlay.finish();
            long[] stats = frames.takeStats();
            if (stats[0] > 0) {
                Preheat.LOG.info(
                        "PREHEAT Loading screen: {} frames drawn during shader builds, {} ms in total",
                        stats[0],
                        Preheat.ms(stats[1]));
            }
        }
    }

    /** An overlay of Preheat's left up after its builds (an error, or a build that never ended) is finished now. */
    public void finishIfStale(Object overlayProgress) {
        Window<O> w = window;
        O current = w == null ? null : w.overlay;
        if (overlayProgress instanceof Overlay o && !o.equals(current) && !o.finished()) {
            o.finish();
        }
    }
}
