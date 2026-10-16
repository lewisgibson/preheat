package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Saved patches around a fake Iris patcher, beside a shader cache on a fake driver. */
class SavedPatchesTest {
    /** Iris's stage names, as far as the saved patches see them. */
    enum Stage {
        VERTEX,
        FRAGMENT
    }

    @TempDir
    Path tmp;

    final FakeDriver gl = new FakeDriver();
    BinaryCache cache;
    boolean renderThread = true;

    private SavedPatches<Stage> patches(boolean verify) {
        return new SavedPatches<>(Stage.class, cache, () -> renderThread, () -> true, () -> false, verify);
    }

    private static Map<String, @Nullable String> stages() {
        Map<String, @Nullable String> m = new LinkedHashMap<>();
        m.put("VERTEX", "void main() {}");
        m.put("GEOMETRY", null);
        m.put("FRAGMENT", "out vec4 c; void main() {}");
        return m;
    }

    private static Map<Stage, String> patched(String fragment) {
        Map<Stage, String> m = new EnumMap<>(Stage.class);
        m.put(Stage.VERTEX, "#version 330\nvoid main() {}");
        m.put(Stage.FRAGMENT, fragment);
        return m;
    }

    private Path generation() {
        Path g = cache.generationDir();
        assertNotNull(g);
        return g;
    }

