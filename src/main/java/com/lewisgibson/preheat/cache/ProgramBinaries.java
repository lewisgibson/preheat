package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.build.BuildTracker;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.gpu.GpuInfo;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The game's one {@link BinaryCache}, on the real driver, with one background thread writing its files; the crash
 * guard is disarmed when the game exits normally. Every method hands straight to it.
 */
public final class ProgramBinaries {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Preheat cache writer");
        t.setDaemon(true);
        return t;
    });

    static final BinaryCache CACHE = new BinaryCache(
            LwjglProgramGl.INSTANCE,
            LwjglProgramGl.INSTANCE,
            WRITER,
            System::nanoTime,
            BuildTracker.TIMER,
            ProgramBinaryStore.HARD_CAP_BYTES,
            System.getProperty("preheat.debugCacheMisses"),
            CacheDir::lockChannel);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(CACHE::shutdown, "Preheat cache guard"));
    }

    private ProgramBinaries() {}

    public static Path root() {
        return CACHE.root();
    }

    public static @Nullable FolderProblem folderProblem() {
        return CACHE.folderProblem();
    }

    public static @Nullable GpuInfo gpu() {
        return CACHE.gpu();
    }

    public static @Nullable CacheOff whyOff() {
        return CACHE.whyOff();
    }

    public static boolean usable() {
        return CACHE.usable();
    }

    public static void settingsChanged() {
        CACHE.settingsChanged();
    }

    public static void useFolder(String target) {
        CACHE.useFolder(target);
    }

    public static String key(String kind, List<String> bindingsAndSources) {
        return CACHE.key(kind, bindingsAndSources);
    }

    public static @Nullable String keyFor(
            String kind, Supplier<String> first, List<String> stages, List<String> parts) {
        return CACHE.keyFor(kind, first, stages, parts);
    }

    public static void debugMiss(String name, String key, List<String> parts) {
        CACHE.debugMiss(name, key, parts);
    }

    public static void beginBuild() {
        CACHE.beginBuild();
    }

    public static int load(String key) {
        return CACHE.load(key);
    }

    public static void retrievable(int program) {
        CACHE.retrievable(program);
    }

    public static void save(int program, String key) {
        CACHE.save(program, key);
    }

    public static @Nullable Path generationDir() {
        return CACHE.generationDir();
    }

    public static List<String> context() {
        return CACHE.context();
    }

    public static void countPatch(boolean hit) {
        CACHE.countPatch(hit);
    }

    public static void touchLater(Path file) {
        CACHE.touchLater(file);
    }

    public static void savePatch(String key, Supplier<byte[]> encoded) {
        CACHE.savePatch(key, encoded);
    }

    public static void forgetPending(int program) {
        CACHE.forgetPending(program);
    }

    public static void discard(int program) {
        CACHE.discard(program);
    }

    public static void saveLater(int program, String key) {
        CACHE.saveLater(program, key);
    }

    public static String endBuild() {
        return CACHE.endBuild();
    }

    public static void purge(Consumer<Boolean> done) {
        CACHE.purge(done);
    }

    public static void changeFolder(String target, boolean move, Consumer<@Nullable FolderProblem> done) {
        CACHE.changeFolder(target, move, done);
    }

    public static ProgramBinaryStore.Usage usage() {
        return CACHE.usage();
    }
}
