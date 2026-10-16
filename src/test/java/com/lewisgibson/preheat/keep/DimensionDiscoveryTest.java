package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.feature.Features;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dimensions the settings list, from the levels the game sets; and the small decisions around keeping. */
class DimensionDiscoveryTest {
    @TempDir
    Path tmp;

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
    void theServersDimensionsAndTheOneEnteredAreListed() {
        DimensionDiscovery.levelSet(
                "aether:the_aether",
                level -> level,
                () -> "connection",
                c -> List.of(KnownDimensions.OVERWORLD, "aether:the_aether", "backrooms:level0"),
                () -> Set.of("aether:the_aether"));
        assertTrue(PreheatConfig.get().dimensions.containsKey("aether:the_aether"));
        assertFalse(PreheatConfig.get().dimensions.containsKey("backrooms:level0"), "no shaders of its own");
    }

    @Test
    void leavingAWorldListsTheServersDimensionsOnly() {
        DimensionDiscovery.<String, String>levelSet(
                null,
                level -> {
                    throw new AssertionError("there is no level");
                },
                () -> "connection",
                c -> List.of("aether:the_aether"),
                () -> Set.of("aether:the_aether"));
        assertFalse(PreheatConfig.get().dimensions.containsKey("aether:the_aether"), "not prepared at startup");
    }

    @Test
    void withoutAConnectionOrWithKeepingOffNothingIsListedAndAnErrorTurnsKeepingOff() {
        List<String> asked = new ArrayList<>();
        DimensionDiscovery.<String, String>levelSet(
                "x:y", l -> l, () -> null, c -> List.of("never"), () -> Set.of("x:y"));
        FeatureStates.allOnExcept(Feature.KEEP_PIPELINES);
        DimensionDiscovery.levelSet(
                "x:y",
                l -> l,
                () -> "c",
                c -> {
                    asked.add("asked");
                    return List.of();
                },
                () -> Set.of("x:y"));
        assertTrue(asked.isEmpty());
        assertEquals(
                KnownDimensions.VANILLA.size(), PreheatConfig.get().dimensions.size());
        FeatureStates.allOnExcept();
        DimensionDiscovery.levelSet(
                "x:y",
                l -> l,
                () -> "c",
                c -> {
                    throw new IllegalStateException("levels changed shape");
                },
                Set::of);
        assertFalse(Features.available(Feature.KEEP_PIPELINES));
    }

    @Test
    void theVanillaThreeAndIrissOwnEndKeyAreLetGoLast() {
        assertTrue(KnownDimensions.letGoLast(KnownDimensions.NETHER, false));
        assertTrue(KnownDimensions.letGoLast("iris:end", true));
        assertFalse(KnownDimensions.letGoLast("aether:the_aether", false));
    }

    @Test
    void aPrebuildNeedsIrissOwnActivePipelineAndAShaderPack() {
        KeptPipelinesTest.FakeHost host = new KeptPipelinesTest.FakeHost();
        KeptPipelines<String, KeptPipelinesTest.Pipe> kept = new KeptPipelines<>(host);
        KeptPipelinesTest.Pipe active = host.irisPrepare(KnownDimensions.OVERWORLD);
        kept.prebuildFrom(List.of(KnownDimensions.NETHER), null, true, true);
        kept.prebuildFrom(List.of(KnownDimensions.NETHER), active, false, true);
        kept.prebuildFrom(List.of(KnownDimensions.NETHER), active, true, false);
        assertTrue(host.calls.isEmpty(), host.calls.toString());
        kept.prebuildFrom(List.of(KnownDimensions.NETHER), active, true, true);
        assertTrue(host.calls.contains("build minecraft:the_nether"));
    }

    @Test
    void aFailedBuildsStorageBuffersAreDeletedNewestFirstAndOnlyThose() {
        List<String> buffers = new ArrayList<>(List.of("kept", "new1", "new2"));
        List<String> destroyed = new ArrayList<>();
        KeptPipelines.dropAddedSince(buffers, 1, destroyed::add);
        assertEquals(List.of("new2", "new1"), destroyed);
        assertEquals(List.of("kept"), buffers);
    }
}
