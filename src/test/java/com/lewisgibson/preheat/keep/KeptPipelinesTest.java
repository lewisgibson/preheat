package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureCheck;
import com.lewisgibson.preheat.feature.Features;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pipeline-keeping decisions against a fake PipelineManager. */
class KeptPipelinesTest {
    /** A pipeline: just a name, compared by identity as the real ones are. */
    static final class Pipe {
        final String dim;

        Pipe(String dim) {
            this.dim = dim;
        }

        @Override
        public String toString() {
            return "Pipe(" + dim + ")";
        }
    }

    /** Iris's manager as far as keeping sees it, recording every call. */
    static class FakeHost implements KeptPipelines.Host<String, Pipe> {
        final Map<String, Pipe> map = new LinkedHashMap<>();

        @Nullable
        Pipe active;

        final List<String> calls = new ArrayList<>();
        final Set<String> off = new HashSet<>();
        final Set<String> failBuild = new HashSet<>();
        KeptBudget.@Nullable Memory memory;
        /** Free MB taken by each build, when memory is reported. */
        long buildCostMb = 0;

        boolean failReactivate;
        long now = 1;
        int sodium;

        @Override
        public Map<String, Pipe> preheat$pipelines() {
            return map;
        }

        @Override
        public @Nullable Pipe preheat$active() {
            return active;
        }

        @Override
        public void preheat$setActive(@Nullable Pipe pipeline) {
            active = pipeline;
        }

        @Override
        public void preheat$deactivate(Pipe pipeline) {
            calls.add("deactivate " + pipeline.dim);
        }

        @Override
        public void preheat$reactivate(Pipe pipeline) {
            if (failReactivate) {
                throw new IllegalStateException("reactivate failed");
            }
            calls.add("reactivate " + pipeline.dim);
        }

        @Override
        public void preheat$resetTextureState() {}

        @Override
        public void preheat$destroy(String id, Pipe pipeline) {
            calls.add("destroy " + id);
        }

        @Override
        public void preheat$signalSodium() {
            sodium++;
        }

        @Override
        public boolean preheat$dimensionOn(String id) {
            return !off.contains(id);
        }

        @Override
        public boolean preheat$vanilla(String id) {
            return id.startsWith("minecraft:");
        }

        @Override
        public KeptBudget.@Nullable Memory preheat$memory() {
            return memory;
        }

        @Override
        public Pipe preheat$build(String id) throws Exception {
            calls.add("build " + id);
            if (failBuild.contains(id)) {
                throw new Exception("build failed");
            }
            KeptBudget.Memory m = memory;
            if (m != null) {
                memory = new KeptBudget.Memory(m.availableMb() - buildCostMb, m.totalMb());
            }
            return new Pipe(id);
        }

        @Override
        public long preheat$nanoTime() {
            return now++;
        }

        /** What Iris's preparePipeline does between our two hooks: the kept one, or a new one. */
        Pipe irisPrepare(String dim) {
            Pipe p = map.computeIfAbsent(dim, Pipe::new);
            active = p;
            return p;
        }
    }

    @TempDir
    Path tmp;

    FakeHost host;
    KeptPipelines<String, Pipe> kept;

