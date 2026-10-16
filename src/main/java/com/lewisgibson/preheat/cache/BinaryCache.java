package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.config.CacheFolder;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.gpu.CachePolicy;
import com.lewisgibson.preheat.gpu.GpuInfo;
import com.lewisgibson.preheat.platform.Platform;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

/**
 * The shader cache: load a linked program from its binary instead of compiling it, and save the binary of a program
 * that was compiled. Render thread only; files are written by one background thread. {@link ProgramBinaries} is the
 * game's one, on the real driver.
 *
 * <p>A cached binary reaches the driver only after its file passed its checksum, and is used only if the driver
 * accepts it without a GL error, reports it linked, and the program it restored exposes the same attributes and
 * uniforms as the one that was compiled. Anything else compiles as Iris always does, and the file is deleted. After
 * {@link CachePolicy#MAX_REFUSALS} refusals in one session the cache stops loading until the next launch.
 *
 * <p>A generation is everything that makes every binary in it stale at once: the GPU, driver and GLSL strings and the
 * Iris and Preheat versions. The first binary written in a new generation deletes every other generation. Packs and
 * their settings share a generation (each program's key holds its final source), and a hard size limit removes the
 * binaries used longest ago if it is ever reached.
 */
public final class BinaryCache {
    private static final int GL_PROGRAM_BINARY_RETRIEVABLE_HINT = 0x8257;
    private static final int GL_NUM_PROGRAM_BINARY_FORMATS = 0x87FE;
    private static final int GL_PROGRAM_BINARY_FORMATS = 0x87FF;

    /** What the driver said when the cache first looked: decided once per launch, on the render thread. */
    private record Driver(List<Integer> formats, GpuInfo gpu, CachePolicy policy, List<String> context) {}

    /** The generation in use in the current folder: its id and its store. */
    private record Generation(String id, ProgramBinaryStore store) {}

    /** The cache folder in use and its state; replaced whenever the folder setting changes. */
    private static final class Folder {
        final Path root;
        final CacheGuard guard;
        /** This game's lock on the folder; null when another running game holds it (the folder is then shared). */
        final @Nullable Closeable lock;

        volatile @Nullable String paused;

        // Reset by a delete on the writer thread, read on the render thread.
        volatile @Nullable Generation generation;

        volatile boolean retired;

        Folder(Path root, CacheGuard guard, @Nullable Closeable lock, @Nullable String paused) {
            this.root = root;
            this.guard = guard;
            this.lock = lock;
            this.paused = paused;
        }

        /** Another running game uses this folder too: its generations are not retired or tidied from here. */
        boolean shared() {
            return lock == null;
        }

        void release() {
            Closeable l = lock;
            if (l != null) {
                try {
                    l.close();
                } catch (IOException ignored) {
                    // Released when the game exits anyway.
                }
            }
        }
    }

    private final ProgramGl gl;
    private final DriverGl driverGl;
    private final Executor writer;
    private final LongSupplier clock;
    private final BuildTimer timer;
    private final long capBytes;
    private final @Nullable String debugMisses;
    private final Function<Path, @Nullable Closeable> lock;

    private @Nullable Driver driver;
    private volatile @Nullable Folder folder;
    private final AtomicInteger refusals = new AtomicInteger();
    private final PendingSaves pending = new PendingSaves();

    // This build's numbers.
    private int hits;
    private int misses;
    private int refused;
    private int damaged;
    private int unlinked;
    private int noBinary;
    private int verified;
    private long verifyNanos;
    private long verifyBytes;
    private int patchHits;
    private int patchMisses;
    private boolean armed;

    private @Nullable String resolvedFor;
    private CacheFolder.@Nullable Resolved resolvedCache;

