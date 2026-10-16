package com.lewisgibson.preheat.config;

import com.lewisgibson.preheat.cache.CacheDir;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Where the shader cache lives. The default is the game folder's {@code preheat/shader-cache}. A folder the player
 * chooses is never used directly: Preheat makes its own {@link #CHILD} folder inside it and keeps everything there, so
 * nothing Preheat deletes can ever be the player's. A chosen folder is used only when it is usable (an absolute path
 * that exists or can be made, is a folder, and can be written to); one that is not falls back to the default with the
 * reason, never an error.
 */
public final class CacheFolder {
    /** The folder Preheat makes inside a folder the player chose. */
    public static final String CHILD = "preheat-shader-cache";

    private CacheFolder() {}

    /** The default cache folder. */
    public static Path instanceFolder(Path gameDir) {
        return gameDir.resolve("preheat").resolve("shader-cache");
    }

    /** Where the cache goes for a folder the player chose. */
    public static Path ownedChild(Path chosen) {
        return chosen.resolve(CHILD);
    }

    /** The folder to use and, when the chosen one could not be, why. */
    public record Resolved(Path folder, @Nullable FolderProblem problem) {
        public boolean custom(Path gameDir) {
            return !folder.equals(instanceFolder(gameDir));
        }
    }

    /** The cache folder for this setting ({@code ""} is the default). */
    public static Resolved resolve(Path gameDir, @Nullable String setting) {
        Path instance = instanceFolder(gameDir);
        if (setting == null || setting.isBlank()) {
            return new Resolved(instance, null);
        }
        Path custom;
        try {
            custom = Path.of(setting.trim());
        } catch (InvalidPathException e) {
            return new Resolved(instance, FolderProblem.INVALID);
        }
        FolderProblem problem = check(custom);
        return problem == null ? new Resolved(ownedChild(custom), null) : new Resolved(instance, problem);
    }

    /**
     * Null when the chosen {@code folder} can hold the cache (in its {@link #CHILD}), else why not. Creates the folder
     * when it is missing.
     */
    public static @Nullable FolderProblem check(Path folder) {
        if (!folder.isAbsolute()) {
            return FolderProblem.NOT_FULL_PATH;
        }
        try {
            if (Files.exists(folder) && !Files.isDirectory(folder)) {
                return FolderProblem.IS_A_FILE;
            }
            Path child = ownedChild(folder);
            if (Files.exists(child, LinkOption.NOFOLLOW_LINKS) && !CacheDir.isOwned(child) && !emptyFolder(child)) {
                return FolderProblem.NOT_OURS;
            }
            Files.createDirectories(folder);
            Path probe = folder.resolve(".preheat-write-test-" + UUID.randomUUID());
            Files.writeString(probe, "ok");
            Files.delete(probe);
            return null;
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            return FolderProblem.NOT_WRITABLE;
        }
    }

    private static boolean emptyFolder(Path p) throws IOException {
        if (!Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
            return !ds.iterator().hasNext();
        }
    }

    /** {@link #check} for a folder typed by the player: a name the file system refuses is a problem too. */
    public static @Nullable FolderProblem check(String typed) {
        try {
            return check(Path.of(typed.trim()));
        } catch (InvalidPathException e) {
            return FolderProblem.INVALID;
        }
    }
}