    private List<Path> saved() throws IOException {
        if (!Files.isDirectory(generation())) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(generation())) {
            return s.filter(p -> p.toString().endsWith(PatchedSources.SUFFIX)).toList();
        }
    }

    @BeforeEach
    void setUp() {
        Path game = tmp.resolve("game");
        Platforms.use(FakePlatform.client(game, "iris", "1.11.7", "sodium", "0.9.2"));
        ConfigFiles.use(game.resolve("config").resolve("preheat.json"));
        FeatureStates.allOnExcept();
        CachePrefetch.releaseAll();
        cache = new BinaryCache(
                gl,
                gl,
                Runnable::run,
                System::nanoTime,
                new BuildTimer(System::nanoTime, () -> {}),
                1L << 30,
                null,
                CacheDir::lockChannel);
    }

    @AfterEach
    void tearDown() {
        Platforms.use(null);
        ConfigFiles.use(null);
        FeatureStates.none();
    }

    @Test
    void aPatchIsSavedTheFirstTimeAndUsedTheNext() throws Exception {
        SavedPatches<Stage> p = patches(false);
        cache.beginBuild();
        assertNull(p.before("gbuffers_terrain", stages(), "params"), "nothing saved yet: Iris patches");
        p.after(patched("fragment"));
        assertEquals(1, saved().size());
        Files.setLastModifiedTime(saved().get(0), java.nio.file.attribute.FileTime.fromMillis(0));

        assertEquals(patched("fragment"), p.before("gbuffers_terrain", stages(), "params"));
        p.after(patched("fragment"));
        assertTrue(Files.getLastModifiedTime(saved().get(0)).toMillis() > 0, "marked as used");
        assertEquals("1 of 2 patches from the cache; nothing cached yet", cache.endBuild());
        assertNull(p.before("gbuffers_terrain", stages(), "other params"), "other parameters are another key");
        assertNull(p.before("gbuffers_water", stages(), "params"), "another program is another key");
    }

    @Test
    void aPatchReadAheadIsUsedWithoutOpeningItsFile() throws Exception {
        SavedPatches<Stage> p = patches(false);
        p.before("composite", stages(), "params");
        p.after(patched("fragment"));
        Path file = saved().get(0);
        CachePrefetch.start(() -> generation().getParent(), s -> {}, r -> {}).join();
        Files.delete(file);
        assertEquals(patched("fragment"), p.before("composite", stages(), "params"));
        CachePrefetch.releaseAll();
    }

    @Test
    void nothingIsSavedOrUsedWhereTheCacheCannotRun() throws Exception {
        SavedPatches<Stage> p = patches(false);
        renderThread = false;
        assertNull(p.before("x", stages(), "params"));
        p.after(patched("f"));
        renderThread = true;
        FeatureStates.allOnExcept(Feature.PATCH_CACHE);
        assertNull(p.before("x", stages(), "params"));
        p.after(patched("f"));
        FeatureStates.allOnExcept();
        assertNull(p.before("x", stages(), new Object()), "parameters that cannot be written down");
        p.after(patched("f"));
        assertTrue(saved().isEmpty());
        assertNull(p.before("x", stages(), "params"));
        p.after(null);
        assertTrue(saved().isEmpty(), "Iris made nothing, so there is nothing to save");

        gl.formats = new int[0];
        BinaryCache off = new BinaryCache(
                gl,
                gl,
                Runnable::run,
                System::nanoTime,
                new BuildTimer(System::nanoTime, () -> {}),
                1L << 30,
                null,
                CacheDir::lockChannel);
        SavedPatches<Stage> q = new SavedPatches<>(Stage.class, off, () -> true, () -> true, () -> false, false);
        assertNull(q.before("x", stages(), "params"), "the shader cache is off");
    }

    @Test
    void aSavedPatchWithAStageThisIrisDoesNotKnowIsPatchedAgain() throws Exception {
        SavedPatches<Stage> p = patches(false);
        p.before("x", stages(), "params");
        p.after(patched("fragment"));
        Path file = saved().get(0);
        String key = file.getFileName().toString().replace(PatchedSources.SUFFIX, "");
        PatchedSources.store(generation(), key, PatchedSources.encode(Map.of("TESS_EVAL", "x")));
        assertNull(p.before("x", stages(), "params"));
        p.after(patched("again"));
        assertEquals(
                Map.of("VERTEX", "#version 330\nvoid main() {}", "FRAGMENT", "again"),
                PatchedSources.load(generation(), key));
    }

    @Test
    void checkingComparesEverySavedPatchWithWhatIrisMakes() throws Exception {
        SavedPatches<Stage> p = patches(false);
        p.before("x", stages(), "params");
        p.after(patched("fragment"));
        SavedPatches<Stage> check = patches(true);
        try (LogLines log = LogLines.capture()) {
            for (int i = 0; i < 99; i++) {
                assertNull(check.before("x", stages(), "params"), "Iris patches anyway");
                check.after(patched("fragment"));
            }
            assertFalse(log.has("Patch cache check"), "quiet while they match");
            assertNull(check.before("x", stages(), "params"));
            check.after(patched("fragment"));
            assertTrue(log.has("INFO PREHEAT Patch cache check: 100 saved patches match what Iris made, 0 differ"));
            assertNull(check.before("x", stages(), "params"));
            check.after(patched("something else"));
            assertTrue(
                    log.has("ERROR PREHEAT Patch cache check: a saved patch differs from what Iris made (1 of 101)"));
            assertNull(check.before("x", stages(), "params"));
            check.after(null);
            assertNull(check.before("y", stages(), "params"), "nothing saved: patched and saved as usual");
            check.after(patched("y"));
        }
        assertEquals(2, saved().size());
    }

    @Test
    void anErrorTurnsSavedPatchesOffAndIrisPatchesAsAlways() {
        SavedPatches<Stage> broken = new SavedPatches<>(
                Stage.class,
                cache,
                () -> true,
                () -> {
                    throw new IllegalStateException("no render device yet");
                },
                () -> false,
                false);
        assertNull(broken.before("x", stages(), "params"));
        assertFalse(Features.available(Feature.PATCH_CACHE));

        FeatureStates.allOnExcept();
        SavedPatches<Stage> p = patches(false);
        p.before("x", stages(), "params");
        Map<Stage, String> odd = new HashMap<>();
        odd.put(null, "a stage with no name");
        p.after(odd);
        assertFalse(Features.available(Feature.PATCH_CACHE), "an error while saving");
    }
}
