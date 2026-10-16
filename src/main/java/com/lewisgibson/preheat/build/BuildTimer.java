package com.lewisgibson.preheat.build;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Where the render thread's time goes while Iris builds a pipeline: each step's own time (time spent in a nested step
 * is charged to the nested one), program counts, and binary cache hits. Steps outside a build are ignored. During a
 * build, every step that ends calls the pulse, which draws the progress screen and keeps the window answering.
 */
public final class BuildTimer {
    /** The steps, in the order the split is logged. */
    public enum Step {
        PREPROCESS("preprocess"),
        TRANSFORM("transform"),
        COMPILE("compile"),
        LINK("link"),
        BINARY_LOAD("binary load"),
        BINARY_SAVE("binary save");

        final String label;

        Step(String label) {
            this.label = label;
        }
    }

    /** One step on the stack: when it started and the time its nested steps took, which is not its own. */
    private static final class Frame {
        final Step step;
        final long start;
        long nested;

        Frame(Step step, long start) {
            this.step = step;
            this.start = start;
        }
    }

    private final LongSupplier clock;
    private final Runnable pulse;
    private final Map<Step, Long> own = new EnumMap<>(Step.class);
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private @Nullable String label;
    private long start;
    private int shaders;
    private int programs;
    private int hits;

    public BuildTimer(LongSupplier clock, Runnable pulse) {
        this.clock = clock;
        this.pulse = pulse;
    }

    public boolean active() {
        return label != null;
    }

    /** A pipeline build begins (a nested begin is ignored: the outer build owns the time). */
    public void begin(String what) {
        if (label != null) {
            return;
        }
        label = what;
        start = clock.getAsLong();
        own.clear();
        stack.clear();
        shaders = programs = hits = 0;
    }

    public void enter(Step step) {
        if (label != null) {
            stack.push(new Frame(step, clock.getAsLong()));
        }
    }

    public void exit(Step step) {
        Frame top = stack.peek();
        if (label == null || top == null || top.step != step) {
            return;
        }
        stack.pop();
        long elapsed = clock.getAsLong() - top.start;
        own.merge(step, elapsed - top.nested, Long::sum);
        Frame parent = stack.peek();
        if (parent != null) {
            parent.nested += elapsed;
        }
        if (step == Step.COMPILE) {
            shaders++;
        }
        pulse.run();
    }

    /** One program made, compiled or loaded from the cache. */
    public void program(boolean fromCache) {
        if (label == null) {
            return;
        }
        programs++;
        if (fromCache) {
            hits++;
        }
    }

    public int programs() {
        return programs;
    }

    /** What is being built (a dimension id), or null outside a build. */
    public @Nullable String label() {
        return label;
    }

    /** Ends the build and returns its split as one line, or null when none was running. */
    public @Nullable String end() {
        if (label == null) {
            return null;
        }
        long total = clock.getAsLong() - start;
        long accounted = 0;
        StringBuilder sb = new StringBuilder();
        sb.append(ms(total)).append(" ms =");
        for (Step s : Step.values()) {
            long t = own.getOrDefault(s, 0L);
            accounted += t;
            sb.append(' ').append(s.label).append(' ').append(ms(t));
            if (s == Step.COMPILE) {
                sb.append(" (").append(shaders).append(" shaders)");
            }
            sb.append(',');
        }
        sb.append(" other ")
                .append(ms(total - accounted))
                .append("; ")
                .append(programs)
                .append(" programs, ")
                .append(hits)
                .append(" from the cache");
        label = null;
        stack.clear();
        return sb.toString();
    }

    static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }
}
