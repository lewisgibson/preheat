package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * What the cache hooks inside Iris's program building do, kept out of the mixins so it can be tested. Nothing here ever
 * throws into Iris: an error turns the cache off for the session ({@link Features#fail}), deletes any program it had
 * half made, and lets Iris compile the program as it always does. An exception reaching Iris would fail the whole
 * shader pack instead.
 */
public final class CacheHooks {
    private CacheHooks() {}

    /**
     * Tries the cache before Iris compiles a program.
     *
     * @param key the program's key, or null when the cache is not in use
     * @param load the cached program for a key, or 0
     * @param use hands a cached program to Iris instead of compiling (may throw; the program is then deleted)
     * @param miss called for a key the cache did not have
     * @param discard deletes a program that will not be used
     * @return the key to save under once Iris has compiled the program, or null (a hit, the cache off, or an error)
     */
    public static @Nullable String beforeCompile(
            Supplier<@Nullable String> key,
            ToIntFunction<String> load,
            IntConsumer use,
            java.util.function.Consumer<String> miss,
            IntConsumer discard) {
        int program = 0;
        try {
            String k = key.get();
            if (k == null) {
                return null;
            }
            program = load.applyAsInt(k);
            if (program != 0) {
                use.accept(program);
                return null;
            }
            miss.accept(k);
            return k;
        } catch (RuntimeException | LinkageError e) {
            Features.fail(Feature.PROGRAM_CACHE, e);
            // A program loaded but not handed over (using it threw) is Preheat's to delete.
            if (program != 0) {
                discard.accept(program);
            }
            return null;
        }
    }

    /** Saves what Iris compiled for {@code key}; an error turns the cache off and leaves Iris's program as it is. */
    public static void afterCompile(@Nullable String key, Runnable save) {
        if (key != null) {
            Features.guard(Feature.PROGRAM_CACHE, save);
        }
    }
}
