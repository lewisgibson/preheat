package com.lewisgibson.preheat.progress;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Draws one frame of the game while the render thread is busy building shaders, the way Minecraft itself draws frames
 * from inside a singleplayer world load ({@code Minecraft.doWorldLoad} calls {@code renderFrame} in a loop). Only
 * outside any other frame and with no world loaded, so nothing but the loading overlay is drawn and no level or
 * pipeline is touched; the GL state Iris's build relies on is put back afterwards ({@link GlStateSnapshot}).
 *
 * <p>Frames are spaced so that drawing them costs at most about one twentieth of the build: never closer than 100 ms,
 * and further apart when a frame itself is slow (a frame waits for the display's refresh with vsync on). Where a frame
 * cannot be drawn (in a world), the window's messages are still pumped every 100 ms on SDL so it never shows "Not
 * responding". {@link FrameRenderer} wires it to the game.
 *
 * @param <M> the game
 */
public final class Frames<M> {
    private static final long MIN_INTERVAL_NANOS = 100_000_000L;
    private static final long PUMP_INTERVAL_NANOS = 100_000_000L;
    private static final int COST_SHARE = 20;

    private final LongSupplier clock;
    private final BooleanSupplier onRenderThread;
    private final Supplier<@Nullable M> game;
    private final Predicate<M> inWorld;
    private final Predicate<M> hasWindow;
    private final Consumer<M> renderFrame;
    private final StateGl gl;
    private final Runnable pump;
    private final boolean sdl;

    private int depth;
    private long lastFrame;
    private long lastPump;
    private long frameCost;
    private long totalCost;
    private int frames;

    /**
     * @param clock nanoseconds
     * @param onRenderThread whether the caller is on the render thread
     * @param game the game, or null before it exists
     * @param inWorld whether a world is loaded
     * @param hasWindow whether the game has its window yet
     * @param renderFrame draws one frame of the game
     * @param gl the GL state a frame may change, read before it and put back after it
     * @param pump moves the window's pending messages into SDL's queue
     * @param sdl whether the game runs on SDL (Minecraft 26.3 and later), the only place messages can be pumped
     */
    Frames(
            LongSupplier clock,
            BooleanSupplier onRenderThread,
            Supplier<@Nullable M> game,
            Predicate<M> inWorld,
            Predicate<M> hasWindow,
            Consumer<M> renderFrame,
            StateGl gl,
            Runnable pump,
            boolean sdl) {
        this.clock = clock;
        this.onRenderThread = onRenderThread;
        this.game = game;
        this.inWorld = inWorld;
        this.hasWindow = hasWindow;
        this.renderFrame = renderFrame;
        this.gl = gl;
        this.pump = pump;
        this.sdl = sdl;
    }

    /** Called at the start and end of every frame (the mixin on Minecraft.renderFrame). */
    public void enterFrame() {
        depth++;
    }

    public void exitFrame() {
        depth = Math.max(0, depth - 1);
    }

    /** Whether a frame may be drawn now: render thread, between frames, no world, a window. */
    public boolean canRender() {
        M m = game.get();
        return m != null && depth == 0 && onRenderThread.getAsBoolean() && !inWorld.test(m) && hasWindow.test(m);
    }

    /** Draws a frame if one is due, otherwise pumps the window's messages if that is due. */
    public void frameIfDue() {
        long now = clock.getAsLong();
        long interval = Math.max(MIN_INTERVAL_NANOS, frameCost * COST_SHARE);
        if (now - lastFrame >= interval && canRender()) {
            frameNow();
        } else {
            pumpIfDue();
        }
    }

    /** Draws a frame now (when allowed). */
    public void frameNow() {
        M m = game.get();
        if (m == null || !canRender()) {
            pumpIfDue();
            return;
        }
        long t0 = clock.getAsLong();
        GlStateSnapshot state = GlStateSnapshot.capture(gl);
        try {
            renderFrame.accept(m);
        } finally {
            state.restore(gl);
            long t1 = clock.getAsLong();
            frameCost = t1 - t0;
            totalCost += frameCost;
            frames++;
            lastFrame = t1;
            lastPump = t1;
        }
    }

    /** Moves the window's pending messages into SDL's queue, at most every 100 ms. */
    public void pumpIfDue() {
        long now = clock.getAsLong();
        if (now - lastPump < PUMP_INTERVAL_NANOS || !onRenderThread.getAsBoolean()) {
            return;
        }
        lastPump = now;
        if (sdl) {
            pump.run();
        }
    }

    /** Frames drawn and their total time since the last call, for the log; resets both. */
    public long[] takeStats() {
        long[] s = {frames, totalCost};
        frames = 0;
        totalCost = 0;
        return s;
    }

    /** A build begins: the first pump or frame waits a full interval. */
    public void reset() {
        lastPump = clock.getAsLong();
    }
}