    /**
     * @param gl the calls on a program
     * @param driverGl the calls on the driver
     * @param writer runs every file write, in order, off the render thread
     * @param clock nanoseconds
     * @param timer the build timer the loads and saves are charged to
     * @param capBytes the most the cache may hold ({@link ProgramBinaryStore#HARD_CAP_BYTES})
     * @param debugMisses a folder to write each missed program's key parts to, or null
     * @param lock takes this game's lock on a cache folder, or null when another game holds it ({@link
     *     CacheDir#lockChannel})
     */
    public BinaryCache(
            ProgramGl gl,
            DriverGl driverGl,
            Executor writer,
            LongSupplier clock,
            BuildTimer timer,
            long capBytes,
            @Nullable String debugMisses,
            Function<Path, @Nullable Closeable> lock) {
        this.gl = gl;
        this.driverGl = driverGl;
        this.writer = writer;
        this.clock = clock;
        this.timer = timer;
        this.capBytes = capBytes;
        this.debugMisses = debugMisses;
        this.lock = lock;
    }

    /** The game is exiting: a build it was loading in is not a crash. */
    public void shutdown() {
        Folder f = folder;
        if (f != null) {
            f.guard.disarm();
        }
    }

    /** The cache folder the settings point at, or the default when the chosen one cannot be used. */
    public Path root() {
        return resolved().folder();
    }

    /** Why the custom cache folder is not in use (and the instance's is), or null. */
    public @Nullable FolderProblem folderProblem() {
        return resolved().problem();
    }

    /** Worked out once per setting (checking a folder writes a probe file), and again when the setting changes. */
    private synchronized CacheFolder.Resolved resolved() {
        String setting = PreheatConfig.get().cacheFolder;
        CacheFolder.Resolved r = resolvedCache;
        if (r == null || !setting.equals(resolvedFor)) {
            r = CacheFolder.resolve(Platform.get().gameDir(), setting);
            resolvedCache = r;
            resolvedFor = setting;
        }
        return r;
    }

    /** The GPU as GL reports it, once the cache has looked (null before the first build). */
    public @Nullable GpuInfo gpu() {
        Driver d = driver;
        return d == null ? null : d.gpu();
    }

    /** Why the cache cannot run on this driver or this session, or null when it can (or has not looked yet). */
    public @Nullable CacheOff whyOff() {
        Folder f = folder;
        if (f != null && f.paused != null) {
            return CacheOff.PAUSED;
        }
        Driver d = driver;
        if (d != null && !d.policy().allowed()) {
            return CacheOff.UNSUPPORTED_DRIVER;
        }
        if (refusals.get() >= CachePolicy.MAX_REFUSALS) {
            return CacheOff.REFUSED;
        }
        return null;
    }

    /** Whether the cache runs now; the first call (on the render thread, with a GL context) looks at the driver. */
    public boolean usable() {
        if (!Features.on(Feature.PROGRAM_CACHE)) {
            return false;
        }
        Driver d = driver();
        Folder f = folder();
        if (!d.policy().allowed() || f.paused != null) {
            return false;
        }
        refreshGeneration(d, f);
        return true;
    }

    private Driver driver() {
        Driver d = driver;
        if (d == null) {
            d = readDriver();
            driver = d;
            log(d, folder());
        }
        return d;
    }

    private Driver readDriver() {
        List<Integer> formats = new ArrayList<>();
        if (driverGl.programBinarySupported()) {
            int n = driverGl.integer(GL_NUM_PROGRAM_BINARY_FORMATS);
            if (n > 0) {
                int[] values = new int[n];
                driverGl.integers(GL_PROGRAM_BINARY_FORMATS, values);
                for (int v : values) {
                    formats.add(v);
                }
            }
        }
        GpuInfo gpu = GpuInfo.of(
                driverGl.string(GL11C.GL_VENDOR),
                driverGl.string(GL11C.GL_RENDERER),
                driverGl.string(GL11C.GL_VERSION),
                driverGl.string(GL20C.GL_SHADING_LANGUAGE_VERSION));
        List<String> context = List.of(
                "preheat-program-binary-v2 " + Preheat.version(),
                "iris " + Platform.get().modVersion("iris").orElse("?"),
                gpu.vendor(),
                gpu.renderer(),
                gpu.version(),
                gpu.glsl());
        return new Driver(List.copyOf(formats), gpu, CachePolicy.decide(gpu, formats.size()), context);
    }

