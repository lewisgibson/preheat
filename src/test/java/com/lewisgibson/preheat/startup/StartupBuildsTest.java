package com.lewisgibson.preheat.startup;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The builds around Iris's first one, against a fake Iris and loading screen. */
class StartupBuildsTest {
    @TempDir
    Path tmp;

    boolean pack = true;
    final Set<String> builtIds = new HashSet<>();
    final List<String> calls = new ArrayList<>();
    RuntimeException closeFails;
    RuntimeException prebuildFails;

    final StartupBuilds builds = new StartupBuilds(
            () -> pack,
            () -> Set.of(new NamespacedId("minecraft:the_nether")),
            d -> builtIds.contains(String.valueOf(d)),
            ds -> {
                if (prebuildFails != null) {
                    throw prebuildFails;
                }
                calls.add("prebuild " + ds);
            },
            planned -> calls.add("open " + planned),
            () -> {
                calls.add("close");
                if (closeFails != null) {
                    throw closeFails;
                }
            });

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

    @Test
    void theLoadingScreenCountsTheOverworldAndEveryDimensionPreparedAfterIt() {
        builds.begin();
        assertEquals(List.of("open [minecraft:overworld, minecraft:the_nether, minecraft:the_end]"), calls);
    }

    @Test
    void whatIsBuiltAlreadyIsNotCounted() {
        builtIds.add("minecraft:overworld");
        builtIds.add("minecraft:the_end");
        builds.begin();
        assertEquals(List.of("open [minecraft:the_nether]"), calls);
    }

    @Test
    void withoutThePrebuildOnlyTheOverworldIsCounted() {
        FeatureStates.allOnExcept(Feature.PREBUILD);
        builds.begin();
        assertEquals(List.of("open [minecraft:overworld]"), calls);
    }

    @Test
    void noLoadingScreenWithoutTheFeatureOrAShaderPack() {
        FeatureStates.allOnExcept(Feature.LOADING_PROGRESS);
        builds.begin();
        FeatureStates.allOnExcept();
        pack = false;
        builds.begin();
        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void aFailureCountingTheBuildsTurnsTheLoadingScreenOff() {
        StartupBuilds broken = new StartupBuilds(
                () -> true,
                Set::of,
                d -> {
                    throw new IllegalStateException("Iris changed");
                },
                ds -> {},
                planned -> calls.add("open"),
                () -> {});
        broken.begin();
        assertTrue(calls.isEmpty());
        assertFalse(Features.available(Feature.LOADING_PROGRESS));
    }

    @Test
    void afterIrisBuildsTheOtherDimensionsArePreparedAndTheScreenGoes() {
        builds.end();
        assertEquals(List.of("prebuild [minecraft:the_nether, minecraft:the_end]", "close"), calls);
    }

    @Test
    void withoutAPackOrThePrebuildTheScreenStillGoes() {
        pack = false;
        builds.end();
        FeatureStates.allOnExcept(Feature.PREBUILD);
        pack = true;
        builds.end();
        assertEquals(List.of("close", "close"), calls);
    }

    @Test
    void aFailedPrebuildTurnsItOffAndAScreenThatWillNotCloseIsOnlyLogged() {
        prebuildFails = new IllegalStateException("build failed");
        closeFails = new IllegalStateException("overlay gone");
        try (LogLines log = LogLines.capture()) {
            builds.end();
            assertTrue(
                    log.has("WARN PREHEAT Could not close the shader loading screen"),
                    log.lines().toString());
        }
        assertEquals(List.of("close"), calls);
        assertFalse(Features.available(Feature.PREBUILD));
    }
}
