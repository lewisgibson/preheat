package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.cache.CacheOff;
import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What the settings screens say, decided from the settings, the features, the shader cache and Iris: the status line,
 * the facts beside the cache and the dimensions listed. {@link SettingsText} is the game's, on Iris.
 */
public final class SettingsFacts {
    private final Supplier<Optional<Set<String>>> pack;
    private final Supplier<@Nullable Predicate<String>> built;
    private final Supplier<ProgramBinaryStore.Usage> usage;
    private final Supplier<@Nullable FolderProblem> folderProblem;
    private final Supplier<@Nullable CacheOff> whyOff;
    private final LongSupplier clock;

    private ProgramBinaryStore.@Nullable Usage lastUsage;
    private long usageAt;

    /**
     * @param pack the dimensions the shader pack in use names, or empty with no pack (may throw: Iris is not ready)
     * @param built whether a dimension's pipeline is built, or null when Iris's manager is not Preheat's (may throw)
     * @param usage what the shader cache holds
     * @param folderProblem why the chosen cache folder is not used, or null
     * @param whyOff why the shader cache is not used, or null
     * @param clock nanoseconds
     */
    public SettingsFacts(
            Supplier<Optional<Set<String>>> pack,
            Supplier<@Nullable Predicate<String>> built,
            Supplier<ProgramBinaryStore.Usage> usage,
            Supplier<@Nullable FolderProblem> folderProblem,
            Supplier<@Nullable CacheOff> whyOff,
            LongSupplier clock) {
        this.pack = pack;
        this.built = built;
        this.usage = usage;
        this.folderProblem = folderProblem;
        this.whyOff = whyOff;
        this.clock = clock;
    }

    /** Whether Preheat can work in this game at all: this Iris fits keeping dimensions or the shader cache. */
    public boolean available() {
        return Features.available(Feature.KEEP_PIPELINES) || Features.available(Feature.PROGRAM_CACHE);
    }

    /**
     * The one line that says what Preheat is doing right now: off because this Iris is not supported, off because no
     * dimension is ticked, waiting for a shader pack, or which dimensions are ready.
     */
    public Words status() {
        if (!available()) {
            return Words.translated("preheat.status.unavailable");
        }
        if (!PreheatConfig.get().enabled) {
            return Words.translated("preheat.status.switchedOff");
        }
        if (!PreheatConfig.get().active()) {
            return Words.translated("preheat.status.disabled");
        }
        if (!shaderPackOn()) {
            return Words.translated("preheat.status.noPack");
        }
        List<String> ready = readyDimensions();
        return ready.isEmpty()
                ? Words.translated("preheat.status.notReady")
                : Words.translated("preheat.status.ready", String.join(", ", ready));
    }

    /** Whether Preheat is working with dimensions ready (the status line is not a reason it is off). */
    public boolean ready() {
        return available()
                && PreheatConfig.get().active()
                && shaderPackOn()
                && !readyDimensions().isEmpty();
    }

    /** What is saved: its size, or that nothing is yet. Measured at most once a second, as it lists the folder. */
    public Words savedValue() {
        long now = clock.getAsLong();
        ProgramBinaryStore.Usage u = lastUsage;
        if (u == null || now - usageAt > 1_000_000_000L) {
            u = usage.get();
            lastUsage = u;
            usageAt = now;
        }
        return u.files() == 0 ? Words.translated("preheat.cache.nothing") : Words.plain(size(u.bytes()));
    }

    /** The dimensions ready now, or "None yet". */
    public Words readyValue() {
        List<String> ready = readyDimensions();
        return ready.isEmpty() ? Words.translated("preheat.cache.noneReady") : Words.plain(String.join(", ", ready));
    }

    /** "Default", or the folder chosen. */
    public Words folderValue() {
        String chosen = PreheatConfig.get().cacheFolder;
        boolean custom = !chosen.isBlank() && folderProblem.get() == null;
        return custom ? Words.plain(chosen) : Words.translated("preheat.cache.folder.default");
    }

    /** Why the cache is not being used, or why the chosen folder is not, or null when all is well. */
    public @Nullable Words cacheNote() {
        CacheOff off = whyOff.get();
        if (off != null) {
            return Words.translated(off.key);
        }
        return folderWarning();
    }

    /** Why the chosen folder is not being used, or null when it is (or none was chosen). */
    public @Nullable Words folderWarning() {
        FolderProblem problem = folderProblem.get();
        return problem == null ? null : Words.translated("preheat.cache.folder.notUsed", Words.translated(problem.key));
    }

    /** "38 MB", "1.2 GB", "640 KB". */
    public static String size(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format(Locale.ROOT, "%.0f MB", Math.ceil(bytes / (double) (1L << 20)));
        }
        return String.format(Locale.ROOT, "%.0f KB", Math.ceil(bytes / 1024.0));
    }

    /**
     * The dimensions the settings show, in order: those listed in {@code config/preheat.json} that have shaders of
     * their own with the current shader pack (the vanilla three, and those the pack names). A dimension listed while
     * another pack was in use stays in the file for when that pack is back.
     */
    public List<String> listedDimensions() {
        Optional<Set<String>> named;
        try {
            named = pack.get();
        } catch (RuntimeException | LinkageError e) {
            named = Optional.empty();
        }
        List<String> ids = new ArrayList<>();
        for (String id : PreheatConfig.get().dimensions.keySet()) {
            if (named.isEmpty()
                    || KnownDimensions.VANILLA.contains(id)
                    || named.get().contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(KnownDimensions::order);
        return ids;
    }

    /** The id of a dimension's switch in Sodium's settings: a resource path, so lower case and nothing unusual. */
    public static String optionPath(String dimension) {
        return "dimension/"
                + dimension.replace(':', '/').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private boolean shaderPackOn() {
        try {
            return pack.get().isPresent();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private List<String> readyDimensions() {
        List<String> names = new ArrayList<>();
        try {
            Predicate<String> b = built.get();
            if (b == null) {
                return names;
            }
            for (String id : listedDimensions()) {
                if (b.test(id)) {
                    names.add(KnownDimensions.display(id));
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return List.of();
        }
        return names;
    }
}
