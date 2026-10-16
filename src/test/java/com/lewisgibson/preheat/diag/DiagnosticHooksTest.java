package com.lewisgibson.preheat.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.dh.AsyncDisconnect;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the game's hooks tell the timing lines: leaving a world, a connection closing, a dimension change. */
class DiagnosticHooksTest {
    private static final long MS = 1_000_000L;

    @TempDir
    Path tmp;

    final AtomicLong now = new AtomicLong(MS);
    final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        FeatureStates.allOnExcept();
    }

    @AfterEach
    void tearDown() {
        ConfigFiles.use(null);
        FeatureStates.none();
    }

    private void steady(DisconnectTrace t) {
        for (int i = 0; i <= DisconnectTrace.STEADY_FRAMES; i++) {
            now.addAndGet(16 * MS);
            t.onFrame();
        }
    }

    @Test
    void leavingAWorldCutsTheFrameWatchShortAndStartsTheTrace() {
        DisconnectTrace t = new DisconnectTrace(now::get, log::add, false);
        FrameWatch frames = new FrameWatch(now::get, log::add);
        frames.arm("minecraft:overworld", "minecraft:the_nether");
        now.addAndGet(MS);
        frames.onFrame();
        t.disconnecting(false, frames);
        assertFalse(t.active(), "quick play's disconnect on the title screen");
        t.disconnecting(true, frames);
        assertTrue(t.active());
        assertTrue(log.get(0).endsWith("cut short by leaving the world)"), log.toString());
        steady(t);
        FeatureStates.allOnExcept(Feature.DIAGNOSTICS);
        t.disconnecting(true, frames);
        assertFalse(t.active(), "timing lines off");
    }

    @Test
    void aClosingConnectionStartsTheTraceInAWorldAndIsOnlyAMarkOtherwise() {
        DisconnectTrace t = new DisconnectTrace(now::get, log::add, false);
        t.connectionClosing(false);
        assertFalse(t.active());
        t.connectionClosing(true);
        assertTrue(t.active());
        t.connectionClosing(false);
        steady(t);
        assertEquals(
                List.of("disconnect +0.0 ms connection close start", "disconnect +0.0 ms connection close start"),
                log.stream().filter(l -> l.contains("close start")).toList());
        FeatureStates.allOnExcept(Feature.DIAGNOSTICS);
        t.connectionClosing(true);
        assertFalse(t.active());
    }

    @Test
    void aDimensionChangeArmsTheFrameWatchWithTimingLinesOn() {
        FrameWatch frames = new FrameWatch(now::get, log::add);
        frames.changing(null, "minecraft:overworld");
        now.addAndGet(MS);
        frames.onFrame();
        frames.cut();
        assertTrue(log.get(0).startsWith("longest frame after joining => minecraft:overworld"), log.toString());
        frames.changing("minecraft:overworld", "minecraft:the_end");
        now.addAndGet(MS);
        frames.onFrame();
        frames.cut();
        assertTrue(log.get(1).startsWith("longest frame after minecraft:overworld => minecraft:the_end"));
        FeatureStates.allOnExcept(Feature.DIAGNOSTICS);
        frames.changing("a", "b");
        frames.onFrame();
        frames.cut();
        assertEquals(2, log.size());
    }

    @Test
    void aDistantHorizonsCloseIsNotedWhereItRuns() {
        assertEquals(
                "distant horizons close running in the background", AsyncDisconnect.closeNote(true, "Render thread"));
        assertEquals("distant horizons close on Render thread", AsyncDisconnect.closeNote(false, "Render thread"));
    }
}
