package com.lewisgibson.preheat.config;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import java.util.List;
import java.util.Locale;

/**
 * The dimensions the settings list. The vanilla three are always there; every other dimension is added the first time
 * the game reports it (the server sends its dimension registry at every join, modded and new vanilla ones included)
 * and starts switched on.
 */
public final class KnownDimensions {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";
    public static final List<String> VANILLA = List.of(OVERWORLD, NETHER, END);

    private KnownDimensions() {}

    /** Whether a kept dimension is let go last: the Overworld, the Nether and the End, Iris's own End key too. */
    public static boolean letGoLast(String id, boolean irisEnd) {
        return VANILLA.contains(id) || irisEnd;
    }

    /** How a dimension id reads on screen and in the log: "Nether" for minecraft:the_nether, "Twilight Forest" for a mod's. */
    public static String display(String id) {
        if (id == null || id.isEmpty()) {
            return "Unknown";
        }
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether", "nether" -> "Nether";
            case "the_end", "end" -> "End";
            default -> titleCase(path);
        };
    }

    /** The vanilla three first in their usual order, then the rest by id. */
    public static int order(String a, String b) {
        int ia = VANILLA.indexOf(a);
        int ib = VANILLA.indexOf(b);
        if (ia >= 0 || ib >= 0) {
            return Integer.compare(ia < 0 ? Integer.MAX_VALUE : ia, ib < 0 ? Integer.MAX_VALUE : ib);
        }
        return a.compareTo(b);
    }

    private static final Splitter WORDS = Splitter.on(CharMatcher.anyOf("_-.")).omitEmptyStrings();

    static String titleCase(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        StringBuilder sb = new StringBuilder();
        for (String word : WORDS.split(name)) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.isEmpty() ? path : sb.toString();
    }
}
