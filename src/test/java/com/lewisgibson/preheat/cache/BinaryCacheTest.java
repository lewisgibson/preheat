package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureStates;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shader cache end to end on a fake driver and a real folder: what it loads, saves, refuses, logs and deletes. */
class BinaryCacheTest {
    private static final long MS = 1_000_000L;
    private static final String STALE = "00000000000000aa";

    @TempDir
    Path tmp;

    Path game;
    Path root;
    final FakeDriver gl = new FakeDriver();
    final AtomicLong now = new AtomicLong(1_000 * MS);
    final BuildTimer timer = new BuildTimer(now::get, () -> {});

    @BeforeEach
    void setUp() {
        game = tmp.resolve("game");
        root = game.resolve("preheat").resolve("shader-cache");
        Platforms.use(FakePlatform.client(game, "iris", "1.11.7", "preheat", "0.6.0"));
        ConfigFiles.use(game.resolve("config").resolve("preheat.json"));
        FeatureStates.allOnExcept();
        CachePrefetch.releaseAll();
    }

    @AfterEach
    void tearDown() {
        Platforms.use(null);
        ConfigFiles.use(null);
        FeatureStates.none();
    }

    private BinaryCache cache(long capBytes, @Nullable String debugMisses) {
        return new BinaryCache(gl, gl, Runnable::run, now::get, timer, capBytes, debugMisses, CacheDir::lockChannel);
    }

    private BinaryCache cache() {
        return cache(1L << 30, null);
    }

    private static Path generation(BinaryCache c) {
        Path g = c.generationDir();
        assertNotNull(g);
        return g;
    }

    @Test
    void theFirstUseLooksAtTheDriverOnceAndSaysWhatItFound() {
        gl.formats = new int[] {0x8E21, 0x8E22};
        BinaryCache c = cache();
        assertNull(c.whyOff(), "nothing known yet, so nothing wrong");
        assertNull(c.gpu(), "nothing asked before the first build");
        assertNull(c.generationDir());
        try (LogLines log = LogLines.capture()) {
            assertTrue(c.usable());
            assertTrue(c.usable());
            assertTrue(
                    log.has("INFO PREHEAT GPU: NVIDIA GeForce RTX 5090/PCIe/SSE2 (NVIDIA Corporation)"),
                    log.lines().toString());
            assertTrue(
                    log.has("2 program binary formats (0x8e21 0x8e22)"),
                    log.lines().toString());
            assertTrue(
                    log.has("INFO PREHEAT Shader cache at " + root + ": NVIDIA binaries"),
                    log.lines().toString());
            assertEquals(
                    1,
                    log.lines().stream().filter(l -> l.contains("PREHEAT GPU")).count(),
                    "looked once");
        }
        assertNotNull(c.gpu());
        assertNull(c.whyOff());
        assertEquals(root, c.root());
        assertEquals(root, generation(c).getParent());
        assertEquals(
                List.of("preheat-program-binary-v2 " + Preheat.version(), "iris 1.11.7"),
                c.context().subList(0, 2));
    }

    @Test
    void aDriverWithoutBinariesTurnsTheCacheOff() {
        gl.formats = new int[0];
        BinaryCache c = cache();
        try (LogLines log = LogLines.capture()) {
            assertFalse(c.usable());
            assertTrue(log.has("INFO PREHEAT Shader cache off: the driver offers no program binary formats"));
            assertTrue(log.has("program binary formats (none)"), log.lines().toString());
        }
        assertEquals(CacheOff.UNSUPPORTED_DRIVER, c.whyOff());
        c.retrievable(5);
        assertTrue(gl.calls.isEmpty(), "no hint where nothing is saved");

        gl.supported = false;
        gl.formats = new int[] {1};
        assertFalse(cache().usable(), "a context without program binaries at all");
    }

    @Test
    void withTheFeatureOffTheDriverIsNotEvenAsked() {
        FeatureStates.allOnExcept(Feature.PROGRAM_CACHE);
        BinaryCache c = cache();
        assertFalse(c.usable());
        assertNull(c.gpu());
    }

    @Test
    void aPausedFolderIsNotUsedUntilItIsDeleted() throws Exception {
        CacheDir.adopt(root);
        Files.writeString(root.resolve(CacheGuard.PAUSED), CacheGuard.STOPPED_WHILE_LOADING);
        BinaryCache c = cache();
        try (LogLines log = LogLines.capture()) {
            assertFalse(c.usable());
            assertTrue(
                    log.has("WARN PREHEAT Shader cache paused: stopped-while-loading"),
                    log.lines().toString());
        }
        assertEquals(CacheOff.PAUSED, c.whyOff());
        assertNull(c.generationDir());
        c.retrievable(5);
        assertTrue(gl.calls.isEmpty());
        assertEquals("", c.endBuild(), "a paused folder is not tidied");
        List<Boolean> done = new ArrayList<>();
        c.purge(done::add);
        assertEquals(List.of(true), done);
        assertNull(c.whyOff());
        assertTrue(c.usable());
    }

