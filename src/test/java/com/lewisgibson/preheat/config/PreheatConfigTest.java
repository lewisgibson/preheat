package com.lewisgibson.preheat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreheatConfigTest {
    @TempDir
    Path tmp;

    @Test
    void missingOrBrokenFilesMeanEverythingOn() throws Exception {
        PreheatConfig d = PreheatConfig.load(tmp.resolve("none.json"));
        assertTrue(d.enabled);
        assertEquals(
                KnownDimensions.VANILLA,
                List.copyOf(d.dimensions.keySet().stream()
                        .sorted(KnownDimensions::order)
                        .toList()));
        assertTrue(d.dimensions.values().stream().allMatch(Boolean::booleanValue));
        assertEquals("", d.cacheFolder);
        Files.writeString(tmp.resolve("broken.json"), "{ not json");
        assertTrue(PreheatConfig.load(tmp.resolve("broken.json")).enabled);
    }

    @Test
    void roundTripsAndIgnoresUnknownFields() throws Exception {
        PreheatConfig c = new PreheatConfig().normalize();
        c.dimensions.put(KnownDimensions.END, false);
        c.cacheFolder = "  /caches/preheat  ";
        Path f = tmp.resolve("sub").resolve("preheat.json");
        PreheatConfig.write(f, c);
        PreheatConfig back = PreheatConfig.load(f);
        assertFalse(back.dimensions.get(KnownDimensions.END));
        assertEquals("/caches/preheat", back.cacheFolder);
        assertFalse(Files.exists(f.resolveSibling("preheat.json.tmp")));
        Files.writeString(
                tmp.resolve("old.json"),
                "{\"programCache\": false, \"enabled\": false, \"dimensions\": {\"x:y\": null}}");
        PreheatConfig old = PreheatConfig.load(tmp.resolve("old.json"));
        assertFalse(old.enabled);
        assertFalse(old.dimensions.containsKey("x:y"));
        assertTrue(old.dimensions.containsKey(KnownDimensions.NETHER));
    }

    @Test
    void eachDimensionHasItsOwnSwitchAndNewOnesFollowTheNewDimensionsSwitch() {
        PreheatConfig c = new PreheatConfig().normalize();
        assertTrue(c.moddedDimensions, "new dimensions on by default");
        assertTrue(c.dimensionOn("mod:never_seen"));
        c.dimensions.put(KnownDimensions.NETHER, false);
        assertFalse(c.dimensionOn(KnownDimensions.NETHER), "no switch above it overrides a dimension's own");
        assertTrue(c.dimensionOn(KnownDimensions.END));
        c.moddedDimensions = false;
        assertFalse(c.dimensionOn("mod:never_seen"));
        c.enabled = false;
        assertFalse(c.dimensionOn(KnownDimensions.END), "the file-only switch still turns everything off");
    }

    @Test
    void preheatIsOffOnlyWhenNothingIsTicked() {
        PreheatConfig c = new PreheatConfig().normalize();
        assertTrue(c.active());
        KnownDimensions.VANILLA.forEach(id -> c.dimensions.put(id, false));
        assertTrue(c.active(), "new dimensions still on");
        c.moddedDimensions = false;
        assertFalse(c.active());
        c.dimensions.put(KnownDimensions.END, true);
        assertTrue(c.active());
        c.enabled = false;
        assertFalse(c.active());
    }

    @Test
    void moddedDimensionsAreDiscoveredOnlyAsTheNewDimensionsSwitchSays() {
        PreheatConfig c = new PreheatConfig().normalize();
        c.moddedDimensions = false;
        assertTrue(c.discover(List.of("mod:off")));
        assertFalse(c.dimensions.get("mod:off"));
    }

    @Test
    void displayNamesAndOrder() {
        assertEquals("Overworld", KnownDimensions.display(KnownDimensions.OVERWORLD));
        assertEquals("Nether", KnownDimensions.display(KnownDimensions.NETHER));
        assertEquals("End", KnownDimensions.display(KnownDimensions.END));
        assertEquals("Twilight Forest", KnownDimensions.display("twilightforest:twilight_forest"));
        assertEquals("Deep Dark", KnownDimensions.display("mod:levels/deep-dark"));
        assertEquals("Unknown", KnownDimensions.display(""));
        assertEquals("Nether", KnownDimensions.display("the_nether"), "an id without a namespace");
        assertEquals("Unknown", KnownDimensions.display(null));
        assertEquals("plain", KnownDimensions.titleCase("plain").toLowerCase(java.util.Locale.ROOT));
        assertEquals("___", KnownDimensions.titleCase("___"));
        List<String> ids = new java.util.ArrayList<>(
                List.of("b:x", KnownDimensions.END, "a:y", KnownDimensions.OVERWORLD, KnownDimensions.NETHER));
        ids.sort(KnownDimensions::order);
        assertEquals(
                List.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END, "a:y", "b:x"), ids);
    }
}
