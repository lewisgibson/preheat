package com.lewisgibson.preheat.diag;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * The freeze a player feels after a dimension change: the longest gap between two frames from the change until ten
 * seconds after the first frame that follows it, logged once per change. The gap between frame starts covers the tick
 * that switches the level, the pipeline swap and any shaders compiled on first use. Render thread only.
 */
public final class FrameWatch {
    static final long WINDOW_NANOS = 10_000_000_000L;

    /** The game's own watch, on the system clock, writing to Preheat's log. */
    public static final FrameWatch GAME =
            new FrameWatch(System::nanoTime, line -> Preheat.LOG.info("PREHEAT {}", line));

    private final LongSupplier clock;
    private final Consumer<String> log;
    private long lastFrame;
    private long armedAt;
    private long maxGap;
    private long firstFrameAfter;
    private @Nullable String from;
    private @Nullable String to;

    FrameWatch(LongSupplier clock, Consumer<String> log) {
        this.clock = clock;
        this.log = log;
    }

    /** Iris's dimension-change hook ran, from {@code from} (null when joining) to {@code to}: armed with timing lines on. */
    public void changing(@Nullable Object from, Object to) {
        if (Features.on(Feature.DIAGNOSTICS)) {
            arm(from == null ? "joining" : String.valueOf(from), String.valueOf(to));
        }
    }

    /** A dimension change began (Iris's own hook ran). */
    public void arm(String fromDimension, String toDimension) {
        armedAt = clock.getAsLong();
        maxGap = 0;
        firstFrameAfter = 0;
        from = fromDimension;
        to = toDimension;
    }

    /**
     * Leaving the world ends the watch early: what follows is the disconnect, which has its own trace, not the dimension
     * change. Logs what was seen so far, marked as cut short.
     */
    public void cut() {
        if (armedAt == 0) {
            return;
        }
        if (firstFrameAfter != 0) {
            log.accept("longest frame after " + from + " => " + to + ": " + Preheat.ms(maxGap) + " ms (first frame "
                    + Preheat.ms(firstFrameAfter - armedAt) + " ms after the change; cut short by leaving the world)");
        }
        armedAt = 0;
    }

    /** Called at the start of every frame. */
    public void onFrame() {
        long now = clock.getAsLong();
        if (armedAt != 0) {
            if (lastFrame != 0) {
                maxGap = Math.max(maxGap, now - lastFrame);
            }
            if (firstFrameAfter == 0 && now > armedAt) {
                firstFrameAfter = now;
            }
            if (firstFrameAfter != 0 && now - firstFrameAfter >= WINDOW_NANOS) {
                log.accept("longest frame after " + from + " => " + to + ": " + Preheat.ms(maxGap) + " ms (first frame "
                        + Preheat.ms(firstFrameAfter - armedAt) + " ms after the change)");
                armedAt = 0;
            }
        }
        lastFrame = now;
    }
}
