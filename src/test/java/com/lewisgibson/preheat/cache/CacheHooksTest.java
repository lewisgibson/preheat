package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureCheck;
import com.lewisgibson.preheat.feature.Features;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regression tests for the review's must-fix 3: no error in the cache ever reaches Iris. */
class CacheHooksTest {
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void allOn() {
        Map<Feature, FeatureCheck.Result> on = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            on.put(f, new FeatureCheck.Result(FeatureCheck.State.ON, List.of()));
        }
        Features.set(on);
    }

    @AfterEach
    void reset() {
        Features.set(Map.of());
    }

    @Test
    void aHitIsUsedAndAMissIsRemembered() {
        assertNull(CacheHooks.beforeCompile(
                () -> "k", k -> 9, p -> calls.add("use " + p), k -> calls.add("miss"), p -> calls.add("discard")));
        assertEquals(
                "k",
                CacheHooks.beforeCompile(
                        () -> "k",
                        k -> 0,
                        p -> calls.add("use"),
                        k -> calls.add("miss " + k),
                        p -> calls.add("discard")));
        assertNull(CacheHooks.beforeCompile(
                () -> null, k -> 9, p -> calls.add("use"), k -> calls.add("miss"), p -> calls.add("discard")));
        assertEquals(List.of("use 9", "miss k"), calls);
        assertTrue(Features.available(Feature.PROGRAM_CACHE));
    }

    @Test
    void anErrorWhileKeyingTurnsTheCacheOffAndIrisCompilesAsUsual() {
        assertNull(CacheHooks.beforeCompile(
                () -> {
                    throw new IllegalArgumentException("bad source");
                },
                k -> 9,
                p -> calls.add("use"),
                k -> calls.add("miss"),
                p -> calls.add("discard")));
        assertTrue(calls.isEmpty());
        assertFalse(Features.available(Feature.PROGRAM_CACHE));
    }

    @Test
    void aLinkageErrorWhileLoadingIsCaughtToo() {
        assertNull(CacheHooks.beforeCompile(
                () -> "k",
                k -> {
                    throw new NoSuchMethodError("glProgramBinary");
                },
                p -> calls.add("use"),
                k -> calls.add("miss"),
                p -> calls.add("discard")));
        assertFalse(Features.available(Feature.PROGRAM_CACHE));
    }

    @Test
    void aProgramThatCouldNotBeHandedOverIsDeleted() {
        assertNull(CacheHooks.beforeCompile(
                () -> "k",
                k -> 9,
                p -> {
                    throw new IllegalStateException("accessor");
                },
                k -> calls.add("miss"),
                p -> calls.add("discard " + p)));
        assertEquals(List.of("discard 9"), calls);
        assertFalse(Features.available(Feature.PROGRAM_CACHE));
    }

    @Test
    void anErrorWhileSavingLeavesIrisAlone() {
        CacheHooks.afterCompile(null, () -> calls.add("save"));
        assertTrue(calls.isEmpty(), "nothing to save");
        CacheHooks.afterCompile("k", () -> {
            throw new NoClassDefFoundError("GL41C");
        });
        assertFalse(Features.available(Feature.PROGRAM_CACHE));
    }
}
