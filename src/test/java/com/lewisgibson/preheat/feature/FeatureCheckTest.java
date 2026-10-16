package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The startup check on the real classes with one piece taken away. */
class FeatureCheckTest {
    private static final ShapeCheck.ClassSource ALL =
            OwnClasses.over(name -> null, FeatureCheckTest.class.getClassLoader());

    private static ShapeCheck.ClassSource without(String internalName) {
        return name -> name.equals(internalName) ? null : ALL.get(name);
    }

    @Test
    void aMixinMissingFromTheJarTurnsItsFeatureAndThoseThatNeedItOff() {
        Map<Feature, FeatureCheck.Result> r =
                FeatureCheck.evaluate(without(FeatureCheck.MIXINS + "startup/IrisLoadingMixin"), true);
        assertEquals(
                new FeatureCheck.Result(
                        FeatureCheck.State.OFF, List.of("mixin startup.IrisLoadingMixin is missing from the jar")),
                r.get(Feature.STARTUP));
        assertEquals(
                List.of("needs hooking the end of the game's loading, which is off"),
                r.get(Feature.PREBUILD).problems());
        assertTrue(r.get(Feature.KEEP_PIPELINES).on());
    }

    @Test
    void aHelperMissingFromTheJarTurnsItsFeatureOff() {
        Map<Feature, FeatureCheck.Result> r =
                FeatureCheck.evaluate(without(FeatureCheck.ROOT + "startup/Startup"), true);
        assertEquals(
                List.of("class startup.Startup is missing from the jar"),
                r.get(Feature.STARTUP).problems());
    }

    @Test
    void anIrisThatPatchesDifferentlyTurnsOnlyThePatchCacheOff() {
        Map<Feature, FeatureCheck.Result> r = FeatureCheck.evaluate(without(PatchFingerprint.KEY), true);
        assertEquals(
                List.of("this Iris no longer patches shaders the way Preheat was checked against"
                        + " (TransformPatcher changed)"),
                r.get(Feature.PATCH_CACHE).problems());
        assertTrue(r.get(Feature.PROGRAM_CACHE).on());
    }

    @Test
    void theForeignClassesAreTheGamesAndItsModsOnly() {
        for (String name : List.of(
                "net/irisshaders/iris/Iris",
                "net/minecraft/client/Minecraft",
                "com/mojang/blaze3d/systems/RenderSystem",
                "net/caffeinemc/mods/sodium/api/Thing",
                "com/seibel/distanthorizons/api/Thing",
                "org/lwjgl/opengl/GL11C")) {
            assertTrue(FeatureCheck.FOREIGN.test(name), name);
        }
        assertFalse(FeatureCheck.FOREIGN.test("java/lang/String"));
        assertFalse(FeatureCheck.FOREIGN.test("com/lewisgibson/preheat/Preheat"));
    }
}
