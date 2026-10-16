package com.lewisgibson.preheat.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A shader source as the cache keys it. Iris names the copies of a function it specialises per depth texture
 * {@code iris_depth_<n>_<Name>}, numbering them in the iteration order of a map keyed by syntax-tree nodes
 * ({@code DepthTransformer.specializeDepthFunctions}), so the same program can come out with the numbers swapped from
 * one launch to the next. Renumbering every such name by where it first appears gives the same text for the same
 * program. It is a consistent renaming, so two sources only share a key if they are the same program; the program
 * itself is still compiled from Iris's text.
 */
public final class StableSource {
    /** Iris's generated names: {@code iris_<what>_<number>_<rest>}. Shader packs do not use the {@code iris_} prefix. */
    private static final Pattern GENERATED = Pattern.compile("\\biris_([A-Za-z]+)_(\\d+)_(\\w+)");

    private StableSource() {}

    /** The source with Iris's numbered names renumbered in order of first appearance. */
    public static String of(String source) {
        if (!source.contains("iris_")) {
            return source;
        }
        Map<String, String> renamed = new HashMap<>();
        Map<String, Integer> next = new HashMap<>();
        Matcher m = GENERATED.matcher(source);
        StringBuilder out = new StringBuilder(source.length());
        while (m.find()) {
            String name = renamed.computeIfAbsent(m.group(), whole -> {
                int n = next.merge(m.group(1), 1, Integer::sum) - 1;
                return "iris_" + m.group(1) + "_" + n + "_" + m.group(3);
            });
            m.appendReplacement(out, Matcher.quoteReplacement(name));
        }
        m.appendTail(out);
        return out.toString();
    }
}
