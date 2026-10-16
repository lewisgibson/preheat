package com.lewisgibson.preheat.progress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** When a frame is drawn mid-build, and when the window is only pumped, against a fake game and clock. */
class FramesTest {
    private static final long MS = 1_000_000L;

    /** The game as far as drawing a frame sees it. */
    static final class Game {
        boolean inWorld;
        boolean window = true;
        long frameMs = 5;
    }

    final AtomicLong now = new AtomicLong(1_000 * MS);
    final List<String> calls = new ArrayList<>();
    final FakeStateGl gl = new FakeStateGl();

    @Nullable
    Game game = new Game();

    boolean renderThread = true;
    boolean sdl = true;

    Frames<Game> frames() {
        return new Frames<>(
                now::get,
                () -> renderThread,
                () -> game,
                g -> g.inWorld,
                g -> g.window,
                g -> {
                    calls.add("frame");
                    now.addAndGet(g.frameMs * MS);
                },
                gl,
                () -> calls.add("pump"),
                sdl);
    }

    @Test
    void aFrameIsDrawnOnlyBetweenFramesOnTheRenderThreadWithNoWorldAndAWindow() {
        Frames<Game> f = frames();
        assertTrue(f.canRender());
        f.enterFrame();
        assertFalse(f.canRender(), "inside another frame");
        f.exitFrame();
        f.exitFrame();
        assertTrue(f.canRender(), "an extra exit never goes below zero");
        renderThread = false;
        assertFalse(f.canRender());
        renderThread = true;
        game.inWorld = true;
        assertFalse(f.canRender());
        game.inWorld = false;
        game.window = false;
        assertFalse(f.canRender());
        game = null;
        assertFalse(f.canRender(), "before the game exists");
    }

    @Test
    void aFrameRestoresTheGlStateAndIsCountedAndTimed() {
        Frames<Game> f = frames();
        f.frameNow();
        assertEquals(List.of("frame"), calls);
        assertTrue(gl.calls.contains("useProgram 0"), "the state was put back");
        assertArrayEquals(new long[] {1, 5 * MS}, f.takeStats());
        assertArrayEquals(new long[] {0, 0}, f.takeStats(), "taking the numbers resets them");
    }

    @Test
    void aFrameThatThrowsStillPutsTheStateBack() {
        Frames<Game> f = new Frames<>(
                now::get,
                () -> true,
                () -> game,
                g -> false,
                g -> true,
                g -> {
                    throw new IllegalStateException("render failed");
                },
                gl,
                () -> {},
                true);
        assertThrows(IllegalStateException.class, f::frameNow);
        assertTrue(gl.calls.contains("useProgram 0"));
        assertEquals(1, f.takeStats()[0]);
    }

    @Test
    void framesAreSpacedByAtLeastAHundredMillisecondsAndTwentyTimesTheirCost() {
        Frames<Game> f = frames();
        game.frameMs = 10;
        f.frameNow();
        calls.clear();
        now.addAndGet(150 * MS);
        f.frameIfDue();
        assertEquals(List.of("pump"), calls, "a 10 ms frame waits 200 ms, so only the window is pumped");
        now.addAndGet(60 * MS);
        f.frameIfDue();
        assertEquals(List.of("pump", "frame"), calls);
    }

    @Test
    void whereNoFrameCanBeDrawnTheWindowIsPumpedEveryHundredMilliseconds() {
        game.inWorld = true;
        Frames<Game> f = frames();
        f.reset();
        f.frameIfDue();
        assertTrue(calls.isEmpty(), "a build's first pump waits a full interval");
        now.addAndGet(100 * MS);
        f.frameNow();
        assertEquals(List.of("pump"), calls);
        now.addAndGet(50 * MS);
        f.pumpIfDue();
        assertEquals(List.of("pump"), calls, "not again so soon");
        now.addAndGet(100 * MS);
        renderThread = false;
        f.pumpIfDue();
        assertEquals(List.of("pump"), calls, "never off the render thread");
        game = null;
        renderThread = true;
        f.frameNow();
        assertEquals(List.of("pump", "pump"), calls, "no game yet: pumped only");
    }

    @Test
    void withoutSdlNothingIsPumped() {
        sdl = false;
        game.inWorld = true;
        Frames<Game> f = frames();
        now.addAndGet(200 * MS);
        f.pumpIfDue();
        assertTrue(calls.isEmpty());
    }
}
