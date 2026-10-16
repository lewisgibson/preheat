package com.lewisgibson.preheat.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gbuffer programs whose binaries are saved when the build ends, by GL program name. A name is only good until the
 * program is deleted: the driver gives it to the next program created, so a deleted program's entry is dropped at once
 * and can never be matched to another program's binary. Render thread only.
 */
final class PendingSaves {
    private final Map<Integer, String> keys = new LinkedHashMap<>();

    /** Iris linked {@code program}, compiled for {@code key}. */
    void add(int program, String key) {
        keys.put(program, key);
    }

    /** {@code program} is being deleted. */
    void forget(int program) {
        keys.remove(program);
    }

    /** A new build: anything left from one that never ended is dropped. */
    void clear() {
        keys.clear();
    }

    /** The saves to make now, in the order the programs were linked; empties the list. */
    List<Map.Entry<Integer, String>> drain() {
        List<Map.Entry<Integer, String>> out = new ArrayList<>(keys.entrySet());
        keys.clear();
        return out;
    }
}
