package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.Preheat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** What the cache buttons do and which notification says how it went ({@link CacheActions} wires them to the game). */
final class CacheButtons {
    private CacheButtons() {}

    /** Opens the cache folder (made first if it is not there yet); a failure is logged and said. */
    static void openFolder(Path root, Consumer<Path> open, Consumer<String> toast) {
        try {
            Files.createDirectories(root);
            open.accept(root);
        } catch (IOException | RuntimeException e) {
            Preheat.LOG.warn("PREHEAT Could not open {}", root, e);
            toast.accept("preheat.cache.open.failed");
        }
    }

    /**
     * The player answered "Delete the saved shaders?": on yes they are deleted ({@code purge}, which hears whether it
     * worked in the background), and on the game thread a notification says how it went and the screen refreshes.
     */
    static void answered(
            boolean yes,
            Consumer<Consumer<Boolean>> purge,
            Executor gameThread,
            Consumer<String> toast,
            Runnable refresh) {
        if (yes) {
            purge.accept(ok -> gameThread.execute(() -> {
                toast.accept(ok ? "preheat.cache.deleted" : "preheat.cache.deleteFailed");
                refresh.run();
            }));
        }
    }
}
