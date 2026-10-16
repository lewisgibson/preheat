package com.lewisgibson.preheat.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class BuildTimerTest {
    private static final long MS = 1_000_000L;

    @Test
    void chargesEachStepItsOwnTime() {
        AtomicLong now = new AtomicLong();
        AtomicInteger pulses = new AtomicInteger();
        BuildTimer t = new BuildTimer(now::get, pulses::incrementAndGet);
        t.begin("minecraft:the_nether");
        now.addAndGet(100 * MS); // other
        t.enter(BuildTimer.Step.PREPROCESS);
        now.addAndGet(300 * MS);
        t.exit(BuildTimer.Step.PREPROCESS);
        t.enter(BuildTimer.Step.TRANSFORM);
        now.addAndGet(200 * MS);
        t.exit(BuildTimer.Step.TRANSFORM);
        t.enter(BuildTimer.Step.LINK); // link wraps two compiles: they are not link's
        now.addAndGet(50 * MS);
        t.enter(BuildTimer.Step.COMPILE);
        now.addAndGet(1000 * MS);
        t.exit(BuildTimer.Step.COMPILE);
        t.enter(BuildTimer.Step.COMPILE);
        now.addAndGet(2000 * MS);
        t.exit(BuildTimer.Step.COMPILE);
        now.addAndGet(150 * MS);
        t.exit(BuildTimer.Step.LINK);
        t.program(false);
        t.enter(BuildTimer.Step.BINARY_LOAD);
        now.addAndGet(5 * MS);
        t.exit(BuildTimer.Step.BINARY_LOAD);
        t.program(true);
        t.program(false);
        assertEquals(
                "3805.0 ms = preprocess 300.0, transform 200.0, compile 3000.0 (2 shaders), link 200.0,"
                        + " binary load 5.0, binary save 0.0, other 100.0; 3 programs, 1 from the cache",
                t.end());
        assertEquals(6, pulses.get(), "every step that ends pulses the progress screen");
        assertNull(t.end());
    }

    @Test
    void ignoresStepsOutsideABuildAndNestedBegins() {
        AtomicLong now = new AtomicLong();
        BuildTimer t = new BuildTimer(now::get, () -> {});
        t.enter(BuildTimer.Step.COMPILE);
        t.exit(BuildTimer.Step.COMPILE);
        t.program(true);
        assertFalse(t.active());
        t.begin("a");
        t.begin("b");
        now.addAndGet(MS);
        assertEquals(
                "1.0 ms = preprocess 0.0, transform 0.0, compile 0.0 (0 shaders), link 0.0, binary load 0.0,"
                        + " binary save 0.0, other 1.0; 0 programs, 0 from the cache",
                t.end());
    }

    @Test
    void aStepLeftOpenByAnExceptionDoesNotBreakTheRest() {
        AtomicLong now = new AtomicLong();
        BuildTimer t = new BuildTimer(now::get, () -> {});
        t.begin("x");
        t.enter(BuildTimer.Step.LINK);
        t.exit(BuildTimer.Step.COMPILE);
        now.addAndGet(MS);
        t.exit(BuildTimer.Step.LINK);
        assertEquals(
                "1.0 ms = preprocess 0.0, transform 0.0, compile 0.0 (0 shaders), link 1.0, binary load 0.0,"
                        + " binary save 0.0, other 0.0; 0 programs, 0 from the cache",
                t.end());
    }

    @Test
    void anUnmatchedExitIsIgnoredAndTheBuildSaysWhatItIs() {
        AtomicLong now = new AtomicLong();
        AtomicInteger pulses = new AtomicInteger();
        BuildTimer t = new BuildTimer(now::get, pulses::incrementAndGet);
        assertFalse(t.active());
        assertNull(t.label());
        t.enter(BuildTimer.Step.COMPILE);
        t.exit(BuildTimer.Step.COMPILE);
        assertEquals(0, pulses.get(), "outside a build nothing is timed");
        t.begin("minecraft:the_end");
        assertTrue(t.active());
        assertEquals("minecraft:the_end", t.label());
        t.exit(BuildTimer.Step.LINK);
        t.enter(BuildTimer.Step.LINK);
        t.exit(BuildTimer.Step.COMPILE);
        assertEquals(0, pulses.get(), "an exit that matches no enter is not a step");
        t.program(true);
        assertEquals(1, t.programs());
    }
}
