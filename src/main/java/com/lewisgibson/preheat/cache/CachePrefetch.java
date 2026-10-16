package com.lewisgibson.preheat.cache;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Reads the saved shaders (binaries and patches) into memory and checks them while the game is still loading, so the
 * startup builds find them there instead of opening, reading and checking every file on the render thread. Measured on Windows with an NVIDIA
 * card, that reading was half of loading a dimension from the cache (283 of 564 ms for the Overworld), most of it
 * spent opening files.
 *
 * <p>It cannot know the generation in use before the game has a graphics context, so it reads the generation holding
 * the most recently used file (normally the only one), most recently used files first, up to a budget. A lookup only
 * ever matches the folder and key it was read from, so reading the wrong generation costs memory for a while and
 * nothing else. Files that fail their checks are left on disk for the normal path to find and delete. Nothing here
 * writes or deletes anything.
 *
 * <p>No GL and no game classes, because it starts while mods are still being loaded.
 */
public final class CachePrefetch {
    /** The most read ahead; one shader pack's three dimensions are about 40 MB. */
    public static final long BUDGET_BYTES = 256L << 20;

    private static final int THREADS = 4;

    /** This game's read-ahead, once {@link #start} has finished reading; null before, or with nothing read. */
    private static volatile @Nullable CachePrefetch current;

    private static volatile boolean stopped;

    /** How long what was read is held if the startup builds never come to release it. */
    private static final long HOLD_MS = 10 * 60_000L;

    private final Path generation;
    private final Map<String, ProgramBinaryStore.Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Map<String, @Nullable String>> patches = new ConcurrentHashMap<>();
    private final AtomicLong bytes = new AtomicLong();
    private volatile boolean released;
    private long nanos;

    private final long budget;

    private CachePrefetch(Path generation, long budget) {
        this.generation = generation;
        this.budget = budget;
    }

    /**
     * Starts reading ahead on a background thread. {@code root} is asked for the cache folder on that thread (working it
     * out reads the settings and checks the folder), and {@code done} hears what was read.
     */
    public static void start(Supplier<Path> root, Consumer<Stats> done) {
        start(root, done, CompletableFuture.delayedExecutor(HOLD_MS, TimeUnit.MILLISECONDS));
    }

