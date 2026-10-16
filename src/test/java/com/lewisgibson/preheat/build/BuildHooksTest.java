package com.lewisgibson.preheat.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.FeatureStates;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What Iris's every-frame prepare and the driver's link-status query mean for a build. */
class BuildHooksTest {
    private static final int GL_LINK_STATUS = 0x8B82;

    @TempDir
    Path tmp;

    final AtomicLong now = new AtomicLong();
    final AtomicInteger pulses = new AtomicInteger();
    final BuildTracking tracking =
            new BuildTracking(now::get, () -> {}, () -> "", d -> {}, n -> pulses.incrementAndGet(), (d, n) -> {});

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

    @Test
    void aPrepareBuildsOnlyForADimensionWithoutAPipelineUnderAShaderPack() {
        AtomicInteger packAsked = new AtomicInteger();
        tracking.prepareStarted("minecraft:overworld", true, () -> packAsked.incrementAndGet() > 0);
        assertEquals(0, packAsked.get(), "Iris is not asked on the frames that build nothing");
        tracking.prepareStarted("minecraft:overworld", false, () -> false);
        assertNull(tracking.timer().label());
        tracking.prepareStarted("minecraft:the_nether", false, () -> true);
        assertEquals("minecraft:the_nether", tracking.timer().label());
    }

    @Test
    void askingForTheLinkStatusIsTimedAsLinking() {
        tracking.begin("minecraft:overworld");
        tracking.programQuery(0x8B81, true);
        tracking.programQuery(0x8B81, false);
        assertEquals(0, pulses.get(), "another query is not a step");
        tracking.programQuery(GL_LINK_STATUS, true);
        now.addAndGet(3_000_000L);
        tracking.programQuery(GL_LINK_STATUS, false);
        assertEquals(1, pulses.get());
        String split = tracking.timer().end();
        assertTrue(split != null && split.contains("link 3.0"), split);
        assertFalse(tracking.timer().active());
    }
}
