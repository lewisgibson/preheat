package com.lewisgibson.preheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.cache.CacheDir;
import com.lewisgibson.preheat.cache.CachePrefetch;
import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureCheck;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.feature.OwnClasses;
import com.lewisgibson.preheat.feature.ShapeCheck;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** What Mixin is told to apply, on the real class files and a stand-in mod loader. */
class MixinGateTest {
    private static final ShapeCheck.ClassSource CLASSES =
            OwnClasses.over(name -> null, MixinGateTest.class.getClassLoader());
    private static final String MIXINS = "com.lewisgibson.preheat.mixin.";

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        ConfigFiles.use(tmp.resolve("config").resolve("preheat.json"));
    }

    @AfterEach
    void tearDown() {
        Platforms.use(null);
        ConfigFiles.use(null);
        FeatureStates.none();
        CachePrefetch.releaseAll();
    }

    private static MixinGate gate(ShapeCheck.ClassSource classes) {
        return new MixinGate(() -> classes, System::nanoTime);
    }

    private void client(String... mods) {
        Platforms.use(FakePlatform.client(tmp, mods));
    }

    @Test
    void aDedicatedServerAppliesNothingAndSaysNothing() {
        Platforms.use(FakePlatform.client(tmp, "iris", "1.11.7").server());
        try (LogLines log = LogLines.capture()) {
            Map<Feature, FeatureCheck.Result> r = gate(CLASSES).evaluate();
            assertEquals(FeatureCheck.State.NOT_NEEDED, r.get(Feature.STARTUP).state());
            assertTrue(log.lines().isEmpty(), log.lines().toString());
        }
        assertFalse(Features.available(Feature.STARTUP));
    }

    @Test
    void withoutIrisEverythingIsOffAndTheLogSaysWhy() {
        client("minecraft", "26.3");
        try (LogLines log = LogLines.capture()) {
            MixinGate g = gate(CLASSES);
            assertEquals(
                    FeatureCheck.State.OFF,
                    g.evaluate().get(Feature.KEEP_PIPELINES).state());
            assertTrue(
                    log.has("is doing nothing: Iris is not installed"),
                    log.lines().toString());
            assertFalse(g.shouldApply(MIXINS + "keep.PipelineManagerMixin"));
        }
    }

    @Test
    void theInstalledClassesDecideAndTheResultIsLoggedOnce() throws Exception {
        client("iris", "1.11.7", "minecraft", "26.3", "fake", "0.19.5");
        Path cache = tmp.resolve("preheat").resolve("shader-cache");
        CacheDir.adopt(cache);
        byte[] iface = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];
        new ProgramBinaryStore(cache.resolve("0123456789abcdef"))
                .store("k", new ProgramBinaryStore.Entry(0x8E21, new byte[] {1}, iface));
        MixinGate g = gate(CLASSES);
        try (LogLines log = LogLines.capture()) {
            Map<Feature, FeatureCheck.Result> r = g.evaluate();
            assertSame(r, g.evaluate(), "worked out once");
            assertTrue(r.get(Feature.KEEP_PIPELINES).on());
            assertEquals(
                    FeatureCheck.State.NOT_NEEDED,
                    r.get(Feature.BACKGROUND_DH_CLOSE).state());
            assertTrue(
                    log.has("on Fake 0.19.5, Minecraft 26.3, Iris 1.11.7, Sodium absent, Distant Horizons absent;"),
                    log.lines().toString());
            assertTrue(
                    log.has("features: keep_pipelines, build_tracking"),
                    log.lines().toString());
            long until = System.nanoTime() + 10_000_000_000L;
            while (!log.has("Shader cache: read 1 saved shaders") && System.nanoTime() < until) {
                Thread.onSpinWait();
            }
            assertTrue(
                    log.has("INFO PREHEAT Shader cache: read 1 saved shaders (0.0 MB) ahead in"),
                    log.lines().toString());
        }
    }

    @Test
    void aFeatureThatDoesNotFitIsTurnedOffWithItsReasonsAndItsMixinsAreNotApplied() {
        client("iris", "1.11.7");
        ShapeCheck.ClassSource without = name ->
                name.equals("com/lewisgibson/preheat/mixin/startup/IrisLoadingMixin") ? null : CLASSES.get(name);
        MixinGate g = gate(without);
        try (LogLines log = LogLines.capture()) {
            g.evaluate();
            assertTrue(
                    log.has("WARN PREHEAT Turned off hooking the end of the game's loading: mixin"
                            + " startup.IrisLoadingMixin is missing from the jar"),
                    log.lines().toString());
        }
        assertFalse(g.shouldApply(MIXINS + "startup.IrisLoadingMixin"));
        assertTrue(g.shouldApply(MIXINS + "keep.PipelineManagerMixin"));
        assertFalse(g.shouldApply("com.example.mixin.SomeoneElsesMixin"), "not one of Preheat's");
    }

    @Test
    void distantHorizonsMixinsApplyOnlyWithItInstalled() {
        client("iris", "1.11.7");
        assertFalse(gate(CLASSES).shouldApply(MIXINS + "dh.DHCompatInternalAccessor"));
        client("iris", "1.11.7", "distanthorizons", "3.3.4-26.3");
        assertTrue(gate(CLASSES).shouldApply(MIXINS + "dh.DHCompatInternalAccessor"));
    }

    @Test
    void aMixinWhoseInjectorsFoundNothingToHookTurnsItsFeatureOff() {
        client("iris", "1.11.7");
        MixinGate g = gate(CLASSES);
        g.evaluate();
        ClassNode target = new ClassNode();
        target.name = "net/irisshaders/iris/Iris";
        MethodNode m = new MethodNode(Opcodes.ACC_STATIC, "onLoadingComplete", "()V", null, null);
        for (String handler : new String[] {"preheat$beforeFirstBuild", "preheat$afterFirstBuild"}) {
            m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, target.name, handler, "()V", false));
        }
        target.methods.add(m);
        g.applied("net.irisshaders.iris.Iris", target, MIXINS + "startup.IrisLoadingMixin");
        assertTrue(Features.available(Feature.STARTUP), "every injector was placed");
        g.applied("x.Y", new ClassNode(), "com.example.mixin.NotOurs");
        gate(name -> null).applied("x.Y", new ClassNode(), MIXINS + "startup.IrisLoadingMixin");
        assertTrue(Features.available(Feature.STARTUP));
        ClassNode changed = new ClassNode();
        changed.name = target.name;
        g.applied("net.irisshaders.iris.Iris", changed, MIXINS + "startup.IrisLoadingMixin");
        assertFalse(Features.available(Feature.STARTUP), "another mod changed what it hooks");
    }

    @Test
    void withPreheatSwitchedOffNothingIsReadAhead() throws Exception {
        client("iris", "1.11.7");
        com.lewisgibson.preheat.config.PreheatConfig.change(c -> c.enabled = false);
        try (LogLines log = LogLines.capture()) {
            gate(CLASSES).evaluate();
            Thread.sleep(200);
            assertFalse(log.has("ahead in"), log.lines().toString());
        }
        assertFalse(Features.on(Feature.PROGRAM_CACHE));
    }
}
