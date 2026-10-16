package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** How Iris's compile and patch calls become cache keys and stage maps. */
class CacheKeysTest {
    /** Iris's stage names, as far as the saved patches see them. */
    enum Stage {
        VERTEX,
        COMPUTE
    }

    @TempDir
    Path tmp;

    final FakeDriver gl = new FakeDriver();
    BinaryCache cache;

    @BeforeEach
    void setUp() {
        Path game = tmp.resolve("game");
        Platforms.use(FakePlatform.client(game, "iris", "1.11.7"));
        ConfigFiles.use(game.resolve("config").resolve("preheat.json"));
        FeatureStates.allOnExcept();
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
    void aProgramsKeyIsItsKindItsFixedPartAndItsStagesAndThePartsAreKeptForTheMissLog() {
        List<String> parts = new ArrayList<>();
        String key = cache.keyFor("builder", () -> "fixed locations", List.of("vertex", "void main() {}"), parts);
        assertEquals(List.of("fixed locations", "vertex", "void main() {}"), parts);
        assertEquals(cache.key("builder", List.of("fixed locations", "vertex", "void main() {}")), key);
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
        List<String> none = new ArrayList<>();
        assertNull(off.keyFor(
                "builder",
                () -> {
                    throw new AssertionError("not worked out with the cache off");
                },
                List.of(),
                none));
        assertTrue(none.isEmpty());
    }

    @Test
    void aGbufferProgramsAttributesAreItsVertexFormatAndWhetherItIsAFallback() {
        assertEquals(
                "attributes Position,Color,UV0, fallback true",
                BinaryCache.attributes(List.of("Position", "Color", "UV0"), true));
        assertEquals("attributes  fallback false", BinaryCache.attributes(List.of(), false));
    }

    @Test
    void stagesAreNamedAsIrisNamesThemAndAProgramWithNoneIsLeftToIris() {
        SavedPatches<Stage> p = new SavedPatches<>(Stage.class, cache, () -> true, () -> true, () -> false, false);
        assertNull(p.beforeGraphics("x", null, null, null, null, null, "params"));
        p.after(Map.of(Stage.VERTEX, "never saved"));
        assertNull(p.beforeGraphics("x", "void main() {}", null, null, null, null, "params"), "a miss");
        p.after(Map.of(Stage.VERTEX, "patched"));
        assertEquals(
                Map.of(Stage.VERTEX, "patched"),
                p.beforeGraphics("x", "void main() {}", null, null, null, null, "params"));
        for (int only = 0; only < 5; only++) {
            String[] s = new String[5];
            s[only] = "void main() {}";
            assertNull(
                    p.beforeGraphics("one stage " + only, s[0], s[1], s[2], s[3], s[4], "params"), "a miss, looked up");
            p.after(Map.of(Stage.VERTEX, "patched " + only));
            assertEquals(
                    Map.of(Stage.VERTEX, "patched " + only),
                    p.beforeGraphics("one stage " + only, s[0], s[1], s[2], s[3], s[4], "params"));
        }
        assertNull(p.beforeCompute("c", null, "params"));
        assertNull(p.beforeCompute("c", "void main() {}", "params"));
        p.after(Map.of(Stage.COMPUTE, "patched compute"));
        assertNotNull(p.beforeCompute("c", "void main() {}", "params"));
    }

    @Test
    void aFolderNotPreheatsIsNeverLocked() {
        assertNull(CacheDir.lockChannel(tmp.resolve("someone else's")));
    }
}