    @Test
    void aSavedBinaryIsLoadedNextTimeAndOlderGenerationsGo() throws Exception {
        CacheDir.adopt(root);
        Files.createDirectories(root.resolve(STALE));
        BinaryCache c = cache();
        assertTrue(c.usable());
        String key = c.key("builder", List.of("vertex", "void main() {}"));
        c.beginBuild();
        timer.begin("minecraft:overworld");
        c.retrievable(7);
        assertEquals(List.of("retrievable 7"), gl.calls);
        try (LogLines log = LogLines.capture()) {
            c.save(7, key);
            assertTrue(
                    log.has("INFO PREHEAT Shader cache: deleted 1 older cache generation(s)"),
                    log.lines().toString());
        }
        assertFalse(Files.exists(root.resolve(STALE)));
        assertTrue(Files.exists(generation(c).resolve(key + ProgramBinaryStore.SUFFIX)));
        assertEquals("", c.endBuild(), "nothing was looked up");

        c.beginBuild();
        int program = c.load(key);
        assertNotEquals(0, program);
        assertTrue(
                Files.exists(root.resolve("loading-" + ProcessHandle.current().pid() + ".marker")), "armed");
        assertEquals(0, c.load("missing"));
        String line = c.endBuild();
        assertTrue(line.startsWith("verified 1 cached binaries (0.0 MB) in "), line);
        assertFalse(
                Files.exists(root.resolve("loading-" + ProcessHandle.current().pid() + ".marker")), "disarmed");
        c.shutdown();
    }

    @Test
    void aBinaryInAFormatTheDriverNoLongerOffersIsAMiss() {
        BinaryCache c = cache();
        assertTrue(c.usable());
        gl.format = 0x1234;
        c.save(7, "k");
        c.beginBuild();
        assertEquals(0, c.load("k"));
        assertTrue(c.endBuild().startsWith("verified 1 cached binaries"), "read and checked, then not usable");
        assertTrue(Files.exists(generation(c).resolve("k" + ProgramBinaryStore.SUFFIX)), "kept for another driver");
    }

    @Test
    void aDamagedFileIsDeletedAndCounted() throws Exception {
        BinaryCache c = cache();
        assertTrue(c.usable());
        Files.createDirectories(generation(c));
        Files.write(generation(c).resolve("k" + ProgramBinaryStore.SUFFIX), new byte[] {1, 2, 3});
        c.beginBuild();
        assertEquals(0, c.load("k"));
        assertEquals("nothing cached yet, 1 damaged files deleted", c.endBuild());
    }

    @Test
    void theDriverRefusingBinariesIsLoggedAndStopsTheCacheForTheSession() {
        BinaryCache c = cache();
        assertTrue(c.usable());
        for (String k : List.of("a", "b", "c", "d")) {
            c.save(7, k);
        }
        gl.error = 0x502;
        c.beginBuild();
        try (LogLines log = LogLines.capture()) {
            for (String k : List.of("a", "b", "c", "d")) {
                assertEquals(0, c.load(k));
            }
            assertEquals(
                    2,
                    log.lines().stream()
                            .filter(
                                    l -> l.equals(
                                            "INFO PREHEAT The driver refused a cached shader (GL error 0x502); compiling it instead"))
                            .count());
            assertTrue(log.has("WARN PREHEAT The driver refused 3 cached shaders this session (last: GL error 0x502)"));
        }
        assertEquals(CacheOff.REFUSED, c.whyOff());
        assertTrue(Files.exists(generation(c).resolve("d" + ProgramBinaryStore.SUFFIX)), "not even tried");
        assertTrue(c.endBuild().endsWith(", 3 refused by the driver"));
        List<Boolean> done = new ArrayList<>();
        c.purge(done::add);
        assertNull(c.whyOff(), "a delete starts the count again");
    }

