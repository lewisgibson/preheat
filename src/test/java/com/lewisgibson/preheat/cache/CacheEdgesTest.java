package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheEdgesTest {
    @TempDir
    Path tmp;

    @Test
    void aUsedBinaryIsMarkedRecentAndAMissingOneIsIgnored() throws Exception {
        Path f = Files.writeString(tmp.resolve("k.bin"), "x");
        Files.setLastModifiedTime(f, FileTime.fromMillis(1_000));
        ProgramBinaryStore.touch(f);
        assertTrue(Files.getLastModifiedTime(f).toMillis() > 1_000);
        ProgramBinaryStore.touch(tmp.resolve("gone.bin"));
    }

    @Test
    void anUnreadableBinaryIsAMissNotAnError() throws Exception {
        ProgramBinaryStore s = new ProgramBinaryStore(tmp.resolve("g"));
        s.store(
                "k",
                new ProgramBinaryStore.Entry(1, new byte[] {1, 2}, new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES]));
        Path f = s.file("k");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("---------"));
        try {
            ProgramBinaryStore.Lookup l = s.load("k");
            assertNull(l.entry());
            assertFalse(l.damaged(), "not deleted: it may be readable next time");
        } finally {
            Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void purgeTakesStaleLoadingMarkersToo() throws Exception {
        CacheDir.ensureOwned(tmp);
        Files.writeString(tmp.resolve("loading-12.marker"), "x");
        CacheDir.purge(tmp);
        assertFalse(Files.exists(tmp.resolve("loading-12.marker")));
    }

    @Test
    void aLockFileThatIsAFolderMeansShared() throws Exception {
        CacheDir.ensureOwned(tmp);
        Files.createDirectories(tmp.resolve(".lock"));
        assertNull(CacheDir.lock(tmp));
    }

    @Test
    void everyOffReasonHasWords() throws Exception {
        JsonObject lang;
        try (var in = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/assets/preheat/lang/en_us.json")),
                StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(in).getAsJsonObject();
        }
        for (CacheOff off : CacheOff.values()) {
            assertTrue(lang.has(off.key), off.name());
        }
        assertEquals(3, CacheOff.values().length);
    }
}