    private static void log(Driver d, Folder f) {
        String formatsHex = d.formats().stream()
                .map(v -> "0x" + Integer.toHexString(v))
                .reduce((a, b) -> a + " " + b)
                .orElse("none");
        Preheat.LOG.info(
                "PREHEAT GPU: {}; {} hardware, {} driver; {} program binary formats ({})",
                d.gpu().describe(),
                d.gpu().hardware(),
                d.gpu().driver(),
                d.formats().size(),
                formatsHex);
        if (!d.policy().allowed()) {
            Preheat.LOG.info("PREHEAT Shader cache off: {}", d.policy().reason());
        } else if (f.paused != null) {
            Preheat.LOG.warn("PREHEAT Shader cache paused: {}", f.paused);
        } else {
            Preheat.LOG.info(
                    "PREHEAT Shader cache at {}: {}", f.root, d.policy().note());
        }
    }

    private Folder folder() {
        Folder f = folder;
        if (f == null) {
            f = openFolder();
            folder = f;
        }
        return f;
    }

    private Folder openFolder() {
        CacheFolder.Resolved r = resolved();
        FolderProblem problem = r.problem();
        if (problem != null) {
            Preheat.LOG.warn(
                    "PREHEAT Shader cache: the chosen folder {} cannot be used ({}); using {} instead",
                    PreheatConfig.get().cacheFolder,
                    problem,
                    r.folder());
        }
        Path root = r.folder();
        try {
            if (r.custom(Platform.get().gameDir())) {
                CacheDir.ensureOwned(root);
            } else {
                CacheDir.adopt(root);
            }
        } catch (IOException e) {
            Preheat.LOG.warn("PREHEAT Shader cache: could not prepare {}", root, e);
        }
        Closeable held = lock.apply(root);
        if (held == null) {
            Preheat.LOG.info("PREHEAT Shader cache: another running game uses {} too; sharing it", root);
        }
        CacheGuard guard = new CacheGuard(root);
        return new Folder(root, guard, held, guard.checkAtStartup());
    }

    /** The cache folder setting changed: the next build uses the new folder. */
    public void settingsChanged() {
        CachePrefetch.releaseAll();
        Folder f = folder;
        if (f != null && !root().equals(f.root)) {
            f.release();
            folder = openFolder();
        }
    }

    /**
     * Makes {@code target} ({@code ""} for the default) the cache folder: saves the setting and switches to it in one
     * step. Called on the render thread once {@link #changeFolder} has moved what was saved, so no build can see the
     * new setting with the old folder.
     */
    public void useFolder(String target) {
        PreheatConfig next = PreheatConfig.get().copy();
        next.cacheFolder = target.trim();
        PreheatConfig.save(next);
        settingsChanged();
    }

    /**
     * Picks the generation: one per driver, Iris and Preheat version. The pack, its settings and Iris's settings are not
     * part of it, because each program's key already holds its final source, so switching packs or moving a slider
     * never empties the cache; packs share it, and the size limit removes what was used longest ago.
     */
    private static void refreshGeneration(Driver d, Folder f) {
        if (f.generation == null) {
            String id = ProgramBinaryStore.key(d.context()).substring(0, 16);
            f.generation = new Generation(id, new ProgramBinaryStore(f.root.resolve(id)));
            f.retired = false;
        }
    }

    /** The key of a program: the kind of program, its attribute bindings and its stages' final sources. */
    public String key(String kind, List<String> bindingsAndSources) {
        List<String> parts = new ArrayList<>(driver().context());
        parts.add(kind);
        for (String part : bindingsAndSources) {
            parts.add(StableSource.of(part));
        }
        return ProgramBinaryStore.key(parts);
    }

    /**
     * The key of a program about to be compiled, or null when the cache is not in use now. Its parts after the driver
     * context ({@code first}, worked out only when the cache is in use, then {@code stages}) are added to {@code
     * parts}, for {@link #debugMiss}.
     */
    public @Nullable String keyFor(String kind, Supplier<String> first, List<String> stages, List<String> parts) {
        if (!usable()) {
            return null;
        }
        parts.add(first.get());
        parts.addAll(stages);
        return key(kind, parts);
    }

