package com.lewisgibson.preheat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.platform.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Preheat's settings, kept in {@code config/preheat.json}: one switch per dimension, whether dimensions not seen yet
 * start switched on, and an optional custom folder for the shader cache. A missing or unreadable file means the
 * defaults (everything on, the cache in the instance). Preheat is off when no dimension is switched on and new ones are
 * not either; there is no separate master switch on screen ({@link #enabled} stays in the file for troubleshooting).
 */
public final class PreheatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile @Nullable PreheatConfig current;
    private static volatile @Nullable Path fileOverride;

    /** A switch kept only in the file, for troubleshooting: off, the game behaves exactly as without Preheat. */
    public boolean enabled = true;

    /** Dimension id ("minecraft:the_nether") to whether Preheat keeps and prepares its shaders; every dimension seen. */
    public Map<String, Boolean> dimensions = new TreeMap<>();

    /** An absolute folder for the shader cache; empty means the instance's own {@code preheat/shader-cache}. */
    public String cacheFolder = "";

    /** Whether a dimension seen for the first time starts switched on (modded dimensions, "Modded Dimensions"). */
    public boolean moddedDimensions = true;

    /** Dimensions prepared while the game loads, as patterns ({@link DimensionPolicy}). */
    public List<String> prepareAtStartup = new ArrayList<>(DimensionPolicy.PREPARE_AT_STARTUP);

    /** Dimensions never kept: built when entered, let go when left, as patterns ({@link DimensionPolicy}). */
    public List<String> neverKeep = new ArrayList<>(DimensionPolicy.NEVER_KEEP);

    /** The settings in use, read from disk the first time they are asked for. */
    public static PreheatConfig get() {
        PreheatConfig c = current;
        if (c == null) {
            synchronized (PreheatConfig.class) {
                c = current;
                if (c == null) {
                    c = load(file());
                    current = c;
                }
            }
        }
        return c;
    }

    /** Replaces the settings in use and writes them to disk. */
    public static void save(PreheatConfig c) {
        c.normalize();
        current = c;
        try {
            write(file(), c);
        } catch (IOException e) {
            Preheat.LOG.warn("PREHEAT Could not save {}", file(), e);
        }
    }

    /** Saves a copy of the settings in use with {@code change} made to it (a tick on the settings screens). */
    public static void change(Consumer<PreheatConfig> change) {
        PreheatConfig next = get().copy();
        change.accept(next);
        save(next);
    }

    public static Path file() {
        Path override = fileOverride;
        return override != null ? override : Platform.get().configDir().resolve(Preheat.MOD_ID + ".json");
    }

    /** Reads and writes the settings at {@code file} instead of the loader's config folder, starting afresh; the tests' seam. */
    static void useFile(@Nullable Path file) {
        synchronized (PreheatConfig.class) {
            fileOverride = file;
            current = null;
        }
    }

    /** The settings in {@code file}, or the defaults when it is missing or cannot be read. */
    public static PreheatConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new PreheatConfig().normalize();
        }
        try {
            PreheatConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PreheatConfig.class);
            return (c == null ? new PreheatConfig() : c).normalize();
        } catch (IOException | JsonParseException e) {
            Preheat.LOG.warn("PREHEAT Could not read {}, using the defaults: {}", file, e.getMessage());
            return new PreheatConfig().normalize();
        }
    }

    /** Writes through a temporary file, so a crash never leaves half a settings file. */
    public static void write(Path file, PreheatConfig c) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(c), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Fills what a hand-edited or older file left out; returns this. */
    public PreheatConfig normalize() {
        dimensions = dimensions == null ? new TreeMap<>() : new TreeMap<>(dimensions);
        dimensions.values().removeIf(Objects::isNull);
        for (String id : KnownDimensions.VANILLA) {
            dimensions.putIfAbsent(id, true);
        }
        cacheFolder = cacheFolder == null ? "" : cacheFolder.trim();
        prepareAtStartup = prepareAtStartup == null
                ? new ArrayList<>(DimensionPolicy.PREPARE_AT_STARTUP)
                : new ArrayList<>(
                        prepareAtStartup.stream().filter(Objects::nonNull).toList());
        neverKeep = neverKeep == null
                ? new ArrayList<>(DimensionPolicy.NEVER_KEEP)
                : new ArrayList<>(neverKeep.stream().filter(Objects::nonNull).toList());
        return this;
    }

    public PreheatConfig copy() {
        return GSON.fromJson(GSON.toJson(this), PreheatConfig.class).normalize();
    }

    /** Whether Preheat keeps and prepares this dimension's shaders: its own switch, or for one not seen yet the new one. */
    public boolean dimensionOn(String id) {
        return enabled && !DimensionPolicy.neverKeeps(this, id) && dimensions.getOrDefault(id, moddedDimensions);
    }

    /** Whether Preheat does anything at all: some dimension, or new ones, switched on. */
    public boolean active() {
        return enabled && (moddedDimensions || dimensions.containsValue(true));
    }

    /** Adds dimensions not seen before, switched on when new dimensions are; returns whether any was new. */
    public boolean discover(Collection<String> ids) {
        boolean added = false;
        for (String id : ids) {
            if (dimensions.putIfAbsent(id, moddedDimensions) == null) {
                added = true;
            }
        }
        return added;
    }

    /** Records dimensions the game has just reported, saving the settings when one is new. */
    public static void discoverAndSave(Collection<String> ids) {
        synchronized (PreheatConfig.class) {
            PreheatConfig next = get().copy();
            if (next.discover(ids)) {
                save(next);
            }
        }
    }

    /**
     * After a join or a dimension change: lists the well-known dimensions the server has and the one being entered
     * ({@link DimensionPolicy#toList}), saving the settings when one is new.
     */
    public static void discoverAndSave(List<String> serverDimensions, String entered, Set<String> packNamed) {
        discoverAndSave(DimensionPolicy.toList(get(), serverDimensions, entered, packNamed));
    }
}
