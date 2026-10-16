package com.lewisgibson.preheat.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.feature.Features;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One build as the cache, the loading screen and the log see it. */
class BuildTrackingTest {
    @TempDir
    Path tmp;

    final AtomicLong now = new AtomicLong();
    final List<String> calls = new ArrayList<>();
    String cacheLine = "verified 3 cached binaries";

    final BuildTracking tracking = new BuildTracking(
            now::get,
            () -> calls.add("cache begin"),
            () -> {
                calls.add("cache end");
                return cacheLine;
            },
            d -> calls.add("started " + d),
            n -> calls.add("pulse " + n),
            (d, n) -> calls.add("ended " + d + " " + n));

    @BeforeEach
    void allOn() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        FeatureStates.allOnExcept();
    }

    @AfterEach
    void reset() {
        ConfigFiles.use(null);
        FeatureStates.none();
    }

    private void oneProgram() {
        tracking.timer().enter(BuildTimer.Step.LINK);
        now.addAndGet(2_000_000L);
        tracking.timer().program(false);
        tracking.timer().exit(BuildTimer.Step.LINK);
    }

    @Test
    void aBuildTellsTheCacheAndTheLoadingScreenAndEndsInOneLogLine() {
        try (LogLines log = LogLines.capture()) {
            tracking.begin("minecraft:the_nether");
            tracking.begin("minecraft:the_end");
            oneProgram();
            tracking.end();
            tracking.end();
            assertEquals(
                    List.of(
                            "cache begin",
                            "started minecraft:the_nether",
                            "pulse 1",
                            "cache end",
                            "ended minecraft:the_nether 1"),
                    calls,
                    "a nested begin and a second end change nothing");
            assertTrue(
                    log.has("INFO PREHEAT Built the Nether shaders (minecraft:the_nether) in 2.0 ms ="),
                    log.lines().toString());
            assertTrue(
                    log.has("from the cache; verified 3 cached binaries"),
                    log.lines().toString());
        }
    }

    @Test
    void withoutTheCacheOrTheLoadingScreenOnlyTheTimingRemains() {
        FeatureStates.allOnExcept(Feature.PROGRAM_CACHE, Feature.LOADING_PROGRESS);
        try (LogLines log = LogLines.capture()) {
            tracking.begin("minecraft:overworld");
            oneProgram();
            tracking.end();
            assertTrue(calls.isEmpty(), calls.toString());
            assertTrue(log.has("1 programs, 0 from the cache"), log.lines().toString());
            assertFalse(log.has("from the cache;"), "no cache part");
        }
    }

    @Test
    void anEmptyCachePartAndDiagnosticsOffWriteNothingExtra() {
        cacheLine = "";
        try (LogLines log = LogLines.capture()) {
            tracking.begin("minecraft:overworld");
            tracking.end();
            assertTrue(log.has("0 programs, 0 from the cache"), log.lines().toString());
            assertFalse(log.has("from the cache;"));
        }
        Features.fail(Feature.DIAGNOSTICS, new IllegalStateException("test"));
        try (LogLines log = LogLines.capture()) {
            tracking.begin("minecraft:overworld");
            tracking.end();
            assertFalse(log.has("Built the"), log.lines().toString());
        }
    }

    @Test
    void aLoadingScreenThatFailsIsTurnedOffAndTheBuildGoesOn() {
        BuildTracking failing = new BuildTracking(
                now::get,
                () -> {},
                () -> "",
                d -> {
                    throw new IllegalStateException("overlay gone");
                },
                n -> {},
                (d, n) -> {});
        failing.begin("minecraft:overworld");
        assertFalse(Features.available(Feature.LOADING_PROGRESS));
        failing.timer().enter(BuildTimer.Step.COMPILE);
        failing.timer().exit(BuildTimer.Step.COMPILE);
        assertEquals("minecraft:overworld", failing.timer().label());
    }
}