    /** A gbuffer program's attribute part: the vertex format's element names in order, and whether it is Iris's fallback. */
    public static String attributes(List<String> elementNames, boolean fallback) {
        StringBuilder attributes = new StringBuilder();
        for (String e : elementNames) {
            attributes.append(e).append(',');
        }
        return "attributes " + attributes + " fallback " + fallback;
    }

    /**
     * With a debug folder ({@code -Dpreheat.debugCacheMisses=<folder>}), each program the cache did not have is
     * written there as {@code <name>-<key>.txt} with every part of its key, so two launches can be compared to find a
     * key that changes.
     */
    public void debugMiss(String name, String key, List<String> parts) {
        String folderName = debugMisses;
        if (folderName == null) {
            return;
        }
        try {
            Path dir = Path.of(folderName);
            Files.createDirectories(dir);
            String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
            Files.writeString(
                    dir.resolve(safe + "-" + key.substring(0, 12) + ".txt"),
                    String.join("\n=====\n", parts),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Preheat.LOG.warn("PREHEAT Could not write the cache-miss debug file for {}", name, e);
        }
    }

    /** A build begins: its numbers start from zero. */
    public void beginBuild() {
        // A save queued by an earlier build that never ended must not be matched against this build's programs.
        pending.clear();
        hits = misses = refused = damaged = verified = unlinked = noBinary = patchHits = patchMisses = 0;
        verifyNanos = verifyBytes = 0;
        armed = false;
    }

    /** A new linked program loaded from the cache, or 0 when there is none or the driver refuses it. */
    public int load(String key) {
        Folder f = folder();
        Generation g = f.generation;
        if (refusals.get() >= CachePolicy.MAX_REFUSALS || g == null) {
            misses++;
            return 0;
        }
        timer.enter(BuildTimer.Step.BINARY_LOAD);
        try {
            long t0 = clock.getAsLong();
            ProgramBinaryStore.Entry ahead = CachePrefetch.taken(g.store().dir(), key);
            ProgramBinaryStore.Lookup found = ahead != null
                    ? new ProgramBinaryStore.Lookup(ahead, false, ProgramBinaryStore.HEADER + (long) ahead.length())
                    : g.store().load(key);
            verifyNanos += clock.getAsLong() - t0;
            verifyBytes += found.bytes();
            if (found.damaged()) {
                damaged++;
            }
            ProgramBinaryStore.Entry e = found.entry();
            if (e != null) {
                verified++;
            }
            if (e == null || !driver().formats().contains(e.format())) {
                misses++;
                return 0;
            }
            if (!armed) {
                armed = true;
                f.guard.arm(g.id(), String.valueOf(timer.label()));
            }
            BinaryChecks.Loaded loaded = BinaryChecks.load(gl, e);
            int program = loaded.program();
            String why = loaded.refusal();
            if (why != null) {
                g.store().delete(key);
                refused++;
                misses++;
                // Render thread only, and no load is tried once the limit is reached, so this is the last refusal.
                int n = refusals.incrementAndGet();
                if (n < CachePolicy.MAX_REFUSALS) {
                    Preheat.LOG.info("PREHEAT The driver refused a cached shader ({}); compiling it instead", why);
                } else {
                    Preheat.LOG.warn(
                            "PREHEAT The driver refused {} cached shaders this session (last: {}); compiling instead until the next launch",
                            n,
                            why);
                }
                return 0;
            }
            hits++;
            Path used = g.store().file(key);
            writer.execute(() -> ProgramBinaryStore.touch(used));
            return program;
        } finally {
            timer.exit(BuildTimer.Step.BINARY_LOAD);
        }
    }

    /** Asks the driver to keep the binary of a program about to be linked. */
    public void retrievable(int program) {
        Folder f = folder;
        Driver d = driver;
        if (f != null && d != null && d.policy().allowed() && f.paused == null && Features.on(Feature.PROGRAM_CACHE)) {
            driverGl.programParameter(program, GL_PROGRAM_BINARY_RETRIEVABLE_HINT, GL11C.GL_TRUE);
        }
    }

    /** Saves a linked program's binary (read from the driver here, written to disk in the background). */
    public void save(int program, String key) {
        Folder f = folder();
        Generation g = f.generation;
        if (g == null) {
            return;
        }
        timer.enter(BuildTimer.Step.BINARY_SAVE);
        try {
            BinaryChecks.Captured captured = BinaryChecks.capture(gl, program);
            // An entry comes with SAVED and only with it.
            ProgramBinaryStore.Entry entry = captured.entry();
            if (entry != null) {
                write(f, g, "a binary", () -> g.store().store(key, entry));
            } else if (captured.outcome() == BinaryChecks.Outcome.NOT_LINKED) {
                unlinked++;
            } else {
                noBinary++;
            }
        } finally {
            timer.exit(BuildTimer.Step.BINARY_SAVE);
        }
    }

    /** A write to the generation, on the writer thread; the first write in a new generation retires the others. */
    private interface Write {
        void run() throws IOException;
    }

    private void write(Folder f, Generation g, String what, Write w) {
        boolean retire = !f.retired && !f.shared();
        f.retired = true;
        Path root = f.root;
        writer.execute(() -> {
            try {
                if (retire) {
                    int n = CacheDir.retireOthers(root, g.id());
                    if (n > 0) {
                        Preheat.LOG.info("PREHEAT Shader cache: deleted {} older cache generation(s)", n);
                    }
                }
                w.run();
            } catch (IOException | RuntimeException e) {
                Preheat.LOG.warn("PREHEAT Shader cache: could not write {}", what, e);
            }
        });
    }

    /** The generation folder when the cache runs now ({@link #usable}), or null when it does not. */
    public @Nullable Path usableDir() {
        if (!usable()) {
            return null;
        }
        return generationDir();
    }

    /** The generation folder in use, or null before the first build has looked at the driver. */
    public @Nullable Path generationDir() {
        Folder f = folder;
        Generation g = f == null ? null : f.generation;
        return g == null ? null : g.store().dir();
    }

    /** What every key in this generation starts with: the driver strings and the Iris and Preheat versions. */
    public List<String> context() {
        return driver().context();
    }

    /** A patched program found saved ({@code hit}) or patched afresh, for the build's log line. */
    public void countPatch(boolean hit) {
        if (hit) {
            patchHits++;
        } else {
            patchMisses++;
        }
    }

    /** Marks a saved file as just used, in the background, so the size limit removes it last. */
    public void touchLater(Path file) {
        writer.execute(() -> ProgramBinaryStore.touch(file));
    }

    /** Saves a patched program's encoded stages ({@link PatchedSources}) in the background. */
    public void savePatch(String key, Supplier<byte[]> encoded) {
        Folder f = folder;
        Generation g = f == null ? null : f.generation;
        if (f == null || g == null) {
            return;
        }
        Path dir = g.store().dir();
        write(f, g, "a patched shader", () -> PatchedSources.store(dir, key, encoded.get()));
    }

    /** A program Iris is deleting: a save waiting for it is dropped, because its name may be reused. */
    public void forgetPending(int program) {
        pending.forget(program);
    }

    /** Deletes a program Preheat made that will not be used (a load that failed part way). */
    public void discard(int program) {
        BinaryChecks.discard(gl, program);
    }

    /** A program Iris linked and checks later (the gbuffer programs): saved when the build ends. */
    public void saveLater(int program, String key) {
        pending.add(program, key);
    }

    /**
     * The build ended: pending saves, the crash guard, leftover temporary files. Returns the cache's part of the build's
     * log line.
     */
    public String endBuild() {
        for (var p : pending.drain()) {
            int program = p.getKey();
            if (driverGl.isProgram(program)) {
                Features.guard(Feature.PROGRAM_CACHE, () -> save(program, p.getValue()));
            }
        }
        Folder f = folder;
        if (f != null) {
            if (armed) {
                armed = false;
                f.guard.disarm();
            }
            if (f.generation != null && !f.shared()) {
                Path root = f.root;
                writer.execute(() -> tidy(root));
            }
        }
        if (hits + misses + patchHits + patchMisses == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (patchHits + patchMisses > 0) {
            sb.append(patchHits).append(" of ").append(patchHits + patchMisses).append(" patches from the cache; ");
        }
        if (verified == 0) {
            sb.append("nothing cached yet");
        } else {
            sb.append("verified ")
                    .append(verified)
                    .append(" cached binaries (")
                    .append(String.format(Locale.ROOT, "%.1f MB", verifyBytes / 1048576.0))
                    .append(") in ")
                    .append(Preheat.ms(verifyNanos))
                    .append(" ms");
        }
        if (refused > 0) {
            sb.append(", ").append(refused).append(" refused by the driver");
        }
        if (unlinked > 0) {
            sb.append(", ").append(unlinked).append(" not saved (did not link)");
        }
        if (noBinary > 0) {
            sb.append(", ").append(noBinary).append(" not saved (the driver gave no binary)");
        }
        if (damaged > 0) {
            sb.append(", ").append(damaged).append(" damaged files deleted");
        }
        return sb.toString();
    }

    private void tidy(Path root) {
        try {
            CacheDir.deleteTemporaries(root);
            int over = ProgramBinaryStore.enforceCap(root, capBytes);
            if (over > 0) {
                Preheat.LOG.warn(
                        "PREHEAT Shader cache: over its {} GB safety limit, deleted the {} oldest files",
                        capBytes >> 30,
                        over);
            }
        } catch (IOException e) {
            Preheat.LOG.warn("PREHEAT Shader cache: could not tidy {}", root, e);
        }
    }

    /**
     * Deletes everything Preheat saved in the cache folder (the settings button), and nothing else that is there; the
     * next builds compile and fill it again. {@code done} hears whether it worked, on the writer thread. Only a delete
     * that worked lifts a pause.
     */
    public void purge(Consumer<Boolean> done) {
        CachePrefetch.releaseAll();
        Path root = root();
        writer.execute(() -> {
            boolean ok;
            try {
                CacheDir.purge(root);
                Preheat.LOG.info("PREHEAT Shader cache deleted: {}", root);
                ok = true;
            } catch (IOException | RuntimeException e) {
                Preheat.LOG.warn("PREHEAT Shader cache: could not delete {}", root, e);
                ok = generationsIn(root) == 0;
            }
            if (ok) {
                Folder f = folder;
                if (f != null) {
                    f.paused = null;
                    f.retired = false;
                    f.generation = null;
                }
                refusals.set(0);
            }
            done.accept(ok);
        });
    }

    private static int generationsIn(Path root) {
        try {
            return CacheDir.generations(root).size();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Gets {@code target} ({@code ""} for the default) ready to be the cache folder, moving what is saved when {@code
     * move}. Runs in the background; {@code done} gets null on success, after which the caller switches with {@link
     * #useFolder} on the render thread, or the reason it failed, in which case nothing changed. Never throws.
     */
    public void changeFolder(String target, boolean move, Consumer<@Nullable FolderProblem> done) {
        Path from = root();
        writer.execute(() -> done.accept(moveTo(from, target, move)));
    }

    private static @Nullable FolderProblem moveTo(Path from, String target, boolean move) {
        FolderProblem problem = target.isBlank() ? null : CacheFolder.check(target);
        if (problem != null) {
            Preheat.LOG.warn("PREHEAT Shader cache: the folder {} cannot be used ({})", target, problem);
            return problem;
        }
        try {
            Path to = target.isBlank()
                    ? CacheFolder.instanceFolder(Platform.get().gameDir())
                    : CacheFolder.ownedChild(Path.of(target.trim()));
            if (target.isBlank()) {
                CacheDir.adopt(to);
            } else {
                CacheDir.ensureOwned(to);
            }
            if (move) {
                int n = CacheDir.move(from, to);
                Preheat.LOG.info("PREHEAT Shader cache: moved {} files from {} to {}", n, from, to);
            }
            return null;
        } catch (IOException | RuntimeException e) {
            Preheat.LOG.warn("PREHEAT Shader cache: could not move it to {}", target, e);
            return FolderProblem.MOVE_FAILED;
        }
    }

    /** Files and bytes in the cache folder now. */
    public ProgramBinaryStore.Usage usage() {
        return ProgramBinaryStore.usage(root());
    }
}
