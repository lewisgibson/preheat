package com.lewisgibson.preheat.cache;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The folder the cache lives in, and the only code that deletes or moves anything there. It never trusts the folder to
 * be Preheat's: a folder is Preheat's only when it carries the {@link #MARKER} file Preheat wrote when it made it, and
 * even then only Preheat's own entries are touched: generation folders named like {@link #GENERATION}, and the guard's
 * files. Links and junctions are never followed, so a link inside the folder can never lead a delete elsewhere.
 */
public final class CacheDir {
    /** Written into a folder Preheat made for its cache; nothing is deleted from a folder without it. */
    public static final String MARKER = ".preheat-cache";
    /** A generation folder's name: the first 16 hex digits of the generation's key. */
    public static final Pattern GENERATION = Pattern.compile("[0-9a-f]{16}");
    /** The guard's files, which live beside the generations. */
    static final List<String> GUARD_FILES = List.of(CacheGuard.PAUSED, CacheGuard.STRIKES, ".lock");

    private CacheDir() {}

    /** Makes {@code root} (and its parents) if needed and marks it as Preheat's, unless something else is there. */
    public static void ensureOwned(Path root) throws IOException {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && !isOwned(root)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                if (ds.iterator().hasNext()) {
                    throw new IOException(root + " already holds files that are not Preheat's");
                }
            }
        }
        Files.createDirectories(root);
        Path marker = root.resolve(MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            Files.writeString(
                    marker,
                    "This folder holds Preheat's saved shaders. Deleting it is safe.\n",
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Marks the default folder ({@code <game folder>/preheat/shader-cache}) as Preheat's even when it already has files:
     * that path has only ever been Preheat's, and caches written before the marker existed are adopted this way.
     */
    public static void adopt(Path root) throws IOException {
        Files.createDirectories(root);
        Path marker = root.resolve(MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            Files.writeString(
                    marker,
                    "This folder holds Preheat's saved shaders. Deleting it is safe.\n",
                    StandardCharsets.UTF_8);
        }
    }

    /** Whether {@code root} is a real folder (not a link) that Preheat marked as its own. */
    public static boolean isOwned(Path root) {
        return Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root.resolve(MARKER), LinkOption.NOFOLLOW_LINKS);
    }

    /** The generation folders in an owned root (real folders only, named like a generation). */
    public static List<Path> generations(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!isOwned(root)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
            for (Path p : ds) {
                if (GENERATION.matcher(p.getFileName().toString()).matches()
                        && Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /**
     * Deletes the generation folders other than {@code keep} from an owned root: binaries of another driver, Iris or
     * Preheat version, pack or setting are never used again. Returns how many were deleted.
     */
    public static int retireOthers(Path root, String keep) throws IOException {
        int n = 0;
        for (Path g : generations(root)) {
            if (!g.getFileName().toString().equals(keep)) {
                deleteTree(g);
                n++;
            }
        }
        return n;
    }

    /**
     * Empties an owned root of everything Preheat put there (generations and the guard's files), leaving the folder
     * and its marker. Anything else in it is left alone. Throws if something could not be deleted.
     */
    public static void purge(Path root) throws IOException {
        if (!isOwned(root)) {
            return;
        }
        for (Path g : generations(root)) {
            deleteTree(g);
        }
        for (String name : GUARD_FILES) {
            if (!name.equals(".lock")) {
                Files.deleteIfExists(root.resolve(name));
            }
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, "loading*.marker")) {
            for (Path p : ds) {
                Files.deleteIfExists(p);
            }
        }
    }

    /**
     * Moves Preheat's entries from owned root {@code from} into {@code to} (made and marked if needed): each file is
     * copied to a temporary name and renamed into place, and only once everything is across are the originals deleted.
     * If a copy fails, what was copied is removed and {@code from} is left as it was. Returns the files moved.
     */
    public static int move(Path from, Path to) throws IOException {
        if (!isOwned(from)
                || from.toAbsolutePath().normalize().equals(to.toAbsolutePath().normalize())) {
            return 0;
        }
        if (to.toAbsolutePath().normalize().startsWith(from.toAbsolutePath().normalize())) {
            throw new IOException(to + " is inside " + from);
        }
        ensureOwned(to);
        List<Path> sources = new ArrayList<>();
        for (Path g : generations(from)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(g)) {
                for (Path f : ds) {
                    if (ProgramBinaryStore.isCacheFile(f)) {
                        sources.add(f);
                    }
                }
            }
        }
        // In name order, so a move behaves the same on every file system (directory listings come back in any order).
        sources.sort(null);
        List<Path> copied = new ArrayList<>();
        try {
            for (Path src : sources) {
                Path dst = to.resolve(from.relativize(src).toString());
                Files.createDirectories(dst.getParent());
                Path tmp = dst.resolveSibling(dst.getFileName() + ".tmp");
                Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                copied.add(dst);
            }
        } catch (IOException | RuntimeException e) {
            for (Path p : copied) {
                Files.deleteIfExists(p);
            }
            throw e;
        }
        purge(from);
        return copied.size();
    }

    /** Deletes temporary files a crash left inside the generation folders; returns how many. */
    public static int deleteTemporaries(Path root) throws IOException {
        int deleted = 0;
        for (Path g : generations(root)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(g, "*.tmp")) {
                for (Path p : ds) {
                    if (Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
                        Files.deleteIfExists(p);
                        deleted++;
                    }
                }
            }
        }
        return deleted;
    }

    /**
     * Deletes a file or a folder and everything under it, without following links: a link or junction is removed
     * itself, never what it points at.
     */
    public static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(p, new TreeDeleter());
    }

    /** Deletes each file, then each folder once it is empty; a folder that could not be listed is left, and says so. */
    static final class TreeDeleter extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Takes this process's lock on an owned root, or returns null when another running game holds it. A root shared
     * with another game is used, but its generations are never retired or tidied from this one.
     */
    public static @Nullable FileLock lock(Path root) {
        return lock(root, FileChannel::tryLock);
    }

    /** {@link #lock}, as the channel that holds it: closing the channel lets the lock go. */
    public static @Nullable Closeable lockChannel(Path root) {
        FileLock l = lock(root);
        return l == null ? null : l.channel();
    }

    /** How a lock is tried: {@link FileChannel#tryLock}, null when another process holds it. */
    interface Locker {
        @Nullable
        FileLock tryLock(FileChannel channel) throws IOException;
    }

    static @Nullable FileLock lock(Path root, Locker locker) {
        try {
            if (!isOwned(root)) {
                return null;
            }
            FileChannel ch =
                    FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = locker.tryLock(ch);
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException e) {
                // Held by this same game already (an earlier folder object): treated as shared.
            }
            ch.close();
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
