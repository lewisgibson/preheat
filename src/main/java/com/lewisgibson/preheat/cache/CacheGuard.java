package com.lewisgibson.preheat.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.function.LongPredicate;
import org.jspecify.annotations.Nullable;

/**
 * The crash guard. Each game writes its own marker file ({@code loading-<pid>.marker}, naming the generation) before
 * the first cached binary of a build goes to the driver, and removes it when the loads are done or the game exits
 * normally. A launch that finds a marker from a game that is no longer running knows that game stopped while loading
 * cached shaders: a driver crash, or simply the game being stopped from the launcher at that moment.
 *
 * <p>The first such stop deletes that generation, so its shaders are prepared and saved fresh, and the cache carries
 * on. A second stop in a row on the same generation pauses the cache. A clean load clears the strike, and deleting the
 * cache (the settings button, or the folder) clears a pause. Markers of other games still running are left alone, so
 * two games sharing a folder never strike each other.
 *
 * <p>Files hold reason codes, never prose: the screens and the log word them.
 */
public final class CacheGuard {
    static final String LEGACY_LOADING = "loading.marker";
    static final String STRIKES = "strikes";
    static final String PAUSED = "paused";
    /** Unclean stops in a row on one generation that pause the cache. */
    static final int STRIKES_TO_PAUSE = 2;
    /** The reason code in {@link #PAUSED}. */
    public static final String STOPPED_WHILE_LOADING = "stopped-while-loading";

    private final Path root;
    private final long pid;
    private final LongPredicate running;

    public CacheGuard(Path root) {
        this(
                root,
                ProcessHandle.current().pid(),
                p -> ProcessHandle.of(p).map(ProcessHandle::isAlive).orElse(false));
    }

    CacheGuard(Path root, long pid, LongPredicate running) {
        this.root = root;
        this.pid = pid;
        this.running = running;
    }

    private Path marker() {
        return root.resolve("loading-" + pid + ".marker");
    }

    /**
     * Called once per launch before the cache is used: each marker a stopped game left is a strike. Returns the pause's
     * reason code, or null when the cache may run.
     */
    public @Nullable String checkAtStartup() {
        try {
            if (Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, "loading*.marker")) {
                    for (Path m : ds) {
                        String name = m.getFileName().toString();
                        if (!name.equals(LEGACY_LOADING)) {
                            long owner = Long.parseLong(
                                    name.substring("loading-".length(), name.length() - ".marker".length()));
                            if (owner != pid && running.test(owner)) {
                                continue;
                            }
                        }
                        String generation = Files.readString(m, StandardCharsets.UTF_8)
                                .lines()
                                .findFirst()
                                .orElse("")
                                .trim();
                        Files.deleteIfExists(m);
                        strike(generation);
                    }
                }
            }
            Path paused = root.resolve(PAUSED);
            return Files.exists(paused)
                    ? Files.readString(paused, StandardCharsets.UTF_8).trim()
                    : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void strike(String generation) throws IOException {
        Strikes last = strikes();
        int count = last.generation().equals(generation) ? last.count() + 1 : 1;
        if (count >= STRIKES_TO_PAUSE) {
            Files.writeString(root.resolve(PAUSED), STOPPED_WHILE_LOADING, StandardCharsets.UTF_8);
            Files.deleteIfExists(root.resolve(STRIKES));
            return;
        }
        Files.writeString(root.resolve(STRIKES), generation + " " + count, StandardCharsets.UTF_8);
        // Start that generation again: whatever was being loaded is never handed to the driver a second time.
        if (CacheDir.GENERATION.matcher(generation).matches() && CacheDir.isOwned(root)) {
            CacheDir.deleteTree(root.resolve(generation));
        }
    }

    /** The last strike: which generation, and how many in a row. */
    record Strikes(String generation, int count) {}

    Strikes strikes() {
        try {
            Path f = root.resolve(STRIKES);
            if (!Files.exists(f)) {
                return new Strikes("", 0);
            }
            String text = Files.readString(f, StandardCharsets.UTF_8).trim();
            int space = text.indexOf(' ');
            return space < 0
                    ? new Strikes("", 0)
                    : new Strikes(text.substring(0, space), Integer.parseInt(text.substring(space + 1)));
        } catch (IOException | NumberFormatException e) {
            return new Strikes("", 0);
        }
    }

    /** About to hand cached binaries of {@code generation} to the driver, for {@code what} (a dimension). */
    public void arm(String generation, String what) {
        try {
            Files.createDirectories(root);
            Files.writeString(marker(), generation + "\n" + what + "\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Without the marker a crash goes unnoticed, which is how it was before the guard existed.
        }
    }

    /** The loads finished cleanly: this game's marker and the strike go. */
    public void disarm() {
        try {
            Files.deleteIfExists(marker());
            Files.deleteIfExists(root.resolve(STRIKES));
        } catch (IOException ignored) {
            // Left behind, it counts one strike next launch; deleting the cache clears it.
        }
    }
}
