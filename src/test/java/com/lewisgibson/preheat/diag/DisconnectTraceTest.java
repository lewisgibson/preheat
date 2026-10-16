package com.lewisgibson.preheat.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DisconnectTraceTest {
    private static final long MS = 1_000_000L;

    @Test
    void marksSamplesAndTheSummaryOnceFramesAreSteady() {
        AtomicLong now = new AtomicLong(100 * MS);
        List<String> log = new ArrayList<>();
        DisconnectTrace t = new DisconnectTrace(now::get, log::add, false);

        t.onFrame();
        t.mark("ignored, no trace yet");
        t.sample("minecraft|Minecraft.run", MS);
        assertFalse(t.active());

        t.start("disconnect start");
        now.addAndGet(5 * MS);
        t.start("connection close start"); // a second start while running is a mark
        t.sample("distanthorizons|ClientApi.onClientOnlyDisconnected", 40 * MS);
        t.sample(DisconnectTrace.FRAME_LIMITER, 10 * MS);
        now.addAndGet(45 * MS);
        t.mark("disconnect end");
        for (int i = 0; i <= DisconnectTrace.STEADY_FRAMES; i++) {
            t.onFrame();
            now.addAndGet(16 * MS);
        }
        assertFalse(t.active());
        assertEquals(
                "disconnect: 1000.0 ms of work (10.0 ms idle in the frame limiter, 1010.0 ms until frames were"
                        + " steady again)",
                log.get(0));
        assertEquals(
                List.of(
                        "disconnect +0.0 ms disconnect start",
                        "disconnect +5.0 ms connection close start",
                        "disconnect +50.0 ms disconnect end"),
                log.subList(1, 4));
        assertEquals(
                List.of(
                        "disconnect render thread in distanthorizons 40 ms (ClientApi.onClientOnlyDisconnected 40 ms)",
                        "disconnect render thread in minecraft 10 ms (FramerateLimiter.limitDisplayFPS 10 ms)"),
                log.subList(4, 6));
        t.sample("late", MS);
        t.onFrame();
        assertEquals(6, log.size(), "finished once");
    }

    @Test
    void unsteadyFramesResetTheCountAndThirtySecondsEndsItAnyway() {
        AtomicLong now = new AtomicLong(0);
        List<String> log = new ArrayList<>();
        DisconnectTrace t = new DisconnectTrace(now::get, log::add, false);
        t.start("disconnect start");
        for (int i = 0; i < 100; i++) {
            now.addAndGet(DisconnectTrace.STEADY_FRAME_NANOS);
            t.onFrame();
        }
        assertTrue(t.active(), "50 ms frames are not steady");
        now.addAndGet(DisconnectTrace.MAX_NANOS);
        t.onFrame();
        assertFalse(t.active());
        assertTrue(log.get(0).startsWith("disconnect: "));
    }

    @Test
    void theSamplerChargesTheRenderThreadsTimeToItsOwner() throws Exception {
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        DisconnectTrace t = new DisconnectTrace(System::nanoTime, log::add, true);
        t.start("disconnect start");
        Thread.sleep(60);
        long until = System.nanoTime() + 5_000 * MS;
        while (t.active() && System.nanoTime() < until) {
            t.onFrame();
            Thread.sleep(1);
        }
        assertFalse(t.active());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("disconnect render thread in ")), log.toString());
    }

    @Test
    void anInterruptedSamplerStops() throws Exception {
        DisconnectTrace t = new DisconnectTrace(System::nanoTime, line -> {}, false);
        t.start("disconnect start");
        Thread render = Thread.currentThread();
        Thread sampler = new Thread(() -> t.sampleWhileActive(render));
        sampler.start();
        sampler.interrupt();
        sampler.join(5_000);
        assertFalse(sampler.isAlive(), "it stopped although the trace is still running");
        assertTrue(t.active());
    }

    @Test
    void theGamesTraceWritesToPreheatsLog() {
        try (LogLines log = LogLines.capture()) {
            DisconnectTrace.GAME.start("disconnect start");
            for (int i = 0; i <= DisconnectTrace.STEADY_FRAMES && DisconnectTrace.GAME.active(); i++) {
                DisconnectTrace.GAME.onFrame();
            }
            assertFalse(DisconnectTrace.GAME.active());
            assertTrue(log.has("INFO PREHEAT disconnect: "), log.lines().toString());
        }
    }
}
