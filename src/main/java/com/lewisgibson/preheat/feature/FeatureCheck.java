package com.lewisgibson.preheat.feature;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.objectweb.asm.tree.ClassNode;

/**
 * Decides, from class files alone, which features fit the installed game: each feature's mixins and helper classes
 * are checked with {@link ShapeCheck}, then a feature whose requirement failed fails too. Used by the mixin plugin at
 * startup and by the compatibility matrix test against every Iris and Minecraft version in deps.json.
 */
public final class FeatureCheck {
    static final String ROOT = "com/lewisgibson/preheat/";
    static final String MIXINS = ROOT + "mixin/";

    /** Iris, Minecraft (with its rendering libraries), Sodium and Distant Horizons: what a broken link would point at. */
    public static final Predicate<String> FOREIGN = name -> name.startsWith("net/irisshaders/")
            || name.startsWith("net/minecraft/")
            || name.startsWith("com/mojang/")
            || name.startsWith("net/caffeinemc/")
            || name.startsWith("com/seibel/distanthorizons/")
            || name.startsWith("org/lwjgl/");

    /** What became of one feature. */
    public enum State {
        /** Every mixin fits; the feature runs (when its setting is on). */
        ON,
        /** Nothing for it to do in this game (Distant Horizons is not installed). */
        NOT_NEEDED,
        /** Something it needs is missing or changed; none of its mixins apply. */
        OFF
    }

    /** One feature's state and, when off, why. */
    public record Result(State state, List<String> problems) {
        public boolean on() {
            return state == State.ON;
        }
    }

    private FeatureCheck() {}

    /** Every feature's state for the classes in {@code source}. */
    public static Map<Feature, Result> evaluate(ShapeCheck.ClassSource source, boolean distantHorizons) {
        Map<Feature, Result> out = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            if (f.needsDistantHorizons && !distantHorizons) {
                out.put(f, new Result(State.NOT_NEEDED, List.of("Distant Horizons is not installed")));
                continue;
            }
            List<String> problems = new ArrayList<>();
            for (String m : f.mixins) {
                if (Feature.distantHorizonsEntry(m) && !distantHorizons) {
                    continue;
                }
                ClassNode node = source.get(MIXINS + m.replace('.', '/'));
                if (node == null) {
                    problems.add("mixin " + m + " is missing from the jar");
                    continue;
                }
                problems.addAll(ShapeCheck.checkMixin(node, source));
                problems.addAll(ShapeCheck.checkLinks(node, source, FOREIGN));
            }
            for (String h : f.helpers) {
                if (Feature.distantHorizonsEntry(h) && !distantHorizons) {
                    continue;
                }
                ClassNode node = source.get(ROOT + h.replace('.', '/'));
                if (node == null) {
                    problems.add("class " + h + " is missing from the jar");
                    continue;
                }
                problems.addAll(ShapeCheck.checkLinks(node, source, FOREIGN));
            }
            if (problems.isEmpty()) {
                String why = f == Feature.KEEP_PIPELINES
                        ? PipelineFingerprint.problem(source)
                        : f == Feature.PATCH_CACHE ? PatchFingerprint.problem(source) : null;
                if (why != null) {
                    problems.add(why);
                }
            }
            out.put(
                    f,
                    new Result(problems.isEmpty() ? State.ON : State.OFF, List.copyOf(new LinkedHashSet<>(problems))));
        }
        // A feature whose requirement is off is off too; repeat until nothing changes.
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Feature f : Feature.values()) {
                if (stateOf(out, f) != State.ON) {
                    continue;
                }
                for (String r : f.requires) {
                    Feature req = Feature.valueOf(r);
                    if (stateOf(out, req) != State.ON) {
                        out.put(f, new Result(State.OFF, List.of("needs " + req.label + ", which is off")));
                        changed = true;
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static State stateOf(Map<Feature, Result> results, Feature f) {
        return Objects.requireNonNull(results.get(f), f.name()).state();
    }
}
