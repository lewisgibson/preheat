package com.lewisgibson.preheat.dh;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.platform.Platform;
import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons' close of a multiplayer world, moved off the render thread so leaving a server returns to the menu
 * at once. Every step of that close is thread-agnostic in DH 3.3.4 (docs/architecture.md). A barrier, {@link #await},
 * runs before anything could touch DH's world again: a new connection, a new level, a singleplayer world, quitting.
 */
public final class AsyncDisconnect {
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);
    private static volatile @Nullable Thread worker;

    static {
        // Quitting through System.exit skips Minecraft.close; the hook still lets DH's databases finish closing.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> await("the JVM exits"), "Preheat DH close barrier"));
    }

    private AsyncDisconnect() {}

    /**
     * Whether this close may run in the background: enabled, on the render thread, not already the background run, and
     * multiplayer.
     */
    public static boolean shouldDefer(
            boolean enabled, boolean insideWorker, boolean onRenderThread, boolean singleplayer) {
        return enabled && !insideWorker && onRenderThread && !singleplayer;
    }

    /**
     * The Distant Horizons versions the background close was checked against: it relies on DH's close not touching GL
     * or the level on the calling thread, which a later DH could change without changing any method's shape.
     */
    static final String CHECKED_DH = "3.3.";

    /** How long a new world waits for an earlier close before going ahead anyway, so the game can never hang here. */
    static final long WAIT_LIMIT_MS = 30_000;

    /** Whether moving the close is on (Preheat on, and a Distant Horizons version it was checked with). */
    public static boolean enabled() {
        return Features.on(Feature.BACKGROUND_DH_CLOSE)
                && checked(Platform.get().modVersion("distanthorizons").orElse(""));
    }

    static boolean checked(String dhVersion) {
        return dhVersion.startsWith(CHECKED_DH);
    }

    /** The disconnect trace's note for a close that runs where it was called. */
    public static String closeNote(boolean insideWorker, String thread) {
        return insideWorker
                ? "distant horizons close running in the background"
                : "distant horizons close on " + thread;
    }

    /** Whether the calling thread is the background close itself. */
    public static boolean insideWorker() {
        return INSIDE.get();
    }

    /** Runs {@code close} on a new background thread, after any earlier one has finished. */
    public static void start(Runnable close) {
        await("the next Distant Horizons close");
        Thread t = new Thread(
                () -> {
                    INSIDE.set(true);
                    long t0 = System.nanoTime();
                    try {
                        close.run();
                    } catch (RuntimeException | LinkageError e) {
                        Preheat.LOG.error("PREHEAT Distant Horizons' background close failed", e);
                    } finally {
                        Preheat.LOG.info(
                                "PREHEAT Distant Horizons closed in the background in {} ms",
                                Preheat.ms(System.nanoTime() - t0));
                    }
                },
                "Preheat DH close");
        // Not a daemon: the JVM does not stop under a database that is still closing.
        t.setDaemon(false);
        worker = t;
        t.start();
    }

    /** Waits for a background close still running; logs only when it actually had to wait. */
    public static void await(String before) {
        await(before, WAIT_LIMIT_MS);
    }

    static void await(String before, long limitMs) {
        Thread t = worker;
        if (t == null || t.equals(Thread.currentThread()) || !t.isAlive()) {
            return;
        }
        long t0 = System.nanoTime();
        Preheat.LOG.info("PREHEAT waiting for Distant Horizons to finish closing before {}", before);
        boolean interrupted = false;
        long deadline = System.nanoTime() + limitMs * 1_000_000L;
        while (t.isAlive()) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                Preheat.LOG.warn(
                        "PREHEAT Distant Horizons is still closing after {} s; carrying on before {}",
                        limitMs / 1000.0,
                        before);
                break;
            }
            try {
                t.join(Math.max(1, left / 1_000_000L));
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        Preheat.LOG.info(
                "PREHEAT waited {} ms for Distant Horizons to finish closing", Preheat.ms(System.nanoTime() - t0));
    }
}
