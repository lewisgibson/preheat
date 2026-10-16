package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What runs: the startup check, the settings, and features that fail at runtime. */
class FeaturesTest {
    @TempDir
    Path tmp;

    @BeforeEach
    void allOn() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        Map<Feature, FeatureCheck.Result> on = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            on.put(f, new FeatureCheck.Result(FeatureCheck.State.ON, List.of()));
        }
        on.put(Feature.BACKGROUND_DH_CLOSE, new FeatureCheck.Result(FeatureCheck.State.NOT_NEEDED, List.of("no DH")));
        on.put(Feature.BUILD_SPLIT, new FeatureCheck.Result(FeatureCheck.State.OFF, List.of("a", "b")));
        Features.set(on);
    }

    @AfterEach
    void reset() {
        ConfigFiles.use(null);
        Features.set(Map.of());
    }

    @Test
    void nothingTickedTurnsEverythingOffButTheAlwaysOnHooks() {
        PreheatConfig c = new PreheatConfig().normalize();
        for (Feature f : Feature.values()) {
            assertTrue(Features.on(f, c), f.name());
        }
        KnownDimensions.VANILLA.forEach(id -> c.dimensions.put(id, false));
        c.moddedDimensions = false;
        for (Feature f : Feature.values()) {
            boolean always = f == Feature.BUILD_TRACKING || f == Feature.STARTUP;
            assertEquals(always, Features.on(f, c), f.name());
        }
    }

    @Test
    void prebuildNeedsAtLeastOneDimensionSwitchedOn() {
        PreheatConfig c = new PreheatConfig().normalize();
        KnownDimensions.VANILLA.forEach(id -> c.dimensions.put(id, false));
        assertTrue(Features.on(Feature.PREBUILD, c), "new dimensions are still on");
        c.moddedDimensions = false;
        assertFalse(Features.on(Feature.PREBUILD, c));
        c.dimensions.put(KnownDimensions.END, true);
        assertTrue(Features.on(Feature.PREBUILD, c));
    }

    @Test
    void onNeedsTheStartupCheckAndTheSettings() {
        assertTrue(Features.on(Feature.KEEP_PIPELINES));
        assertFalse(Features.on(Feature.BACKGROUND_DH_CLOSE), "not needed");
        assertFalse(Features.on(Feature.BUILD_SPLIT), "did not fit");
        assertNull(Features.whyUnavailable(Feature.KEEP_PIPELINES));
        assertEquals("no DH", Features.whyUnavailable(Feature.BACKGROUND_DH_CLOSE));
        assertEquals("a; b", Features.whyUnavailable(Feature.BUILD_SPLIT));
    }

    @Test
    void aFeatureThatThrowsIsOffForTheSession() {
        Features.guard(Feature.DIAGNOSTICS, () -> {});
        assertTrue(Features.available(Feature.DIAGNOSTICS));
        Features.guard(Feature.DIAGNOSTICS, () -> {
            throw new IllegalStateException("Iris changed");
        });
        assertFalse(Features.available(Feature.DIAGNOSTICS));
        assertFalse(Features.on(Feature.DIAGNOSTICS));
        assertEquals(
                "it stopped after an error this session (see the log)", Features.whyUnavailable(Feature.DIAGNOSTICS));
        Features.fail(Feature.DIAGNOSTICS, new LinkageError("again"));
        assertFalse(Features.available(Feature.DIAGNOSTICS), "logged once, still off");
        assertTrue(Features.available(Feature.STARTUP), "the others carry on");
    }

    @Test
    void beforeTheCheckEveryFeatureIsOff() {
        Features.set(Map.of());
        assertFalse(Features.available(Feature.STARTUP));
        assertEquals("Iris is not installed", Features.whyUnavailable(Feature.STARTUP));
        assertFalse(Features.result(Feature.STARTUP).on());
    }

    @Test
    void anAppliedFeatureStillWaitsForItsSetting() {
        PreheatConfig off = new PreheatConfig().normalize();
        off.enabled = false;
        PreheatConfig.save(off);
        assertTrue(Features.available(Feature.KEEP_PIPELINES));
        assertFalse(Features.on(Feature.KEEP_PIPELINES));
        assertTrue(Features.on(Feature.STARTUP), "the always-on hooks");
    }
}
