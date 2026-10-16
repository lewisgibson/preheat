package com.lewisgibson.preheat.progress;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/**
 * How many programs each shader pack's dimension had the last time it was built, so the loading screen can say
 * "34/101" rather than only "34". One small properties file; nothing breaks without it.
 */
public final class BuildCounts {
    private final Path file;
    private final Properties counts = new Properties();

    public BuildCounts(Path file) {
        this.file = file;
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                counts.load(r);
            } catch (IOException | IllegalArgumentException ignored) {
                // Unreadable: every count is unknown until the next build writes it again.
            }
        }
    }

    static String key(String pack, String dimension) {
        return pack + "|" + dimension;
    }

    /** The count from last time, or null. */
    public synchronized @Nullable Integer get(String pack, String dimension) {
        String v = counts.getProperty(key(pack, dimension));
        try {
            return v == null ? null : Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Records a count and writes the file. */
    public synchronized void put(String pack, String dimension, int programs) {
        String k = key(pack, dimension);
        if (String.valueOf(programs).equals(counts.getProperty(k))) {
            return;
        }
        counts.setProperty(k, String.valueOf(programs));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                counts.store(w, "Preheat: programs per shader pack and dimension, for the loading screen");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // Only the loading screen's "of N" is lost.
        }
    }
}
