package com.lewisgibson.preheat.feature;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Sets which features applied, for tests in other packages. */
public final class FeatureStates {
    private FeatureStates() {}

    /** Every feature applied, except {@code off}. */
    public static void allOnExcept(Feature... off) {
        Set<Feature> skip = Set.of(off);
        Map<Feature, FeatureCheck.Result> results = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            results.put(
                    f,
                    skip.contains(f)
                            ? new FeatureCheck.Result(FeatureCheck.State.OFF, List.of("off for the test"))
                            : new FeatureCheck.Result(FeatureCheck.State.ON, List.of()));
        }
        Features.set(results);
    }

    /** As before any check: nothing applied. */
    public static void none() {
        Features.set(Map.of());
    }
}
