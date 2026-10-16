package com.lewisgibson.preheat.feature;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Which features run in this game. A feature runs when its mixins applied (decided once, at startup, by
 * {@link FeatureCheck}), it has not failed since, and its setting is on. A feature that throws at runtime is turned
 * off for the rest of the session with one log line, so a change in Iris that the shape check could not see costs
 * that one feature, never the game.
 */
public final class Features {
    private static volatile Map<Feature, FeatureCheck.Result> results = Map.of();
    // Written from whichever thread a feature fails on (the render thread, Distant Horizons' close).
    private static final Set<Feature> FAILED = ConcurrentHashMap.newKeySet();

    private Features() {}

    /** Records the startup check; called once by the mixin plugin. */
    public static void set(Map<Feature, FeatureCheck.Result> r) {
        Map<Feature, FeatureCheck.Result> copy = new EnumMap<>(Feature.class);
        copy.putAll(r);
        results = Collections.unmodifiableMap(copy);
        FAILED.clear();
    }

    /** The startup check's result for one feature. */
    public static FeatureCheck.Result result(Feature f) {
        FeatureCheck.Result r = results.get(f);
        return r == null ? new FeatureCheck.Result(FeatureCheck.State.OFF, List.of("Iris is not installed")) : r;
    }

    /** Whether the feature's mixins applied and it has not failed this session. */
    public static boolean available(Feature f) {
        return result(f).on() && !FAILED.contains(f);
    }

    /** Whether the feature should act now: available and switched on in the settings in use. */
    public static boolean on(Feature f) {
        return available(f) && on(f, PreheatConfig.get());
    }

    /**
     * Whether the settings switch the feature on. Preheat is off when no dimension (and no new one) is switched on; with
     * any on, every feature runs and the per-dimension switches decide which dimensions are kept and prepared.
     */
    public static boolean on(Feature f, PreheatConfig c) {
        return switch (f) {
            case BUILD_TRACKING, STARTUP -> true;
            case KEEP_PIPELINES,
                    PREBUILD,
                    PROGRAM_CACHE,
                    PATCH_CACHE,
                    LOADING_PROGRESS,
                    BACKGROUND_DH_CLOSE,
                    DIAGNOSTICS,
                    BUILD_SPLIT -> c.active();
        };
    }

    /** Why the feature cannot run this session, or null when it can. */
    public static @Nullable String whyUnavailable(Feature f) {
        if (FAILED.contains(f)) {
            return "it stopped after an error this session (see the log)";
        }
        FeatureCheck.Result r = result(f);
        return r.on() ? null : String.join("; ", r.problems());
    }

    /** Turns a feature off for the rest of the session after it threw; logs once. */
    public static void fail(Feature f, Throwable t) {
        if (FAILED.add(f)) {
            Preheat.LOG.error(
                    "PREHEAT Turned off {} for this session after an error; the game carries on without it",
                    f.label,
                    t);
        }
    }

    /** Runs {@code body} for a feature, turning the feature off if it throws. */
    public static void guard(Feature f, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError e) {
            fail(f, e);
        }
    }
}
