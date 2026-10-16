package com.lewisgibson.preheat.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FrameWatchTest {
    private static final long MS = 1_000_000L;

    @Test
    void reportsTheLongestGapInTheTenSecondsAfterTheFirstFrame() {
        AtomicLong now = new AtomicLong(1_000 * MS);
        List<String> log = new ArrayList<>();
        FrameWatch w = new FrameWatch(now::get, log::add);

        w.onFrame();
        now.addAndGet(16 * MS);
        w.onFrame();
        assertEquals(List.of(), log, "nothing is armed");

        w.arm("minecraft:overworld", "minecraft:the_nether");
        now.addAndGet(1_700 * MS);
        w.onFrame(); // the first frame after the change, 1700 ms after the last one
        for (int i = 0; i < 700; i++) {
            now.addAndGet(16 * MS);
            w.onFrame();
        }
        assertEquals(
                List.of(
                        "longest frame after minecraft:overworld => minecraft:the_nether: 1700.0 ms (first frame 1700.0 ms"
                                + " after the change)"),
                log);
        now.addAndGet(16 * MS);
        w.onFrame();
        assertEquals(1, log.size(), "logged once per change");
    }

    @Test
    void aFrameAtTheVeryMomentOfTheChangeIsNotTheFirstFrameAfterIt() {
        AtomicLong now = new AtomicLong(5 * MS);
        List<String> log = new ArrayList<>();
        FrameWatch w = new FrameWatch(now::get, log::add);
        w.arm("a", "b");
        w.onFrame(); // same instant as the arm
        now.addAndGet(FrameWatch.WINDOW_NANOS);
        w.onFrame(); // the first frame after it
        assertEquals(List.of(), log);
        now.addAndGet(FrameWatch.WINDOW_NANOS);
        w.onFrame();
        assertEquals(1, log.size());
    }

    @Test
    void leavingTheWorldCutsTheWatchShort() {
        AtomicLong now = new AtomicLong(1_000 * MS);
        List<String> log = new ArrayList<>();
        FrameWatch w = new FrameWatch(now::get, log::add);
        w.cut();
        assertEquals(List.of(), log, "nothing armed, nothing to cut");
        w.arm("minecraft:the_end", "minecraft:overworld");
        w.cut();
        assertEquals(List.of(), log, "no frame yet, nothing to report");
        w.arm("minecraft:the_end", "minecraft:overworld");
        now.addAndGet(50 * MS);
        w.onFrame();
        now.addAndGet(20 * MS);
        w.onFrame();
        w.cut();
        assertEquals(
                List.of(
                        "longest frame after minecraft:the_end => minecraft:overworld: 20.0 ms (first frame 50.0 ms after"
                                + " the change; cut short by leaving the world)"),
                log);
        now.addAndGet(6_000 * MS);
        w.onFrame();
        now.addAndGet(20_000 * MS);
        w.onFrame();
        assertEquals(1, log.size(), "the disconnect's long frames are not charged to the change");
    }

    @Test
    void theGamesWatchWritesToPreheatsLog() throws Exception {
        try (LogLines log = LogLines.capture()) {
            FrameWatch.GAME.arm("minecraft:overworld", "minecraft:the_nether");
            Thread.sleep(2);
            FrameWatch.GAME.onFrame();
            FrameWatch.GAME.cut();
            assertTrue(
                    log.has("INFO PREHEAT longest frame after minecraft:overworld => minecraft:the_nether"),
                    log.lines().toString());
        }
    }
}
