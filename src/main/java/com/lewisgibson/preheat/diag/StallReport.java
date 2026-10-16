package com.lewisgibson.preheat.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Render-thread time by owner and by the method it was in, from stack samples weighted by the time each covers. */
public final class StallReport {
    private final Map<String, Long> byOwner = new HashMap<>();
    private final Map<String, Long> byMethod = new HashMap<>();

    /** One sample: a StackOwner signature ("owner|Class.method") standing for {@code nanos} of render thread. */
    public void add(String signature, long nanos) {
        int bar = signature.indexOf('|');
        String owner = bar < 0 ? signature : signature.substring(0, bar);
        byOwner.merge(owner, nanos, Long::sum);
        byMethod.merge(signature, nanos, Long::sum);
    }

    /** The time charged to one exact signature. */
    public long nanos(String signature) {
        return byMethod.getOrDefault(signature, 0L);
    }

    /** The owners, most time first, each with the method it spent most time in: "owner N ms (Class.method N ms)". */
    public List<String> lines(int top) {
        List<String> out = new ArrayList<>();
        byOwner.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(top)
                .forEach(e -> {
                    Map.Entry<String, Long> topMethod = byMethod.entrySet().stream()
                            .filter(m -> m.getKey().startsWith(e.getKey() + "|"))
                            .max(Map.Entry.comparingByValue())
                            .orElseThrow();
                    out.add(e.getKey() + " " + e.getValue() / 1_000_000 + " ms ("
                            + topMethod.getKey().substring(e.getKey().length() + 1) + " "
                            + topMethod.getValue() / 1_000_000 + " ms)");
                });
        return out;
    }
}
