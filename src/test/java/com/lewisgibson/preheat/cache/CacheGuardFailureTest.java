package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The guard never throws: a folder it cannot use means no pause and no marker, as before the guard existed. */
class CacheGuardFailureTest {
    @TempDir
    Path tmp;

    @Test
    void aCacheFolderThatIsAFileIsHarmless() throws Exception {
        Path file = Files.writeString(tmp.resolve("not-a-folder"), "x");
        CacheGuard g = new CacheGuard(file);
        g.arm("0123456789abcdef", "minecraft:the_nether");
        g.disarm();
        assertNull(g.checkAtStartup());
    }

    @Test
    void anUnreadableMarkerMeansNoPause() throws Exception {
        Files.createDirectories(tmp.resolve(CacheGuard.LEGACY_LOADING).resolve("child"));
        assertNull(new CacheGuard(tmp).checkAtStartup());
        Files.writeString(tmp.resolve("loading-notanumber.marker"), "x");
        assertNull(new CacheGuard(tmp).checkAtStartup());
    }

    @Test
    void aMarkerThatCannotBeDeletedIsLeftForNextTime() throws Exception {
        CacheGuard g = new CacheGuard(tmp, 42, p -> false);
        Files.createDirectories(tmp.resolve("loading-42.marker").resolve("child"));
        g.disarm();
        assertFalse(Files.isRegularFile(tmp.resolve("loading-42.marker")));
    }
}