    @BeforeEach
    void allOn() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        Map<Feature, FeatureCheck.Result> on = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            on.put(f, new FeatureCheck.Result(FeatureCheck.State.ON, List.of()));
        }
        Features.set(on);
        host = new FakeHost();
        kept = new KeptPipelines<>(host);
    }

    @AfterEach
    void reset() {
        ConfigFiles.use(null);
        Features.set(Map.of());
    }

    /** One dimension change as Iris and the mixin run it. */
    private void travel(String dim) {
        assertTrue(kept.onDimensionChange());
        kept.beforePrepare(dim);
        host.irisPrepare(dim);
        assertTrue(kept.afterPrepare(dim));
    }

    @Test
    void aDimensionChangePutsTheActivePipelineAsideAndBringsAKeptOneBack() {
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        travel("minecraft:the_nether");
        assertEquals(2, host.map.size());
        assertTrue(host.calls.contains("deactivate minecraft:overworld"));
        assertFalse(host.calls.stream().anyMatch(c -> c.startsWith("destroy")));
        assertEquals(1, host.sodium, "Sodium hears of the change as it would from Iris's destroy");
        host.calls.clear();
        travel("minecraft:overworld");
        assertSame(overworld, host.active, "the very same pipeline came back");
        assertEquals(List.of("deactivate minecraft:the_nether", "reactivate minecraft:overworld"), host.calls);
    }

    @Test
    void leavingADimensionSwitchedOffDestroysItsPipeline() {
        host.irisPrepare("minecraft:the_end");
        host.off.add("minecraft:the_end");
        travel("minecraft:overworld");
        assertTrue(host.calls.contains("destroy minecraft:the_end"));
        assertFalse(host.map.containsKey("minecraft:the_end"));
    }

    @Test
    void aKeptPipelineSwitchedOffSinceIsDroppedAtTheNextChangeButNeverTheDestination() {
        host.irisPrepare("minecraft:overworld");
        travel("minecraft:the_nether");
        travel("minecraft:the_end");
        host.off.add("minecraft:the_nether");
        host.off.add("minecraft:overworld");
        host.calls.clear();
        kept.onDimensionChange();
        kept.beforePrepare("minecraft:overworld");
        assertTrue(host.calls.contains("destroy minecraft:the_nether"));
        assertFalse(host.calls.contains("destroy minecraft:overworld"), "the destination is about to be used");
        assertTrue(host.map.containsKey("minecraft:the_end"), "the one just left is switched on");
    }

    @Test
    void withKeepingOffIrisDestroysAsAlways() {
        Features.fail(Feature.KEEP_PIPELINES, new IllegalStateException("test"));
        host.irisPrepare("minecraft:overworld");
        assertFalse(kept.onDimensionChange());
        assertTrue(host.calls.isEmpty());
    }

    @Test
    void aFailedReactivationTurnsKeepingOffAndTellsTheCallerToRebuild() {
        host.irisPrepare("minecraft:overworld");
        travel("minecraft:the_nether");
        host.failReactivate = true;
        kept.onDimensionChange();
        kept.beforePrepare("minecraft:overworld");
        host.irisPrepare("minecraft:overworld");
        assertFalse(kept.afterPrepare("minecraft:overworld"));
        assertFalse(Features.available(Feature.KEEP_PIPELINES));
    }

    @Test
    void aFailurePuttingAPipelineAsideTurnsKeepingOff() {
        host.irisPrepare("minecraft:overworld");
        FakeHost broken = new FakeHost() {
            @Override
            public void preheat$deactivate(Pipe pipeline) {
                throw new IllegalStateException("deactivate failed");
            }
        };
        broken.irisPrepare("minecraft:overworld");
        assertFalse(new KeptPipelines<>(broken).onDimensionChange());
        assertFalse(Features.available(Feature.KEEP_PIPELINES));
    }

    @Test
    void lowVideoMemoryLetsTheModdedDimensionUsedLongestAgoGoFirst() {
        host.memory = new KeptBudget.Memory(20_000, 32_000);
        host.irisPrepare("minecraft:overworld");
        travel("aether:the_aether");
        travel("twilight:forest");
        travel("minecraft:overworld");
        assertEquals(3, host.map.size());
        // The reserve on 32 GB is 15% (4800 MB); fall below it by a little.
        host.memory = new KeptBudget.Memory(4_500, 32_000);
        travel("minecraft:the_nether");
        assertTrue(host.calls.contains("destroy aether:the_aether"), host.calls.toString());
        assertFalse(host.calls.contains("destroy twilight:forest"), "one was enough to get above the reserve");
        assertTrue(host.map.containsKey("minecraft:overworld"), "vanilla last");
    }

    @Test
    void withoutMemoryReadingsAtMostTwoModdedDimensionsAreKept() {
        host.irisPrepare("minecraft:overworld");
        travel("a:one");
        travel("b:two");
        travel("c:three");
        travel("minecraft:overworld");
        assertTrue(host.calls.contains("destroy a:one"), host.calls.toString());
        assertEquals(3, host.map.size());
    }

    @Test
    void theSizeOfANewPipelineIsMeasuredFromFreeMemory() {
        host.memory = new KeptBudget.Memory(20_000, 32_000);
        host.irisPrepare("minecraft:overworld");
        assertTrue(kept.onDimensionChange());
        kept.beforePrepare("minecraft:the_nether");
        host.memory = new KeptBudget.Memory(18_000, 32_000);
        host.irisPrepare("minecraft:the_nether");
        kept.afterPrepare("minecraft:the_nether");
        assertEquals(2_000L, kept.sizes().get("minecraft:the_nether"));
    }

    @Test
    void prebuildBuildsWhatIsMissingStopsAtAFailureAndGivesTheActiveItsStateBack() {
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        host.failBuild.add("minecraft:the_end");
        kept.prebuild(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "x:y"), overworld);
        assertEquals(
                List.of(
                        "build minecraft:the_nether",
                        "deactivate minecraft:the_nether",
                        "build minecraft:the_end",
                        "reactivate minecraft:overworld"),
                host.calls);
        assertTrue(host.map.containsKey("minecraft:the_nether"));
        assertFalse(host.map.containsKey("minecraft:the_end"));
        assertSame(overworld, host.active, "nothing prepared at startup is made active");
    }

    @Test
    void prebuildStopsWhenTheNextWouldNotFitAndDoesNothingWhenAllAreBuilt() {
        host.memory = new KeptBudget.Memory(8_000, 32_000);
        host.buildCostMb = 2_500;
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        kept.prebuild(List.of("minecraft:the_nether", "minecraft:the_end"), overworld);
        assertTrue(host.map.containsKey("minecraft:the_nether"));
        assertFalse(host.map.containsKey("minecraft:the_end"), "5500 free less 2500 is under the 4800 reserve");
        host.calls.clear();
        kept.prebuild(List.of("minecraft:overworld", "minecraft:the_nether"), overworld);
        assertTrue(host.calls.isEmpty(), "nothing attempted, so nothing to give back");
    }

    @Test
    void prebuildDoesNothingWithKeepingOffAndStillGivesStateBackWhenABuildThrowsUnchecked() {
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        FakeHost exploding = new FakeHost() {
            @Override
            public Pipe preheat$build(String id) {
                throw new LinkageError("a changed Iris");
            }
        };
        exploding.irisPrepare("minecraft:overworld");
        new KeptPipelines<>(exploding).prebuild(List.of("minecraft:the_nether"), exploding.active);
        assertTrue(exploding.calls.contains("reactivate minecraft:overworld"));
        Features.fail(Feature.KEEP_PIPELINES, new IllegalStateException("test"));
        kept.prebuild(List.of("minecraft:the_nether"), overworld);
        assertTrue(host.calls.isEmpty());
    }

    @Test
    void dropInactiveKeepsOnlyTheActivePipeline() {
        host.irisPrepare("minecraft:overworld");
        travel("minecraft:the_nether");
        travel("minecraft:the_end");
        kept.dropInactive("test");
        assertEquals(List.of("minecraft:the_end"), List.copyOf(host.map.keySet()));
    }

    @Test
    void aNeverKeptDimensionIsUsedWhileThereAndLetGoOnLeaving() {
        host.irisPrepare("minecraft:overworld");
        // The real host's dimensionOn is PreheatConfig.dimensionOn, false for a neverKeep match (DimensionPolicyTest).
        host.off.add("rftoolsdim:mine");
        travel("rftoolsdim:mine");
        assertTrue(host.map.containsKey("rftoolsdim:mine"), "built and used while the player is there");
        host.calls.clear();
        travel("minecraft:overworld");
        assertTrue(host.calls.contains("destroy rftoolsdim:mine"), host.calls.toString());
        assertFalse(host.map.containsKey("rftoolsdim:mine"));
    }

    @Test
    void anErrorBeforePrepareTurnsKeepingOffInsteadOfReachingIris() {
        FakeHost broken = new FakeHost() {
            @Override
            public boolean preheat$dimensionOn(String id) {
                throw new IllegalStateException("settings broke");
            }
        };
        broken.irisPrepare("minecraft:overworld");
        broken.map.put("minecraft:the_nether", new Pipe("minecraft:the_nether"));
        KeptPipelines<String, Pipe> k = new KeptPipelines<>(broken);
        k.beforePrepare("minecraft:overworld");
        assertFalse(Features.available(Feature.KEEP_PIPELINES));
        broken.calls.clear();
        k.beforePrepare("minecraft:overworld");
        assertTrue(broken.calls.isEmpty(), "with keeping off it does nothing at all");
    }

    @Test
    void aPreparedPipelineThatCannotBeSetAsideIsLetGo() {
        FakeHost broken = new FakeHost() {
            @Override
            public void preheat$deactivate(Pipe pipeline) {
                if (pipeline.dim.equals("minecraft:the_nether")) {
                    throw new IllegalStateException("deactivate failed");
                }
            }
        };
        Pipe overworld = broken.irisPrepare("minecraft:overworld");
        new KeptPipelines<>(broken).prebuild(List.of("minecraft:the_nether"), overworld);
        assertFalse(broken.map.containsKey("minecraft:the_nether"));
        assertTrue(broken.calls.contains("destroy minecraft:the_nether"));
        assertFalse(Features.available(Feature.KEEP_PIPELINES));
    }

    @Test
    void aChangeWithNothingActiveOrAnActivePipelineIrisDoesNotListPutsNothingAside() {
        assertTrue(kept.onDimensionChange(), "joining: no pipeline yet");
        assertTrue(host.calls.isEmpty());
        host.active = new Pipe("minecraft:overworld");
        host.off.add("minecraft:overworld");
        assertTrue(kept.onDimensionChange());
        assertEquals(List.of("deactivate minecraft:overworld"), host.calls, "not Iris's to destroy by id");
    }

    @Test
    void theEveryFramePrepareOfTheDimensionInUseChangesNothing() {
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        host.off.add("minecraft:overworld");
        kept.beforePrepare("minecraft:the_nether");
        assertTrue(host.map.containsKey("minecraft:overworld"), "the active one is never dropped here");
        kept.beforePrepare("minecraft:overworld");
        host.irisPrepare("minecraft:overworld");
        assertTrue(kept.afterPrepare("minecraft:overworld"));
        assertTrue(host.calls.isEmpty(), host.calls.toString());
        assertSame(overworld, host.active);
    }

    @Test
    void aKeptPipelineIrisDidNotMakeActiveIsNotBroughtBack() {
        host.irisPrepare("minecraft:overworld");
        travel("minecraft:the_nether");
        host.calls.clear();
        kept.onDimensionChange();
        kept.beforePrepare("minecraft:overworld");
        assertTrue(kept.afterPrepare("minecraft:overworld"));
        assertFalse(host.calls.stream().anyMatch(c -> c.startsWith("reactivate")), host.calls.toString());
    }

    @Test
    void keepingTurnedOffDuringAChangeSkipsTheBudgetAndDiagnosticsOffSkipsTheTimings() {
        host.memory = new KeptBudget.Memory(20_000, 32_000);
        host.irisPrepare("minecraft:overworld");
        try (LogLines log = LogLines.capture()) {
            travel("minecraft:the_nether");
            assertTrue(
                    log.has("INFO PREHEAT Pipeline for minecraft:the_nether ready in"),
                    log.lines().toString());
            assertTrue(
                    log.has("INFO PREHEAT Video memory: 20000 MB free of 32000 MB"),
                    log.lines().toString());
        }
        Features.fail(Feature.DIAGNOSTICS, new IllegalStateException("test"));
        try (LogLines log = LogLines.capture()) {
            travel("minecraft:overworld");
            assertFalse(log.has("ready in"), log.lines().toString());
            assertFalse(log.has("Video memory"), log.lines().toString());
        }
        kept.onDimensionChange();
        kept.beforePrepare("minecraft:the_end");
        Features.fail(Feature.KEEP_PIPELINES, new IllegalStateException("test"));
        host.irisPrepare("minecraft:the_end");
        assertTrue(kept.afterPrepare("minecraft:the_end"));
        assertFalse(kept.sizes().containsKey("minecraft:the_end"), "nothing measured with keeping off");
    }

    @Test
    void aSizeIsMeasuredOnlyWhenTheCardReportedFreeMemoryBeforeAndAfter() {
        host.memory = new KeptBudget.Memory(20_000, 32_000);
        Pipe overworld = host.irisPrepare("minecraft:overworld");
        kept.onDimensionChange();
        kept.beforePrepare("minecraft:the_nether");
        host.memory = null;
        host.irisPrepare("minecraft:the_nether");
        kept.afterPrepare("minecraft:the_nether");
        assertFalse(kept.sizes().containsKey("minecraft:the_nether"), "no reading after the build");
        host.memory = new KeptBudget.Memory(20_000, 32_000);
        host.buildCostMb = 1_000;
        FakeHost forgetful = new FakeHost() {
            int reads;

            @Override
            public KeptBudget.@Nullable Memory preheat$memory() {
                return reads++ == 0 ? new KeptBudget.Memory(20_000, 32_000) : null;
            }
        };
        Pipe active = forgetful.irisPrepare("minecraft:overworld");
        KeptPipelines<String, Pipe> k = new KeptPipelines<>(forgetful);
        k.prebuild(List.of("minecraft:the_end"), active);
        assertTrue(forgetful.map.containsKey("minecraft:the_end"));
        assertFalse(k.sizes().containsKey("minecraft:the_end"), "no reading after the prepare");
        kept.prebuild(List.of("minecraft:the_end"), overworld);
        assertEquals(1_000L, kept.sizes().get("minecraft:the_end"));
    }

    @Test
    void anEntryWithNoPipelineIsForgottenWithoutDestroyingAnything() {
        host.irisPrepare("minecraft:overworld");
        host.map.put("x:empty", null);
        kept.dropInactive("test");
        assertFalse(host.map.containsKey("x:empty"));
        assertFalse(host.calls.stream().anyMatch(c -> c.startsWith("destroy")), host.calls.toString());
    }
}
