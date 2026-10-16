package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The mixin config, the feature table and the mixin package agree with each other. */
class FeatureTest {
    @Test
    void theMixinConfigListsExactlyTheFeaturesMixins() throws Exception {
        JsonObject config;
        try (var in =
                new InputStreamReader(getClass().getResourceAsStream("/preheat.mixins.json"), StandardCharsets.UTF_8)) {
            config = JsonParser.parseReader(in).getAsJsonObject();
        }
        Set<String> listed = new TreeSet<>();
        config.getAsJsonArray("client").forEach(e -> listed.add(e.getAsString()));
        Set<String> owned = new TreeSet<>();
        for (Feature f : Feature.values()) {
            for (String m : f.mixins) {
                assertTrue(owned.add(m), m + " belongs to two features");
            }
        }
        assertEquals(owned, listed);
        assertEquals(
                "com.lewisgibson.preheat.PreheatMixinPlugin",
                config.get("plugin").getAsString());
    }

    @Test
    void everyClassInTheMixinPackageBelongsToAFeature() throws Exception {
        Path dir = Path.of("src/main/java/com/lewisgibson/preheat/mixin");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(f -> f.toString().endsWith(".java")
                            && !f.getFileName().toString().equals("package-info.java"))
                    .forEach(f -> found.add(
                            dir.relativize(f).toString().replace('/', '.').replace(".java", "")));
        }
        Set<String> owned = new HashSet<>();
        for (Feature f : Feature.values()) {
            owned.addAll(f.mixins);
        }
        assertEquals(found, new TreeSet<>(owned));
    }

    @Test
    void requirementsNameFeaturesAndOfMixinFindsTheOwner() {
        for (Feature f : Feature.values()) {
            for (String r : f.requires) {
                assertNotNull(Feature.valueOf(r));
            }
        }
        assertEquals(
                Feature.PROGRAM_CACHE, Feature.ofMixin("com.lewisgibson.preheat.mixin.cache.ProgramBuilderCacheMixin"));
        assertEquals(null, Feature.ofMixin("com.lewisgibson.preheat.mixin.cache.Nothing"));
    }
}