    @Test
    void beforeTheFirstBuildNothingIsLoadedSavedOrTidied() {
        BinaryCache c = cache();
        assertEquals("", c.endBuild(), "no folder yet");
        List<Boolean> done = new ArrayList<>();
        c.purge(done::add);
        assertEquals(List.of(true), done, "nothing there is nothing to fail");
        c.savePatch("early", () -> new byte[] {1});
        c.save(7, "opens the folder");
        c.savePatch("still early", () -> new byte[] {1});
        assertNull(c.generationDir(), "a folder, but no generation before the driver is known");
        c.beginBuild();
        assertEquals(0, c.load("k"));
        assertEquals("nothing cached yet", c.endBuild());
        assertFalse(
                Files.exists(root.resolve("loading-" + ProcessHandle.current().pid() + ".marker")));
    }

    @Test
    void aBinaryReadAheadIsUsedWithoutOpeningItsFile() throws Exception {
        BinaryCache c = cache();
        assertTrue(c.usable());
        c.save(7, "k");
        CachePrefetch.start(() -> root, s -> {}, r -> {}).join();
        Files.delete(generation(c).resolve("k" + ProgramBinaryStore.SUFFIX));
        c.beginBuild();
        assertNotEquals(0, c.load("k"));
        assertTrue(c.endBuild().startsWith("verified 1 cached binaries"));
        CachePrefetch.releaseAll();
    }

    @Test
    void programsThatDidNotLinkOrGaveNoBinaryAreNotSaved() {
        BinaryCache c = cache();
        c.save(7, "before");
        assertTrue(c.usable());
        assertFalse(Files.exists(generation(c).resolve("before" + ProgramBinaryStore.SUFFIX)), "no generation yet");
        c.beginBuild();
        c.load("x");
        gl.linked = false;
        c.save(7, "a");
        gl.linked = true;
        gl.binary = new byte[0];
        c.save(7, "b");
        assertEquals(
                "nothing cached yet, 1 not saved (did not link), 1 not saved (the driver gave no binary)",
                c.endBuild());
    }

    @Test
    void programsLinkedForLaterAreSavedWhenTheBuildEndsUnlessDeletedMeanwhile() {
        BinaryCache c = cache();
        assertTrue(c.usable());
        c.beginBuild();
        gl.programs.add(1);
        gl.programs.add(2);
        c.saveLater(1, "one");
        c.saveLater(2, "two");
        c.saveLater(3, "three");
        c.forgetPending(2);
        c.endBuild();
        assertTrue(Files.exists(generation(c).resolve("one" + ProgramBinaryStore.SUFFIX)));
        assertFalse(Files.exists(generation(c).resolve("two" + ProgramBinaryStore.SUFFIX)), "forgotten");
        assertFalse(Files.exists(generation(c).resolve("three" + ProgramBinaryStore.SUFFIX)), "no longer a program");
        c.saveLater(1, "left over");
        c.beginBuild();
        c.endBuild();
        assertFalse(
                Files.exists(generation(c).resolve("left over" + ProgramBinaryStore.SUFFIX)), "a new build drops it");
    }

    @Test
    void patchesAreCountedAndSavedBesideTheBinaries() throws Exception {
        BinaryCache c = cache();
        assertTrue(c.usable());
        c.beginBuild();
        c.countPatch(true);
        c.countPatch(false);
        c.savePatch("p", () -> PatchedSources.encode(java.util.Map.of("COMPUTE", "x")));
        assertEquals("1 of 2 patches from the cache; nothing cached yet", c.endBuild());
        assertTrue(Files.exists(generation(c).resolve("p" + PatchedSources.SUFFIX)));
        Path f = generation(c).resolve("p" + PatchedSources.SUFFIX);
        Files.setLastModifiedTime(f, FileTime.fromMillis(0));
        c.touchLater(f);
        assertTrue(Files.getLastModifiedTime(f).toMillis() > 0);
    }

    @Test
    void aWriteThatFailsIsLoggedAndTheGameCarriesOn() throws Exception {
        BinaryCache c = cache();
        assertTrue(c.usable());
        Files.writeString(generation(c), "a file where the generation folder goes");
        try (LogLines log = LogLines.capture()) {
            c.save(7, "k");
            assertTrue(
                    log.has("WARN PREHEAT Shader cache: could not write a binary"),
                    log.lines().toString());
        }
    }

    @Test
    void aMissIsWrittenOutOnlyWithTheDebugFolder() throws Exception {
        cache().debugMiss("gbuffers_terrain", "0123456789abcdef", List.of("a"));
        Path debug = tmp.resolve("misses");
        BinaryCache c = cache(1L << 30, debug.toString());
        c.debugMiss("gbuffers/terrain", "0123456789abcdef", List.of("one", "two"));
        assertEquals("one\n=====\ntwo", Files.readString(debug.resolve("gbuffers_terrain-0123456789ab.txt")));
        Path blocked = tmp.resolve("blocked");
        Files.writeString(blocked, "a file");
        try (LogLines log = LogLines.capture()) {
            cache(1L << 30, blocked.toString()).debugMiss("x", "0123456789abcdef", List.of());
            assertTrue(
                    log.has("WARN PREHEAT Could not write the cache-miss debug file for x"),
                    log.lines().toString());
        }
    }