    /** As {@link #start(Supplier, Consumer)}, letting go of what was read through {@code later}; returns the thread. */
    static Thread start(Supplier<Path> root, Consumer<Stats> done, Executor later) {
        stopped = false;
        Thread t = new Thread(() -> readAhead(root, done, later), "Preheat cache read-ahead");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void readAhead(Supplier<Path> root, Consumer<Stats> done, Executor later) {
        try {
            CachePrefetch p = read(root.get(), BUDGET_BYTES);
            if (p == null) {
                return;
            }
            current = p;
            if (stopped) {
                // Released while this was reading or being published: nothing may be held after a release.
                releaseAll();
                return;
            }
            done.accept(p.stats());
            later.execute(() -> {
                if (current == p) {
                    releaseAll();
                }
            });
        } catch (RuntimeException | LinkageError e) {
            // Then nothing is read ahead, and every build reads its files as it always has.
        }
    }

    /** The entry read ahead for {@code key} in generation folder {@code dir}, or null; see {@link #take(Path, String)}. */
    public static ProgramBinaryStore.@Nullable Entry taken(Path dir, String key) {
        CachePrefetch p = current;
        return p == null ? null : p.take(dir, key);
    }

    /** The patch read ahead for {@code key} in generation folder {@code dir}, or null; handed over once. */
    public static @Nullable Map<String, @Nullable String> takenPatch(Path dir, String key) {
        CachePrefetch p = current;
        return p == null ? null : p.takePatch(dir, key);
    }

    /** Lets go of everything read ahead (the startup builds are done, or the cache changed). */
    public static void releaseAll() {
        stopped = true;
        CachePrefetch p = current;
        current = null;
        if (p != null) {
            p.release();
        }
    }

    /** The generation folder read from. */
    public Path generation() {
        return generation;
    }

    /** Entries held, bytes held (binaries as read, patches unpacked), and how long reading took. */
    public record Stats(int entries, long bytes, long nanos) {}

    public Stats stats() {
        return new Stats(entries.size() + patches.size(), bytes.get(), nanos);
    }

    /**
     * Reads ahead from {@code root}: the generation holding the most recently used binary, newest first, up to {@code
     * budget} bytes, counted as held in memory (a patch is held unpacked, several times its file). Returns null when
     * there is nothing to read.
     */
    public static @Nullable CachePrefetch read(Path root, long budget) {
        return read(root, budget, THREADS);
    }

    /** As {@link #read(Path, long)} on {@code threads} threads. */
    static @Nullable CachePrefetch read(Path root, long budget, int threads) {
        long t0 = System.nanoTime();
        Newest newest = newestGeneration(root);
        if (newest == null) {
            return null;
        }
        List<Candidate> files = newest.files();
        CachePrefetch p = new CachePrefetch(newest.generation(), budget);
        List<Callable<@Nullable Void>> work = new ArrayList<>();
        long total = 0;
        for (Candidate c : files) {
            if (total + c.size() > budget) {
                break;
            }
            total += c.size();
            work.add(() -> {
                p.readOne(c.file());
                return null;
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Preheat cache read-ahead");
            t.setDaemon(true);
            return t;
        });
        try {
            // invokeAll only notices an interrupt while a read is still running, so an interrupted caller is checked
            // first and reads nothing.
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            // Each read catches its own failures: one unreadable file is read again by the normal path at its build.
            pool.invokeAll(work);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
        p.nanos = System.nanoTime() - t0;
        return p;
    }

    /**
     * Reads one file. Nothing else can see this read-ahead until {@link #read} returns it, so nothing can release it
     * meanwhile; only the budget can stop a read.
     */
    private void readOne(Path file) {
        if (bytes.get() >= budget) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            if (name.endsWith(PatchedSources.SUFFIX)) {
                Map<String, @Nullable String> m = PatchedSources.decode(data);
                if (m != null) {
                    patches.put(name.substring(0, name.length() - PatchedSources.SUFFIX.length()), m);
                    long held = 0;
                    for (String v : m.values()) {
                        held += v == null ? 0 : v.length();
                    }
                    bytes.addAndGet(held);
                }
                return;
            }
            ProgramBinaryStore.Entry e = ProgramBinaryStore.decode(data);
            if (e != null) {
                entries.put(name.substring(0, name.length() - ProgramBinaryStore.SUFFIX.length()), e);
                bytes.addAndGet(data.length);
            }
        } catch (IOException | RuntimeException ignored) {
            // Read again, and dealt with, by the normal path.
        }
    }

    /** The entry read ahead for {@code key} in generation folder {@code dir}, handed over once; null when there is none. */
    public ProgramBinaryStore.@Nullable Entry take(Path dir, String key) {
        if (released || !dir.equals(generation)) {
            return null;
        }
        return entries.remove(key);
    }

    /** The patch read ahead for {@code key} in generation folder {@code dir}, handed over once; null when there is none. */
    public @Nullable Map<String, @Nullable String> takePatch(Path dir, String key) {
        if (released || !dir.equals(generation)) {
            return null;
        }
        return patches.remove(key);
    }

    /** Lets go of everything read; later lookups read from disk. */
    public void release() {
        released = true;
        entries.clear();
        patches.clear();
    }

    private record Candidate(Path file, long modified, long size) {}

    private record Newest(Path generation, List<Candidate> files) {}

    /** The generation holding the most recently used binary, and its binaries, most recently used first. */
    private static @Nullable Newest newestGeneration(Path root) {
        Newest best = null;
        long bestModified = Long.MIN_VALUE;
        try {
            for (Path g : CacheDir.generations(root)) {
                List<Candidate> files = new ArrayList<>();
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(g)) {
                    for (Path f : ds) {
                        if (ProgramBinaryStore.isCacheFile(f)) {
                            files.add(new Candidate(
                                    f,
                                    Files.getLastModifiedTime(f, LinkOption.NOFOLLOW_LINKS)
                                            .toMillis(),
                                    Files.size(f)));
                        }
                    }
                }
                files.sort(Comparator.comparingLong(Candidate::modified).reversed());
                if (!files.isEmpty() && files.get(0).modified() > bestModified) {
                    bestModified = files.get(0).modified();
                    best = new Newest(g, files);
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return best;
    }
}
