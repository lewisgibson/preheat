package com.lewisgibson.preheat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which dimensions are prepared while the game loads, and which are never kept. Both are lists in
 * {@code config/preheat.json} ({@code prepareAtStartup}, by default the vanilla three, and {@code neverKeep}, by default
 * empty), and every entry is treated the same. A dimension in neither is prepared on the player's first visit and kept
 * within the video memory budget ({@code KeptBudget}). A never-kept dimension is built when entered and let go when
 * left; never-keep wins over prepare-at-startup.
 *
 * <p>Entries are patterns: an exact id ({@code aether:the_aether}), or {@code *} for any run of characters
 * ({@code backrooms:*}).
 */
public final class DimensionPolicy {
    /** The default {@code prepareAtStartup}: the vanilla three. */
    public static final List<String> PREPARE_AT_STARTUP =
            List.of(KnownDimensions.OVERWORLD, KnownDimensions.NETHER, KnownDimensions.END);

    /** The default {@code neverKeep}: nothing. */
    public static final List<String> NEVER_KEEP = List.of();

    private DimensionPolicy() {}

    /** Whether {@code id} matches {@code pattern}: equal, or with {@code *} standing for any run of characters. */
    public static boolean matches(String pattern, String id) {
        String p = pattern.trim().toLowerCase(Locale.ROOT);
        String i = id.toLowerCase(Locale.ROOT);
        if (!p.contains("*")) {
            return p.equals(i);
        }
        StringBuilder regex = new StringBuilder();
        for (String part : p.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.matches(regex.toString(), i);
    }

    static boolean any(List<String> patterns, String id) {
        for (String p : patterns) {
            if (p != null && !p.isBlank() && matches(p, id)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a dimension is never kept ({@code neverKeep} in the settings file). */
    public static boolean neverKeeps(PreheatConfig c, String id) {
        return any(c.neverKeep, id);
    }

    /** Whether a dimension is prepared while the game loads ({@code prepareAtStartup}, and not never kept). */
    public static boolean preparesAtStartup(PreheatConfig c, String id) {
        return !neverKeeps(c, id) && any(c.prepareAtStartup, id);
    }

    /**
     * The dimensions worth listing in the settings after a join: those the server has that are prepared at startup (so
     * they can be prepared at the next launch) and the one being entered, never a never-kept one, and only dimensions
     * with shaders of their own: the vanilla three, and those the shader pack names ({@code packNamed}). Iris draws any
     * other dimension with the Overworld's or the End's shaders, so that dimension's tick is the Overworld's or the
     * End's. A mod with dozens of dimensions adds each to the list only when the player goes there.
     */
    public static List<String> toList(
            PreheatConfig c, List<String> serverDimensions, String entered, Set<String> packNamed) {
        List<String> out = new ArrayList<>();
        for (String id : serverDimensions) {
            if (ownShaders(id, packNamed) && preparesAtStartup(c, id)) {
                out.add(id);
            }
        }
        if (!entered.isEmpty() && ownShaders(entered, packNamed) && !neverKeeps(c, entered) && !out.contains(entered)) {
            out.add(entered);
        }
        return out;
    }

    private static boolean ownShaders(String id, Set<String> packNamed) {
        return KnownDimensions.VANILLA.contains(id) || packNamed.contains(id);
    }
}