    @Test
    void aProgramThatWillNotBeUsedIsDeleted() {
        cache().discard(9);
        assertEquals(List.of("delete 9"), gl.calls);
    }

    @Test
    void theHintIsGivenOnlyOnceTheDriverIsKnownAndWhileTheCacheIsOn() {
        BinaryCache c = cache();
        c.retrievable(1);
        c.save(7, "opens the folder");
        c.retrievable(1);
        assertTrue(c.usable());
        FeatureStates.allOnExcept(Feature.PROGRAM_CACHE);
        c.retrievable(2);
        FeatureStates.allOnExcept();
        c.retrievable(3);
        assertEquals(List.of("retrievable 3"), gl.calls);
    }

    @Test
    void aFolderAnotherGameUsesIsSharedNeverRetiredOrTidied() throws Exception {
        BinaryCache first = cache();
        assertTrue(first.usable());
        BinaryCache second = cache(1, null);
        try (LogLines log = LogLines.capture()) {
            assertTrue(second.usable());
            assertTrue(log.has("INFO PREHEAT Shader cache: another running game uses " + root + " too; sharing it"));
        }
        Files.createDirectories(root.resolve(STALE));
        second.save(7, "k");
        second.beginBuild();
        second.load("k");
        second.endBuild();
        assertTrue(Files.exists(root.resolve(STALE)), "not retired from a shared folder");
        assertTrue(Files.exists(generation(second).resolve("k" + ProgramBinaryStore.SUFFIX)), "nor tidied away");
    }

    @Test
    void theSafetyLimitDeletesTheOldestAndATidyThatFailsIsLogged() throws Exception {
        BinaryCache c = cache(10, null);
        assertTrue(c.usable());
        c.save(7, "old");
        Files.setLastModifiedTime(generation(c).resolve("old" + ProgramBinaryStore.SUFFIX), FileTime.fromMillis(0));
        c.save(7, "new");
        c.beginBuild();
        try (LogLines log = LogLines.capture()) {
            c.load("new");
            c.endBuild();
            assertTrue(
                    log.has("over its 0 GB safety limit, deleted the 2 oldest files"),
                    log.lines().toString());
        }
        Unreadable.during(generation(c), () -> {
            try (LogLines log = LogLines.capture()) {
                c.beginBuild();
                c.endBuild();
                assertTrue(
                        log.has("WARN PREHEAT Shader cache: could not tidy " + root),
                        log.lines().toString());
            }
        });
    }

