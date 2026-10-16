package com.lewisgibson.preheat.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The loading screen through the startup builds, against a fake overlay and game. */
class LoadingScreenTest {
    private static final long MS = 1_000_000L;

    /** An overlay's progress, recording what it was shown. */
    static final class Shown implements LoadingScreen.Overlay {
        final List<String> lines = new ArrayList<>();
        boolean finished;

        @Override
        public void update(float fraction, ProgressPlan.Line line) {
            lines.add(line.dimension() + " " + line.done() + "/" + line.total());
        }

        @Override
        public void finish() {
            finished = true;
        }

        @Override
        public boolean finished() {
            return finished;
        }
    }

    @TempDir
    Path tmp;

    final AtomicLong now = new AtomicLong(1_000 * MS);
    final List<String> calls = new ArrayList<>();
    final List<Shown> shown = new ArrayList<>();
    final FramesTest.Game game = new FramesTest.Game();
    final Frames<FramesTest.Game> frames = new Frames<>(
            now::get,
            () -> true,
            () -> game,
            g -> g.inWorld,
            g -> g.window,
            g -> {
                calls.add("frame");
                now.addAndGet(5 * MS);
            },
            new FakeStateGl(),
            () -> calls.add("pump"),
            true);
    LoadingScreen<Shown> screen;

    @BeforeEach
    void setUp() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        Platforms.use(FakePlatform.client(tmp));
        FeatureStates.allOnExcept();
        screen = new LoadingScreen<>(() -> "Complementary.zip", frames, Shown::new, o -> {
            calls.add("show");
            shown.add(o);
        });
    }

    @AfterEach
    void reset() {
        ConfigFiles.use(null);
        Platforms.use(null);
        FeatureStates.none();
    }

    @Test
    void theStartupBuildsDriveOneOverlayThatFadesWhenTheWindowCloses() {
        new BuildCounts(tmp.resolve("preheat").resolve("build-counts.properties"))
                .put("Complementary.zip", "minecraft:the_nether", 101);
        screen.openWindow(List.of("minecraft:overworld", "minecraft:the_nether"));
        screen.buildStarted("minecraft:overworld");
        assertEquals(List.of("show", "frame"), calls, "the overlay goes up with its first frame");
        Shown o = shown.get(0);
        assertEquals(List.of("Overworld 0/201"), o.lines);
        screen.pulse(40);
        screen.buildEnded("minecraft:overworld", 99);
        screen.buildStarted("minecraft:the_nether");
        assertEquals(1, shown.size(), "one overlay for every startup build");
        assertEquals(List.of("Overworld 0/201", "Overworld 40/201", "null 99/200", "Nether 99/200"), o.lines);
        try (LogLines log = LogLines.capture()) {
            screen.closeWindow();
            assertTrue(o.finished);
            assertTrue(
                    log.has("INFO PREHEAT Loading screen: 1 frames drawn during shader builds, 5.0 ms in total"),
                    log.lines().toString());
        }
        assertEquals(
                99,
                new BuildCounts(tmp.resolve("preheat").resolve("build-counts.properties"))
                        .get("Complementary.zip", "minecraft:overworld"),
                "remembered for next time");
    }

    @Test
    void outsideTheStartupWindowABuildOnlyKeepsTheWindowAnswering() {
        screen.buildStarted("minecraft:the_end");
        now.addAndGet(150 * MS);
        screen.pulse(1);
        screen.buildEnded("minecraft:the_end", 0);
        screen.closeWindow();
        assertEquals(List.of("pump"), calls);
        assertEquals(
                null,
                new BuildCounts(tmp.resolve("preheat").resolve("build-counts.properties"))
                        .get("Complementary.zip", "minecraft:the_end"),
                "a build that made nothing is not remembered");
    }

    @Test
    void noOverlayGoesUpWithTheFeatureOffOrWhereNoFrameCanBeDrawn() {
        game.inWorld = true;
        screen.openWindow(List.of());
        screen.buildStarted("minecraft:overworld");
        now.addAndGet(150 * MS);
        screen.pulse(5);
        assertEquals(List.of("pump"), calls, "in a world the window is only pumped");
        screen.buildEnded("minecraft:overworld", 10);
        FeatureStates.allOnExcept(Feature.LOADING_PROGRESS);
        game.inWorld = false;
        screen.buildStarted("minecraft:the_nether");
        try (LogLines log = LogLines.capture()) {
            screen.closeWindow();
            assertFalse(log.has("Loading screen"), "no overlay, nothing to report");
        }
        assertTrue(shown.isEmpty());
    }

    @Test
    void anOverlayWithNoFramesDrawnSaysNothingWhenItCloses() {
        screen.openWindow(List.of());
        game.window = true;
        screen.buildStarted("minecraft:overworld");
        frames.takeStats();
        try (LogLines log = LogLines.capture()) {
            screen.closeWindow();
            assertFalse(log.has("Loading screen"), log.lines().toString());
        }
    }

    @Test
    void anOverlayLeftUpFromEarlierIsFinishedButNotTheOneInUse() {
        Shown left = new Shown();
        screen.finishIfStale(left);
        assertTrue(left.finished, "nothing is in use");
        screen.finishIfStale("not an overlay of Preheat's");
        screen.openWindow(List.of());
        screen.buildStarted("minecraft:overworld");
        Shown current = shown.get(0);
        screen.finishIfStale(current);
        assertFalse(current.finished);
        Shown done = new Shown();
        done.finished = true;
        done.lines.add("kept");
        screen.finishIfStale(done);
        assertEquals(List.of("kept"), done.lines);
    }
}
