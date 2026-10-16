package com.lewisgibson.preheat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DimensionPolicyTest {
    @Test
    void patternsMatchExactIdsAndStars() {
        assertTrue(DimensionPolicy.matches("aether:the_aether", "aether:the_aether"));
        assertFalse(DimensionPolicy.matches("aether:the_aether", "aether:the_aether_2"));
        assertTrue(DimensionPolicy.matches("rftoolsdim:*", "rftoolsdim:my_dim"));
        assertFalse(DimensionPolicy.matches("rftoolsdim:*", "rftools:my_dim"));
        assertTrue(DimensionPolicy.matches("backrooms:level*", "backrooms:level0"));
        assertFalse(DimensionPolicy.matches("backrooms:level*", "backrooms:attic"));
        assertTrue(DimensionPolicy.matches("*:mining", "allthemodium:mining"));
        assertTrue(DimensionPolicy.matches("*", "anything:at_all"));
        assertTrue(DimensionPolicy.matches(" AETHER:The_Aether ", "aether:the_aether"), "trimmed, any case");
        assertTrue(DimensionPolicy.matches("a.b:c", "a.b:c"));
        assertFalse(DimensionPolicy.matches("a.b:c", "axb:c"), "a dot is a dot, not any character");
    }

    @Test
    void theDefaultsStartWithTheVanillaThreeAndTreatEveryEntryTheSame() {
        PreheatConfig c = new PreheatConfig().normalize();
        assertEquals(KnownDimensions.VANILLA, c.prepareAtStartup.subList(0, 3));
        assertTrue(DimensionPolicy.preparesAtStartup(c, KnownDimensions.NETHER));
        assertFalse(DimensionPolicy.preparesAtStartup(c, "mod:other"));
        c.prepareAtStartup = new ArrayList<>(List.of("mod:*"));
        assertFalse(
                DimensionPolicy.preparesAtStartup(c, KnownDimensions.NETHER), "the vanilla three are only defaults");
        assertTrue(DimensionPolicy.preparesAtStartup(c, "mod:other"));
    }

    @Test
    void neverKeepWinsAndSwitchesTheDimensionOff() {
        PreheatConfig c = new PreheatConfig().normalize();
        assertFalse(DimensionPolicy.neverKeeps(c, "rftoolsdim:abc"), "nothing is never-kept by default");
        c.neverKeep.add("rftoolsdim:*");
        assertTrue(DimensionPolicy.neverKeeps(c, "rftoolsdim:abc"));
        assertFalse(c.dimensionOn("rftoolsdim:abc"));
        c.neverKeep.add("aether:*");
        c.prepareAtStartup.add("aether:the_aether");
        assertFalse(DimensionPolicy.preparesAtStartup(c, "aether:the_aether"));
        c.dimensions.put("aether:the_aether", true);
        assertFalse(c.dimensionOn("aether:the_aether"), "a tick never overrides never-keep");
    }

    @Test
    void onlyStartupDimensionsAndTheOneEnteredAreListedAndOnlyWithShadersOfTheirOwn() {
        PreheatConfig c = new PreheatConfig().normalize();
        java.util.Set<String> packNamed = java.util.Set.of("aether:the_aether", "backrooms:level7");
        c.prepareAtStartup.add("aether:*");
        c.neverKeep.add("rftoolsdim:*");
        List<String> server = new ArrayList<>(KnownDimensions.VANILLA);
        for (int i = 0; i < 75; i++) {
            server.add("backrooms:level" + i);
        }
        server.add("aether:the_aether");
        server.add("rftoolsdim:abc");
        assertEquals(
                List.of(
                        KnownDimensions.OVERWORLD,
                        KnownDimensions.NETHER,
                        KnownDimensions.END,
                        "aether:the_aether",
                        "backrooms:level7"),
                DimensionPolicy.toList(c, server, "backrooms:level7", packNamed));
        assertEquals(
                List.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END, "aether:the_aether"),
                DimensionPolicy.toList(
                        c, server, "rftoolsdim:abc", java.util.Set.of("aether:the_aether", "rftoolsdim:abc")),
                "a never-kept dimension is not listed even when entered");
        assertEquals(KnownDimensions.VANILLA, DimensionPolicy.toList(c, KnownDimensions.VANILLA, "", packNamed));
        assertEquals(
                List.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END, "aether:the_aether"),
                DimensionPolicy.toList(c, server, "backrooms:level8", packNamed),
                "a dimension the pack does not name uses the Overworld's or the End's shaders, and their tick");
    }

    @Test
    void anOlderFileGetsTheDefaultListsAndNullEntriesAreDropped() {
        PreheatConfig c = new PreheatConfig();
        c.prepareAtStartup = null;
        c.neverKeep = new ArrayList<>(java.util.Arrays.asList("x:*", null));
        c.normalize();
        assertEquals(DimensionPolicy.PREPARE_AT_STARTUP, c.prepareAtStartup);
        assertEquals(List.of("x:*"), c.neverKeep);
        assertFalse(DimensionPolicy.any(java.util.Arrays.asList(null, " "), "x:y"));
    }

    @Test
    void theDimensionEnteredIsListedOnce() {
        PreheatConfig c = new PreheatConfig().normalize();
        assertEquals(
                KnownDimensions.VANILLA,
                DimensionPolicy.toList(c, KnownDimensions.VANILLA, KnownDimensions.NETHER, java.util.Set.of()));
    }
}
