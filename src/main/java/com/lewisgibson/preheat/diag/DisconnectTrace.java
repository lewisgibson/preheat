package com.lewisgibson.preheat.diag;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Where the render thread's time goes when the player leaves a world: millisecond marks at each step, and a sampler that
 * reads the render thread's stack every 5 ms and charges the time to the mod it was in, until frames have been steady
 * for 60 frames. Observes only; changes nothing.
 */
public final class DisconnectTrace {
    static final long INTERVAL_MS = 5;
    static final String FRAME_LIMITER = "minecraft|FramerateLimiter.limitDisplayFPS";
    static final long STEADY_FRAME_NANOS = 50_000_000L;
    static final long MAX_NANOS = 30_000_000_000L;
    static final int STEADY_FRAMES = 60;

    /** The game's own trace, on the system clock, sampling the render thread, writing to Preheat's log. */
    public static final DisconnectTrace GAME =
            new DisconnectTrace(System::nanoTime, line -> Preheat.LOG.info("PREHEAT {}", line), true);

    private final LongSupplier clock;
    private final Consumer<String> log;
    private final boolean sampleStacks;
    private volatile boolean active;
    private long t0;
    private final List<String> marks = new ArrayList<>();
    private StallReport report = new StallReport();
    private long lastFrame;
    private int steadyFrames;

    DisconnectTrace(LongSupplier clock, Consumer<String> log, boolean sampleStacks) {
        this.clock = clock;
        this.log = log;
        this.sampleStacks = sampleStacks;
    }

    /**
     * Minecraft's disconnect began. Only leaving a world counts (quick play calls disconnect on the title screen before
     * it joins): the frame watch is cut short and the trace starts, when timing lines are on.
     */
    public void disconnecting(boolean inWorld, FrameWatch frames) {
        if (inWorld && Features.on(Feature.DIAGNOSTICS)) {
            frames.cut();
            start("disconnect start");
        }
    }

    /** The connection's close began: in a world with timing lines on it starts the trace, otherwise it is a mark. */
    public void connectionClosing(boolean inWorld) {
        if (inWorld && Features.on(Feature.DIAGNOSTICS)) {
            start("connection close start");
        } else {
            mark("connection close start");
        }
    }

    /** A disconnect began; starts the trace once (a second start while one runs is only a mark). */
    public synchronized void start(String step) {
        if (active) {
            mark(step);
            return;
        }
        active = true;
        t0 = clock.getAsLong();
        marks.clear();
        report = new StallReport();
        lastFrame = 0;
        steadyFrames = 0;
        if (sampleStacks) {
            startSampler(Thread.currentThread());
        }
        mark(step);
    }

    private void startSampler(Thread render) {
        Thread sampler = new Thread(() -> sampleWhileActive(render), "Preheat disconnect sampler");
        sampler.setDaemon(true);
        sampler.start();
    }

    /** Samples {@code render}'s stack every few milliseconds until the trace ends or this thread is interrupted. */
    void sampleWhileActive(Thread render) {
        long last = clock.getAsLong();
        while (active) {
            String owner = StackOwner.of(render.getStackTrace());
            long now = clock.getAsLong();
            sample(owner, now - last);
            last = now;
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** One stack sample: {@code nanos} of render thread spent in {@code signature} ("owner|Class.method"). */
    synchronized void sample(String signature, long nanos) {
        if (active) {
            report.add(signature, nanos);
        }
    }

    /** A step of the disconnect, stamped in milliseconds since it began. */
    public synchronized void mark(String step) {
        if (active) {
            marks.add("+" + Preheat.ms(clock.getAsLong() - t0) + " ms " + step);
        }
    }

    /** Whether a trace is running. */
    public boolean active() {
        return active;
    }

    /** Every frame start: ends the trace once frames have been steady for 60 frames, or after 30 s. */
    public void onFrame() {
        if (!active) {
            return;
        }
        synchronized (this) {
            long now = clock.getAsLong();
            steadyFrames = lastFrame != 0 && now - lastFrame < STEADY_FRAME_NANOS ? steadyFrames + 1 : 0;
            lastFrame = now;
            if (steadyFrames >= STEADY_FRAMES || now - t0 > MAX_NANOS) {
                finish(now);
            }
        }
    }

    private void finish(long now) {
        active = false;
        // The menu's frame cap sleeping between frames is not work: it is reported apart.
        long idle = report.nanos(FRAME_LIMITER);
        log.accept("disconnect: " + Preheat.ms(now - t0 - idle) + " ms of work (" + Preheat.ms(idle)
                + " ms idle in the frame limiter, " + Preheat.ms(now - t0) + " ms until frames were steady again)");
        for (String m : marks) {
            log.accept("disconnect " + m);
        }
        for (String l : report.lines(8)) {
            log.accept("disconnect render thread in " + l);
        }
    }
}
