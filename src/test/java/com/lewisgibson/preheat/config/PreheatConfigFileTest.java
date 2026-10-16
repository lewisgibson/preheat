package com.lewisgibson.preheat.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The settings in use: read once, replaced and written by save, extended by discovery. */
class PreheatConfigFileTest {
    @TempDir
    Path tmp;

    private Path file;

    @BeforeEach
    void useTempFile() {
        file = tmp.resolve("config").resolve("preheat.json");
        PreheatConfig.useFile(file);
    }

    @AfterEach
    void reset() {
        PreheatConfig.useFile(null);
    }

    @Test
    void getReadsOnceAndSaveWritesAndReplaces() throws Exception {
        assertEquals(file, PreheatConfig.file());
        PreheatConfig first = PreheatConfig.get();
        assertSame(first, PreheatConfig.get(), "read once");
        assertFalse(Files.exists(file), "reading never writes");

        PreheatConfig next = first.copy();
        next.enabled = false;
        PreheatConfig.save(next);
        assertSame(next, PreheatConfig.get());
        assertTrue(Files.readString(file).contains("\"enabled\": false"));

        PreheatConfig.useFile(file);
        assertFalse(PreheatConfig.get().enabled, "read back from disk");
    }

    @Test
    void discoveryWritesOnlyWhenADimensionIsNew() throws Exception {
        PreheatConfig.discoverAndSave(List.of(KnownDimensions.NETHER));
        assertFalse(Files.exists(file), "nothing new, nothing written");
        PreheatConfig.discoverAndSave(List.of("aether:the_aether"));
        assertTrue(Files.readString(file).contains("aether:the_aether"));
        assertTrue(PreheatConfig.get().dimensions.get("aether:the_aether"));
    }

    @Test
    void aSaveThatCannotWriteKeepsTheSettingsInMemory() throws Exception {
        Files.createDirectories(tmp.resolve("blocked"));
        Files.writeString(tmp.resolve("blocked").resolve("config"), "a file where the folder must go");
        PreheatConfig.useFile(tmp.resolve("blocked").resolve("config").resolve("preheat.json"));
        PreheatConfig next = PreheatConfig.get().copy();
        next.cacheFolder = "/elsewhere";
        PreheatConfig.save(next);
        assertEquals("/elsewhere", PreheatConfig.get().cacheFolder);
    }

    @Test
    void aFileOfJsonNullMeansDefaults() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "null");
        PreheatConfig c = PreheatConfig.load(file);
        assertTrue(c.enabled);
        assertEquals(3, c.dimensions.size());
    }

    @Test
    void withoutAFileOfItsOwnTheSettingsLiveInTheLoadersConfigFolder() {
        PreheatConfig.useFile(null);
        Platforms.use(FakePlatform.client(tmp));
        try {
            assertEquals(tmp.resolve("config").resolve("preheat.json"), PreheatConfig.file());
        } finally {
            Platforms.use(null);
        }
    }

    @Test
    // Holding the settings' own lock is the point: the reader has to wait on it as it would on another thread's load.
    @SuppressWarnings("LockOnNonEnclosingClassLiteral")
    void aReadThatWaitedForAnotherUsesThatOnesSettings() throws Exception {
        PreheatConfig saved = new PreheatConfig().normalize();
        java.util.concurrent.atomic.AtomicReference<PreheatConfig> got =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread reader = new Thread(() -> got.set(PreheatConfig.get()));
        synchronized (PreheatConfig.class) {
            reader.start();
            while (reader.getState() != Thread.State.BLOCKED) {
                Thread.onSpinWait();
            }
            PreheatConfig.save(saved);
        }
        reader.join();
        assertSame(saved, got.get());
    }

    @Test
    void aFileWithEveryListNullGetsTheDefaults() throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"dimensions\": null, \"cacheFolder\": null, \"neverKeep\": null}");
        PreheatConfig c = PreheatConfig.get();
        assertEquals(java.util.Set.copyOf(KnownDimensions.VANILLA), c.dimensions.keySet());
        assertEquals("", c.cacheFolder);
        assertEquals(DimensionPolicy.NEVER_KEEP, c.neverKeep);
    }

    @Test
    void aJoinListsTheServersStartupDimensionsAndTheOneEntered() throws Exception {
        PreheatConfig.discoverAndSave(
                List.of(KnownDimensions.OVERWORLD, "aether:the_aether"),
                "aether:the_aether",
                java.util.Set.of("aether:the_aether"));
        assertTrue(PreheatConfig.get().dimensions.containsKey("aether:the_aether"));
        assertTrue(Files.readString(file).contains("aether:the_aether"));
    }
}
