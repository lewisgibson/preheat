package com.lewisgibson.preheat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.cache.CacheDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheFolderTest {
    @TempDir
    Path tmp;

    @Test
    void theDefaultIsTheInstanceFolder() {
        CacheFolder.Resolved r = CacheFolder.resolve(tmp, "");
        assertEquals(tmp.resolve("preheat").resolve("shader-cache"), r.folder());
        assertNull(r.problem());
        assertFalse(r.custom(tmp));
        assertEquals(r.folder(), CacheFolder.resolve(tmp, null).folder());
    }

    @Test
    void aUsableCustomFolderIsUsedThroughAFolderPreheatMakesInsideIt() {
        Path custom = tmp.resolve("elsewhere").resolve("cache");
        CacheFolder.Resolved r = CacheFolder.resolve(tmp, custom.toString());
        assertEquals(custom.resolve(CacheFolder.CHILD), r.folder(), "never the chosen folder itself");
        assertNull(r.problem());
        assertTrue(r.custom(tmp));
        assertTrue(Files.isDirectory(custom));
        try (var files = Files.list(custom)) {
            assertEquals(0, files.count(), "the write test leaves nothing behind");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void anUnusableCustomFolderFallsBackToTheInstanceWithTheReason() throws Exception {
        Path file = Files.writeString(tmp.resolve("a-file"), "x");
        CacheFolder.Resolved r = CacheFolder.resolve(tmp, file.toString());
        assertEquals(CacheFolder.instanceFolder(tmp), r.folder());
        assertEquals(FolderProblem.IS_A_FILE, r.problem());
        assertEquals(
                FolderProblem.NOT_FULL_PATH,
                CacheFolder.resolve(tmp, "relative/path").problem());
        assertEquals(
                FolderProblem.INVALID, CacheFolder.resolve(tmp, "bad\0path").problem());
        assertEquals(FolderProblem.INVALID, CacheFolder.check("bad\0path"));
        Path taken = Files.createDirectories(tmp.resolve("taken").resolve(CacheFolder.CHILD));
        Files.writeString(taken.resolve("theirs.txt"), "x");
        assertEquals(
                FolderProblem.NOT_OURS,
                CacheFolder.check(tmp.resolve("taken")),
                "a same-named folder that is not Preheat's");
        assertEquals(null, CacheFolder.check(tmp.resolve("typed").toString()));
        Path locked = Files.createDirectory(tmp.resolve("locked"));
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-x------"));
        try {
            assertEquals(FolderProblem.NOT_WRITABLE, CacheFolder.check(locked), "a folder that cannot be written to");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void theFolderPreheatMakesInsideMayAlreadyBeThereIfItIsPreheatsOrEmpty() throws Exception {
        Path chosen = Files.createDirectories(tmp.resolve("chosen"));
        Path child = CacheFolder.ownedChild(chosen);
        Files.createDirectories(child);
        assertNull(CacheFolder.check(chosen), "an empty folder");
        CacheDir.ensureOwned(child);
        Files.writeString(child.resolve("kept"), "x");
        assertNull(CacheFolder.check(chosen), "Preheat's own");
        Path other = Files.createDirectories(tmp.resolve("other"));
        Files.writeString(CacheFolder.ownedChild(other), "a file of the player's where the folder would go");
        assertEquals(FolderProblem.NOT_OURS, CacheFolder.check(other));
    }
}
