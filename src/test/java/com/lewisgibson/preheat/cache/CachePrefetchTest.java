package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachePrefetchTest {
    private static final byte[] IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];

    @TempDir
    Path root;

    private Path store(String generation, String key, int size, long modifiedMillis) throws IOException {
        Path dir = root.resolve(generation);
        new ProgramBinaryStore(dir).store(key, new ProgramBinaryStore.Entry(0x8E21, new byte[size], IFACE));
        Path f = dir.resolve(key + ProgramBinaryStore.SUFFIX);
        Files.setLastModifiedTime(f, FileTime.fromMillis(modifiedMillis));
        return f;
    }

    @Test
    void readsTheGenerationWithTheMostRecentlyUsedFile() throws IOException {
        CacheDir.adopt(root);
        store("aaaaaaaaaaaaaaaa", "old", 10, 1_000);
        store("bbbbbbbbbbbbbbbb", "new", 10, 9_000);
        store("bbbbbbbbbbbbbbbb", "older", 10, 500);
        CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES);
        assertNotNull(p);
        assertEquals(root.resolve("bbbbbbbbbbbbbbbb"), p.generation());
        assertEquals(2, p.stats().entries());
        assertNotNull(p.take(root.resolve("bbbbbbbbbbbbbbbb"), "new"));
        assertNull(p.take(root.resolve("aaaaaaaaaaaaaaaa"), "old"), "another generation's folder never matches");
    }

    @Test
    void anEntryIsHandedOverOnceAndReleaseLetsGoOfTheRest() throws IOException {
        CacheDir.adopt(root);
        Path gen = root.resolve("cccccccccccccccc");
        store("cccccccccccccccc", "one", 10, 2_000);
        store("cccccccccccccccc", "two", 10, 1_000);
        CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES);
        assertNotNull(p);
        assertNotNull(p.take(gen, "one"));
        assertNull(p.take(gen, "one"), "handed over once, so the memory goes with it");
        p.release();
        assertNull(p.take(gen, "two"));
        assertEquals(0, p.stats().entries());
    }

    @Test
    void theBudgetKeepsTheMostRecentlyUsedFiles() throws IOException {
        CacheDir.adopt(root);
        Path gen = root.resolve("dddddddddddddddd");
        store("dddddddddddddddd", "newest", 1000, 3_000);
        store("dddddddddddddddd", "middle", 1000, 2_000);
        store("dddddddddddddddd", "oldest", 1000, 1_000);
        long fileSize = 1000L + ProgramBinaryStore.HEADER;
        CachePrefetch p = CachePrefetch.read(root, 2 * fileSize);
        assertNotNull(p);
        assertEquals(2, p.stats().entries());
        assertEquals(2 * fileSize, p.stats().bytes());
        assertNotNull(p.take(gen, "newest"));
        assertNotNull(p.take(gen, "middle"));
        assertNull(p.take(gen, "oldest"));
    }

    @Test
    void aDamagedFileIsLeftForTheNormalPathToDelete() throws IOException {
        CacheDir.adopt(root);
        Path gen = root.resolve("eeeeeeeeeeeeeeee");
        Path f = store("eeeeeeeeeeeeeeee", "torn", 100, 1_000);
        byte[] b = Files.readAllBytes(f);
        b[b.length - 1] ^= 1;
        Files.write(f, b);
        CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES);
        assertNotNull(p);
        assertNull(p.take(gen, "torn"));
        assertTrue(Files.exists(f), "read-ahead never deletes");
        assertTrue(new ProgramBinaryStore(gen).load("torn").damaged(), "the normal path finds it damaged");
    }

    @Test
    void nothingToReadIsNull() throws IOException {
        assertNull(CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES), "not an owned folder");
        CacheDir.adopt(root);
        assertNull(CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES), "no generations");
        Files.createDirectories(root.resolve("ffffffffffffffff"));
        assertNull(CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES), "an empty generation");
    }

    @Test
    void startReadsInTheBackgroundAndReleaseAllEndsIt() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("0123456789abcdef");
        store("0123456789abcdef", "k", 10, 1_000);
        CompletableFuture<CachePrefetch.Stats> heard = new CompletableFuture<>();
        CachePrefetch.start(() -> root, heard::complete);
        assertEquals(1, heard.get(10, TimeUnit.SECONDS).entries());
        assertNotNull(CachePrefetch.taken(gen, "k"));
        CachePrefetch.releaseAll();
        assertNull(CachePrefetch.taken(gen, "k"));
    }

    @Test
    void patchesAreReadAheadBesideTheBinaries() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("1111111111111111");
        store("1111111111111111", "bin", 10, 1_000);
        java.util.Map<String, @org.jspecify.annotations.Nullable String> stages = new java.util.LinkedHashMap<>();
        stages.put("VERTEX", "void main() {}");
        stages.put("GEOMETRY", null);
        PatchedSources.store(gen, "patch", PatchedSources.encode(stages));
        Path torn = gen.resolve("torn" + PatchedSources.SUFFIX);
        Files.write(torn, new byte[] {1, 2, 3});
        CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES);
        assertNotNull(p);
        assertEquals(2, p.stats().entries(), "the binary and the good patch");
        assertEquals(stages, p.takePatch(gen, "patch"));
        assertNull(p.takePatch(gen, "patch"), "handed over once");
        assertNull(p.takePatch(root.resolve("2222222222222222"), "patch"));
        assertNull(p.takePatch(gen, "torn"));
        assertTrue(Files.exists(torn));
        p.release();
        assertNull(p.takePatch(gen, "bin"));
    }

    @Test
    void theStaticReadAheadHandsOverPatchesToo() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("3333333333333333");
        java.util.Map<String, @org.jspecify.annotations.Nullable String> stages = java.util.Map.of("COMPUTE", "x");
        PatchedSources.store(gen, "c", PatchedSources.encode(stages));
        CompletableFuture<CachePrefetch.Stats> heard = new CompletableFuture<>();
        CachePrefetch.start(() -> root, heard::complete);
        assertEquals(1, heard.get(10, TimeUnit.SECONDS).entries());
        assertEquals(stages, CachePrefetch.takenPatch(gen, "c"));
        CachePrefetch.releaseAll();
        assertNull(CachePrefetch.takenPatch(gen, "c"));
    }

    @Test
    void whatWasReadIsLetGoOnceItsHoldIsOverUnlessAnotherReadAheadTookItsPlace() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("4444444444444444");
        store("4444444444444444", "k", 10, 1_000);
        CompletableFuture<CachePrefetch.Stats> heard = new CompletableFuture<>();
        java.util.List<Runnable> holds = new java.util.ArrayList<>();
        CachePrefetch.start(() -> root, heard::complete, holds::add).join();
        assertEquals(1, heard.get().entries());
        CachePrefetch.start(() -> root, s -> {}, holds::add).join();
        holds.get(0).run();
        assertNotNull(CachePrefetch.taken(gen, "k"), "the first hold is over, but a newer read-ahead is current");
        holds.get(1).run();
        assertNull(CachePrefetch.takenPatch(gen, "k"), "the newer one's hold is over too");
        CachePrefetch.releaseAll();
        CachePrefetch.releaseAll();
        assertNull(CachePrefetch.taken(gen, "k"), "releasing twice is harmless");
    }

    @Test
    void aReleaseWhileReadingLeavesNothingHeld() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("5555555555555555");
        store("5555555555555555", "k", 10, 1_000);
        java.util.List<CachePrefetch.Stats> heard = new java.util.ArrayList<>();
        CachePrefetch.start(
                        () -> {
                            CachePrefetch.releaseAll();
                            return root;
                        },
                        heard::add,
                        Runnable::run)
                .join();
        assertNull(CachePrefetch.taken(gen, "k"));
        assertTrue(heard.isEmpty(), "nothing is reported for a read-ahead let go of");
    }

    @Test
    void aFolderThatCannotBeWorkedOutReadsNothing() throws Exception {
        java.util.List<CachePrefetch.Stats> heard = new java.util.ArrayList<>();
        CachePrefetch.start(
                        () -> {
                            throw new IllegalStateException("settings unreadable");
                        },
                        heard::add,
                        Runnable::run)
                .join();
        CachePrefetch.start(() -> root, heard::add, Runnable::run).join();
        assertTrue(heard.isEmpty(), "an error, then nothing to read");
    }

    @Test
    void anInterruptedReadKeepsTheInterruptAndWhatItHas() throws IOException {
        CacheDir.adopt(root);
        store("6666666666666666", "k", 10, 1_000);
        Thread.currentThread().interrupt();
        CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES);
        assertTrue(Thread.interrupted(), "the interrupt is kept for the caller");
        assertNotNull(p);
    }

    @Test
    void patchesCountWhatTheyHoldUnpackedAgainstTheBudget() throws IOException {
        CacheDir.adopt(root);
        Path gen = root.resolve("7777777777777777");
        java.util.Map<String, @org.jspecify.annotations.Nullable String> big =
                java.util.Map.of("VERTEX", "x".repeat(4000));
        PatchedSources.store(gen, "big", PatchedSources.encode(big));
        Files.setLastModifiedTime(gen.resolve("big" + PatchedSources.SUFFIX), FileTime.fromMillis(2_000));
        store("7777777777777777", "after", 10, 1_000);
        Files.writeString(gen.resolve("notes.txt"), "not the cache's");
        CachePrefetch p = CachePrefetch.read(root, 1_000, 1);
        assertNotNull(p);
        assertEquals(big, p.takePatch(gen, "big"), "small on disk, so it was read");
        assertNull(p.take(gen, "after"), "but it holds more than the budget, so nothing more is read");
    }

    @Test
    void anUnreadableFileOrGenerationIsLeftToTheNormalPath() throws Exception {
        CacheDir.adopt(root);
        Path gen = root.resolve("8888888888888888");
        Path f = store("8888888888888888", "locked", 10, 1_000);
        Unreadable.during(f, () -> {
            CachePrefetch p = CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES, 1);
            assertNotNull(p);
            assertNull(p.take(gen, "locked"));
        });
        Unreadable.during(gen, () -> assertNull(CachePrefetch.read(root, CachePrefetch.BUDGET_BYTES)));
    }
}
