package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PrebuildTest {
    private static List<String> keys(List<String> enabled, Set<String> pack) {
        return Prebuild.keys(enabled, pack, Function.identity(), "iris:end", KnownDimensions.OVERWORLD);
    }

    @Test
    void everyEnabledDimensionWithItsOwnPipelineIsPreparedButNotTheOverworld() {
        assertEquals(
                List.of(KnownDimensions.NETHER, KnownDimensions.END),
                keys(
                        KnownDimensions.VANILLA,
                        Set.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END)));
    }

    @Test
    void unmappedDimensionsUseIrisFallbacks() {
        assertEquals(List.of(KnownDimensions.NETHER, "iris:end"), keys(KnownDimensions.VANILLA, Set.of()));
        assertEquals(
                List.of("mod:sky"),
                keys(List.of("mod:sky", "mod:unmapped"), Set.of("mod:sky")),
                "a modded dimension the pack names is built; one it does not shares another's pipeline");
        assertEquals(
                List.of("iris:end"),
                keys(List.of(KnownDimensions.END, KnownDimensions.END), Set.of("mod:other")),
                "no duplicates");
    }

    @Test
    void onlyTheStartupListIsPreparedAtStartupVanillaFirst() {
        PreheatConfig c = new PreheatConfig().normalize();
        c.prepareAtStartup.add("aether:*");
        c.dimensions.put("aaa:first", true);
        c.dimensions.put("aether:the_aether", true);
        c.dimensions.put(KnownDimensions.NETHER, false);
        assertEquals(
                List.of(KnownDimensions.OVERWORLD, KnownDimensions.END, "aether:the_aether"),
                Prebuild.enabledIds(c),
                "aaa:first waits for its first visit; the Nether is switched off");
    }

    @Test
    void aPackWithEightyDimensionsPreparesOnlyTheListedOnes() {
        PreheatConfig c = new PreheatConfig().normalize();
        c.prepareAtStartup.add("twilightforest:twilight_forest");
        c.prepareAtStartup.add("undergarden:*");
        c.neverKeep.add("rftoolsdim:*");
        for (int i = 0; i < 75; i++) {
            c.dimensions.put("backrooms:level" + i, true);
        }
        c.dimensions.put("twilightforest:twilight_forest", true);
        c.dimensions.put("rftoolsdim:mydim", true);
        c.dimensions.put("undergarden:undergarden", true);
        assertEquals(80 + 1, c.dimensions.size(), "75 + 3 vanilla + 3 modded");
        assertEquals(
                List.of(
                        KnownDimensions.OVERWORLD,
                        KnownDimensions.NETHER,
                        KnownDimensions.END,
                        "twilightforest:twilight_forest",
                        "undergarden:undergarden"),
                Prebuild.enabledIds(c));
    }

    @Test
    void theStartupListComesFromTheSettingsVanillaIncluded() {
        PreheatConfig c = new PreheatConfig().normalize();
        c.prepareAtStartup = new java.util.ArrayList<>(List.of(KnownDimensions.OVERWORLD, "backrooms:level0"));
        c.dimensions.put("backrooms:level0", true);
        assertEquals(List.of(KnownDimensions.OVERWORLD, "backrooms:level0"), Prebuild.enabledIds(c));
    }
}
