package com.lewisgibson.preheat.progress;

import com.lewisgibson.preheat.config.KnownDimensions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The builds of one loading screen and how far along they are: the dimensions in order, the number of programs each
 * is expected to have (what it had last time, or a guess), and the programs made so far. Pure arithmetic, so the bar
 * and the line can be tested without a game.
 */
public final class ProgressPlan {
    /** A pack's dimension has about this many programs the first time it is seen (Complementary has 101). */
    public static final int FIRST_GUESS = 100;

    private final LinkedHashMap<String, Integer> expected = new LinkedHashMap<>();
    private final Map<String, Integer> made = new LinkedHashMap<>();
    private final Map<String, Boolean> known = new LinkedHashMap<>();
    private @Nullable String current;
    private int currentMade;

    /** Adds a dimension to build, with its program count from last time, or null when there is none. */
    public void add(String dimension, @Nullable Integer lastCount) {
        if (!expected.containsKey(dimension)) {
            expected.put(dimension, lastCount != null && lastCount > 0 ? lastCount : FIRST_GUESS);
            known.put(dimension, lastCount != null && lastCount > 0);
        }
    }

    public void start(String dimension, @Nullable Integer lastCount) {
        add(dimension, lastCount);
        current = dimension;
        currentMade = 0;
    }

    public void progress(int programsSoFar) {
        currentMade = programsSoFar;
        String d = current;
        if (d != null && known.getOrDefault(d, false) && programsSoFar > expectedOf(d)) {
            expected.put(d, programsSoFar);
        }
    }

    public void finish(String dimension, int programs) {
        made.put(dimension, programs);
        expected.put(dimension, programs);
        known.put(dimension, true);
        if (dimension.equals(current)) {
            current = null;
            currentMade = 0;
        }
    }

    /** The bar's fill: programs made over programs expected, never full until the last build ends. */
    public float fraction() {
        long total = 0;
        long done = 0;
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            total += e.getValue();
            Integer finished = made.get(e.getKey());
            if (finished != null) {
                done += finished;
            } else if (e.getKey().equals(current)) {
                done += Math.min(currentMade, e.getValue());
            }
        }
        return total == 0 ? 0f : Math.min(0.99f, (float) done / total);
    }

    /**
     * What the line under the bar says, counted the same way as the bar: programs made so far out of every program the
     * startup will build, so "43/303" and the bar always agree.
     */
    public record Line(@Nullable String dimension, long done, long total, boolean totalKnown) {}

    /** The line under the bar now. */
    public Line line() {
        long total = 0;
        long done = 0;
        boolean allKnown = true;
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            total += e.getValue();
            allKnown &= known.getOrDefault(e.getKey(), false);
            Integer finished = made.get(e.getKey());
            if (finished != null) {
                done += finished;
            } else if (e.getKey().equals(current)) {
                done += Math.min(currentMade, e.getValue());
            }
        }
        return new Line(current == null ? null : KnownDimensions.display(current), done, total, allKnown);
    }

    private int expectedOf(String dimension) {
        return expected.getOrDefault(dimension, FIRST_GUESS);
    }

    /** The dimensions in plan order. */
    public List<String> dimensions() {
        return new ArrayList<>(expected.keySet());
    }
}
