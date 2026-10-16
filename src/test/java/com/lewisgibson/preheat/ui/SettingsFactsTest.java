package com.lewisgibson.preheat.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.cache.CacheOff;
import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the settings screens say, against a fake Iris, cache and clock. */
class SettingsFactsTest {
    @TempDir
    Path tmp;

    @Nullable
    Set<String> pack = Set.of();

    final Set<String> built = new HashSet<>();
    boolean managerIsPreheats = true;

    @Nullable
    RuntimeException irisFails;

    ProgramBinaryStore.Usage usage = new ProgramBinaryStore.Usage(0, 0);
    final AtomicInteger usageReads = new AtomicInteger();

    @Nullable
    FolderProblem problem;

    @Nullable
    CacheOff off;

    final AtomicLong now = new AtomicLong();

    SettingsFacts facts() {
        Supplier<Optional<Set<String>>> packs = () -> {
            if (irisFails != null) {
                throw irisFails;
            }
            return Optional.ofNullable(pack);
        };
        Supplier<@Nullable Predicate<String>> isBuilt = () -> {
            if (irisFails != null) {
                throw irisFails;
            }
            return managerIsPreheats ? built::contains : null;
        };
        return new SettingsFacts(
                packs,
                isBuilt,
                () -> {
                    usageReads.incrementAndGet();
                    return usage;
                },
                () -> problem,
                () -> off,
                now::get);
    }

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

    private static String key(Words w) {
        return String.valueOf(w.key());
    }

    @Test
    void theStatusLineSaysWhyPreheatIsOffOrWhatIsReady() {
        built.add(KnownDimensions.OVERWORLD);
        built.add(KnownDimensions.NETHER);
        assertEquals(Words.translated("preheat.status.ready", "Overworld, Nether"), facts().status());
        assertTrue(facts().ready());
        built.clear();
        assertEquals("preheat.status.notReady", key(facts().status()));
        assertFalse(facts().ready());
        pack = null;
        assertEquals("preheat.status.noPack", key(facts().status()));
        assertFalse(facts().ready());
        PreheatConfig.change(c -> {
            c.moddedDimensions = false;
            c.dimensions.replaceAll((id, on) -> false);
        });
        assertEquals("preheat.status.disabled", key(facts().status()));
        assertFalse(facts().ready());
        PreheatConfig.change(c -> c.enabled = false);
        assertEquals("preheat.status.switchedOff", key(facts().status()));
        FeatureStates.allOnExcept(Feature.KEEP_PIPELINES, Feature.PROGRAM_CACHE);
        assertEquals("preheat.status.unavailable", key(facts().status()));
        assertFalse(facts().available());
        assertFalse(facts().ready());
        FeatureStates.allOnExcept(Feature.KEEP_PIPELINES);
        assertTrue(facts().available(), "the shader cache alone is something");
    }

    @Test
    void anIrisThatIsNotReadyOrNotPreheatsReadsAsNothingReady() {
        built.add(KnownDimensions.OVERWORLD);
        managerIsPreheats = false;
        assertEquals("preheat.status.notReady", key(facts().status()));
        managerIsPreheats = true;
        irisFails = new IllegalStateException("Iris is still loading");
        assertEquals("preheat.status.noPack", key(facts().status()));
        assertEquals("preheat.cache.noneReady", key(facts().readyValue()));
        assertEquals(KnownDimensions.VANILLA, facts().listedDimensions(), "every dimension in the file is listed");
    }

    @Test
    void theDimensionsListedAreThoseWithShadersOfTheirOwnUnderThePackInUse() {
        PreheatConfig.change(c -> {
            c.dimensions.put("aether:the_aether", true);
            c.dimensions.put("twilight:forest", true);
        });
        pack = Set.of("aether:the_aether");
        assertEquals(
                List.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END, "aether:the_aether"),
                facts().listedDimensions());
        pack = null;
        assertEquals(5, facts().listedDimensions().size(), "without a pack every one is listed");
        built.add("aether:the_aether");
        pack = Set.of("aether:the_aether");
        assertEquals(Words.plain("The Aether"), facts().readyValue());
    }

    @Test
    void whatIsSavedIsMeasuredAtMostOnceASecond() {
        SettingsFacts f = facts();
        assertEquals("preheat.cache.nothing", key(f.savedValue()));
        usage = new ProgramBinaryStore.Usage(3, 40L << 20);
        assertEquals("preheat.cache.nothing", key(f.savedValue()), "the reading from a moment ago");
        now.addAndGet(1_000_000_001L);
        assertEquals(Words.plain("40 MB"), f.savedValue());
        assertEquals(2, usageReads.get());
    }

    @Test
    void theFolderAndWhyTheCacheOrTheFolderIsNotUsed() {
        assertEquals("preheat.cache.folder.default", key(facts().folderValue()));
        assertNull(facts().cacheNote());
        assertNull(facts().folderWarning());
        PreheatConfig.change(c -> c.cacheFolder = "/games/cache");
        assertEquals(Words.plain("/games/cache"), facts().folderValue());
        problem = FolderProblem.NOT_WRITABLE;
        assertEquals("preheat.cache.folder.default", key(facts().folderValue()), "a folder that cannot be used");
        Words warning =
                Words.translated("preheat.cache.folder.notUsed", Words.translated(FolderProblem.NOT_WRITABLE.key));
        assertEquals(warning, facts().folderWarning());
        assertEquals(warning, facts().cacheNote());
        off = CacheOff.PAUSED;
        assertEquals(Words.translated(CacheOff.PAUSED.key), facts().cacheNote(), "the cache being off comes first");
    }

    @Test
    void sizesReadTheWayFileManagersShowThem() {
        assertEquals("640 KB", SettingsFacts.size(640 * 1024));
        assertEquals("1 KB", SettingsFacts.size(1));
        assertEquals("38 MB", SettingsFacts.size((37L << 20) + 1));
        assertEquals("1.5 GB", SettingsFacts.size(3L << 29));
    }

    @Test
    void aDimensionsSwitchIdIsAPlainResourcePath() {
        assertEquals("dimension/minecraft/the_nether", SettingsFacts.optionPath("minecraft:the_nether"));
        assertEquals("dimension/mod/odd_name_", SettingsFacts.optionPath("Mod:Odd Name!"));
    }
}