    @Test
    void aDeleteThatFailsKeepsThePauseUnlessNothingIsLeftToDelete() throws Exception {
        CacheDir.adopt(root);
        Files.writeString(root.resolve(CacheGuard.PAUSED), CacheGuard.STOPPED_WHILE_LOADING);
        BinaryCache c = cache();
        assertFalse(c.usable());
        Path gen = Files.createDirectories(root.resolve(STALE));
        Files.writeString(gen.resolve("k" + ProgramBinaryStore.SUFFIX), "x");
        List<Boolean> done = new ArrayList<>();
        Unreadable.during(gen, () -> c.purge(done::add));
        assertEquals(List.of(false), done, "a generation could not be deleted");
        assertEquals(CacheOff.PAUSED, c.whyOff());

        Files.delete(root.resolve(CacheGuard.PAUSED));
        Files.createDirectories(root.resolve(CacheGuard.PAUSED).resolve("in the way"));
        c.purge(done::add);
        assertEquals(List.of(false, true), done, "every generation went, which is what the delete is for");

        AtomicReference<Boolean> listed = new AtomicReference<>();
        Files.createDirectories(root.resolve(STALE));
        java.util.Set<java.nio.file.attribute.PosixFilePermission> before = Files.getPosixFilePermissions(root);
        Files.setPosixFilePermissions(root, java.nio.file.attribute.PosixFilePermissions.fromString("-wx------"));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(root));
            c.purge(listed::set);
        } finally {
            Files.setPosixFilePermissions(root, before);
        }
        assertEquals(false, listed.get(), "a folder that cannot be listed cannot be said to be empty");
    }

    @Test
    void movingTheCacheMovesWhatIsSavedOrStartsFresh() throws Exception {
        BinaryCache c = cache();
        assertTrue(c.usable());
        c.save(7, "k");
        Path chosen = Files.createDirectories(tmp.resolve("chosen"));
        AtomicReference<@Nullable FolderProblem> result = new AtomicReference<>(FolderProblem.MOVE_FAILED);
        c.changeFolder(chosen.toString(), true, result::set);
        assertNull(result.get());
        Path moved =
                chosen.resolve("preheat-shader-cache").resolve(generation(c).getFileName());
        assertTrue(Files.exists(moved.resolve("k" + ProgramBinaryStore.SUFFIX)));
        c.useFolder(chosen.toString());
        assertEquals(chosen.toString(), PreheatConfig.get().cacheFolder);
        assertEquals(chosen.resolve("preheat-shader-cache"), c.root());
        assertTrue(c.usable());
        assertEquals(chosen.resolve("preheat-shader-cache"), generation(c).getParent());

        result.set(FolderProblem.MOVE_FAILED);
        c.changeFolder("", false, result::set);
        assertNull(result.get());
        c.useFolder("");
        assertEquals(root, c.root());
        c.useFolder("");
        assertEquals(root, c.root(), "the same folder again changes nothing");
    }

    @Test
    void aFolderThatCannotBeUsedOrMovedIntoSaysWhy() throws Exception {
        BinaryCache c = cache();
        AtomicReference<@Nullable FolderProblem> result = new AtomicReference<>();
        c.changeFolder("relative/path", true, result::set);
        assertEquals(FolderProblem.NOT_FULL_PATH, result.get());
        assertTrue(c.usable());
        c.save(7, "k");
        try (LogLines log = LogLines.capture()) {
            c.changeFolder(root.toString(), true, result::set);
            assertEquals(FolderProblem.MOVE_FAILED, result.get(), "into a folder inside the cache itself");
            assertTrue(
                    log.has("WARN PREHEAT Shader cache: could not move it to " + root),
                    log.lines().toString());
        }
    }

    @Test
    void aChosenFolderThatCannotBeUsedFallsBackToTheDefaultAndSaysSo() throws Exception {
        PreheatConfig next = PreheatConfig.get().copy();
        next.cacheFolder = "relative/path";
        PreheatConfig.save(next);
        BinaryCache c = cache();
        assertEquals(FolderProblem.NOT_FULL_PATH, c.folderProblem());
        try (LogLines log = LogLines.capture()) {
            assertTrue(c.usable());
            assertTrue(log.has("the chosen folder relative/path cannot be used (NOT_FULL_PATH); using " + root));
        }
    }

    @Test
    void aDefaultFolderThatCannotBeMadeIsLoggedAndSharedRatherThanAnError() throws Exception {
        Files.createDirectories(root.getParent());
        Files.writeString(root, "a file where the cache folder goes");
        try (LogLines log = LogLines.capture()) {
            cache().usable();
            assertTrue(
                    log.has("WARN PREHEAT Shader cache: could not prepare " + root),
                    log.lines().toString());
        }
        cache().shutdown();
    }

    @Test
    void usageCountsWhatIsSaved() {
        BinaryCache c = cache();
        assertTrue(c.usable());
        c.save(7, "k");
        assertEquals(1, c.usage().files());
    }

    @Test
    void theSameFolderIsKeptWhenTheSettingIsUnchanged() throws IOException {
        BinaryCache c = cache();
        c.settingsChanged();
        assertTrue(c.usable());
        Path g = generation(c);
        c.settingsChanged();
        assertEquals(g, c.generationDir(), "the folder was not reopened");
    }

    @Test
    void aSharedFolderIsLeftWithoutUnlockingAndAnUnlockThatFailsIsHarmless() throws Exception {
        assertTrue(cache().usable());
        BinaryCache shared = cache();
        assertTrue(shared.usable());
        Path chosen = Files.createDirectories(tmp.resolve("chosen"));
        shared.useFolder(chosen.toString());
        assertEquals(chosen.resolve("preheat-shader-cache"), shared.root());

        List<String> closed = new ArrayList<>();
        BinaryCache stubborn = new BinaryCache(gl, gl, Runnable::run, now::get, timer, 1L << 30, null, r -> () -> {
            closed.add(r.getFileName().toString());
            throw new IOException("will not close");
        });
        assertTrue(stubborn.usable());
        stubborn.useFolder("");
        assertEquals(List.of("preheat-shader-cache"), closed);
        assertEquals(root, stubborn.root());
        assertTrue(stubborn.usable());
    }
}
