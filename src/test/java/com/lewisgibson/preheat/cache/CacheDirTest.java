package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for the review's must-fix 1: nothing Preheat deletes can be the player's. */
class CacheDirTest {
    private static final String OLD = "00000000000000aa";
    private static final String NOW = "00000000000000bb";

    @TempDir
    Path tmp;

    private Path owned() throws IOException {
        Path root = tmp.resolve("cache");
        CacheDir.ensureOwned(root);
        return root;
    }

    private static void bin(Path generation, String name) throws IOException {
        Files.createDirectories(generation);
        Files.writeString(generation.resolve(name + ProgramBinaryStore.SUFFIX), name);
    }

    @Test
    void aFolderWithoutTheMarkerIsNeverTouched() throws Exception {
        Path docs = Files.createDirectories(tmp.resolve("Documents"));
        Files.createDirectories(docs.resolve("Photos"));
        Files.writeString(docs.resolve("Photos").resolve("holiday.jpg"), "x");
        bin(docs.resolve(OLD), "k");
        Files.writeString(docs.resolve("x.tmp"), "x");

        assertEquals(0, CacheDir.retireOthers(docs, NOW));
        CacheDir.purge(docs);
        assertEquals(0, CacheDir.deleteTemporaries(docs));
        assertEquals(0, CacheDir.move(docs, tmp.resolve("to")));
        assertEquals(0, ProgramBinaryStore.enforceCap(docs, 0));
        assertTrue(Files.exists(docs.resolve("Photos").resolve("holiday.jpg")));
        assertTrue(Files.exists(docs.resolve(OLD).resolve("k.bin")), "not even a generation-shaped folder");
        assertTrue(Files.exists(docs.resolve("x.tmp")));
        assertThrows(IOException.class, () -> CacheDir.ensureOwned(docs), "a folder with files is never claimed");
    }

    @Test
    void onlyGenerationFoldersAreRetiredAndPurged() throws Exception {
        Path root = owned();
        bin(root.resolve(OLD), "a");
        bin(root.resolve(NOW), "b");
        Files.createDirectories(root.resolve("My Music"));
        Files.writeString(root.resolve("My Music").resolve("song.mp3"), "x");
        Files.writeString(root.resolve("notes.txt"), "x");

        assertEquals(1, CacheDir.retireOthers(root, NOW));
        assertFalse(Files.exists(root.resolve(OLD)));
        assertTrue(Files.exists(root.resolve(NOW).resolve("b.bin")));
        CacheDir.purge(root);
        assertFalse(Files.exists(root.resolve(NOW)));
        assertTrue(Files.exists(root.resolve("My Music").resolve("song.mp3")), "a folder not named like a generation");
        assertTrue(Files.exists(root.resolve("notes.txt")));
        assertTrue(CacheDir.isOwned(root), "the folder and its marker stay");
    }

    @Test
    void linksAreRemovedNeverFollowed() throws Exception {
        Path root = owned();
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.writeString(outside.resolve("precious.txt"), "x");
        // A link named like a generation, and a link inside a real generation.
        Files.createSymbolicLink(root.resolve(OLD), outside);
        bin(root.resolve(NOW), "k");
        Files.createSymbolicLink(root.resolve(NOW).resolve("link"), outside);

        assertEquals(0, CacheDir.retireOthers(root, NOW), "a link is not a generation");
        assertTrue(Files.exists(outside.resolve("precious.txt")));
        CacheDir.deleteTree(root.resolve(NOW));
        assertFalse(Files.exists(root.resolve(NOW), LinkOption.NOFOLLOW_LINKS));
        CacheDir.deleteTree(root.resolve(OLD));
        assertFalse(Files.exists(root.resolve(OLD), LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(outside.resolve("precious.txt")), "what a link points at is never deleted");
        CacheDir.deleteTree(root.resolve("missing"));
    }

    @Test
    void movingTakesOnlyPreheatsFilesAndLeavesTheRest() throws Exception {
        Path from = owned();
        bin(from.resolve(OLD), "a");
        bin(from.resolve(OLD), "b");
        Files.writeString(from.resolve("theirs.txt"), "x");
        Path to = tmp.resolve("to");
        assertEquals(2, CacheDir.move(from, to));
        assertEquals("a", Files.readString(to.resolve(OLD).resolve("a.bin")));
        assertTrue(CacheDir.isOwned(to));
        assertFalse(Files.exists(from.resolve(OLD)));
        assertTrue(Files.exists(from.resolve("theirs.txt")), "a file Preheat did not make stays where it was");
        assertEquals(0, CacheDir.move(from, to), "nothing left to move");
        assertEquals(0, CacheDir.move(to, to), "the same folder");
        assertThrows(IOException.class, () -> CacheDir.move(to, to.resolve("inside")));
    }

    @Test
    void aFailedMoveLeavesTheOldFolderAndRemovesThePartialCopy() throws Exception {
        Path from = owned();
        bin(from.resolve(OLD), "a");
        bin(from.resolve(OLD), "b");
        Path to = tmp.resolve("to");
        CacheDir.ensureOwned(to);
        // A folder where b.bin must go makes its copy fail after a.bin, which comes first in name order, was copied.
        Files.createDirectories(to.resolve(OLD).resolve("b.bin").resolve("blocker"));
        assertThrows(IOException.class, () -> CacheDir.move(from, to));
        assertTrue(Files.exists(from.resolve(OLD).resolve("a.bin")));
        assertTrue(Files.exists(from.resolve(OLD).resolve("b.bin")));
        assertFalse(Files.exists(to.resolve(OLD).resolve("a.bin")), "the partial copy is removed");
    }

    @Test
    void temporariesAreDeletedOnlyInsideGenerations() throws Exception {
        Path root = owned();
        bin(root.resolve(NOW), "k");
        Files.writeString(root.resolve(NOW).resolve("x.tmp"), "x");
        Files.writeString(root.resolve("theirs.tmp"), "x");
        assertEquals(1, CacheDir.deleteTemporaries(root));
        assertTrue(Files.exists(root.resolve("theirs.tmp")));
    }

    @Test
    void theDefaultFolderIsAdoptedEvenWithAnOlderCacheInIt() throws Exception {
        Path root = tmp.resolve("preheat").resolve("shader-cache");
        bin(root.resolve(OLD), "k");
        CacheDir.adopt(root);
        assertTrue(CacheDir.isOwned(root));
        assertEquals(1, CacheDir.generations(root).size());
        CacheDir.ensureOwned(root);
        CacheDir.ensureOwned(tmp.resolve("empty"));
        assertTrue(CacheDir.isOwned(tmp.resolve("empty")));
    }

    @Test
    void oneGameAtATimeOwnsTheFolder() throws Exception {
        Path root = owned();
        assertNull(CacheDir.lock(tmp.resolve("not-owned")));
        FileLock lock = CacheDir.lock(root);
        assertNotNull(lock);
        try {
            assertNull(CacheDir.lock(root), "a second holder in the same process is refused");
        } finally {
            lock.channel().close();
        }
        FileLock again = CacheDir.lock(root);
        assertNotNull(again);
        again.channel().close();
    }

    @Test
    void adoptingAFolderAlreadyMarkedLeavesItsMarker() throws Exception {
        Path root = owned();
        Files.writeString(root.resolve(CacheDir.MARKER), "written by an earlier launch");
        CacheDir.adopt(root);
        assertEquals("written by an earlier launch", Files.readString(root.resolve(CacheDir.MARKER)));
    }

    @Test
    void onlyCacheFilesAreMovedAndATemporaryFolderIsNotDeleted() throws Exception {
        Path from = owned();
        bin(from.resolve(NOW), "kept");
        Files.writeString(from.resolve(NOW).resolve("notes.txt"), "the player's");
        Files.createDirectories(from.resolve(NOW).resolve("odd.tmp"));
        assertEquals(0, CacheDir.deleteTemporaries(from), "a folder named like a temporary is not one");
        Path to = tmp.resolve("moved");
        assertEquals(1, CacheDir.move(from, to));
        assertTrue(Files.exists(to.resolve(NOW).resolve("kept" + ProgramBinaryStore.SUFFIX)));
        assertFalse(Files.exists(to.resolve(NOW).resolve("notes.txt")));
    }

    @Test
    void aFolderThatCouldNotBeListedIsNotDeleted() throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("half"));
        IOException listing = new IOException("could not list");
        IOException thrown =
                assertThrows(IOException.class, () -> new CacheDir.TreeDeleter().postVisitDirectory(dir, listing));
        assertEquals(listing, thrown);
        assertTrue(Files.exists(dir));
    }

    @Test
    void aFolderLockedByAnotherGameOrAlreadyByThisOneIsShared() throws Exception {
        Path root = owned();
        assertNull(CacheDir.lock(root, channel -> null), "another process holds it");
        FileLock mine = CacheDir.lock(root);
        assertNotNull(mine);
        try {
            assertNull(CacheDir.lock(root), "this game's own earlier lock");
        } finally {
            mine.channel().close();
        }
    }
}
