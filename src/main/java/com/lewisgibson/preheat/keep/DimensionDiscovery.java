package com.lewisgibson.preheat.keep;

import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The settings list the dimensions worth a tick: the well-known ones the server has (its level list, sent at each join)
 * and each one the player enters, the first time. A dimension a mod creates while the game runs is not in the server's
 * list; it is listed when the player first enters it. Never-kept dimensions are not listed ({@code DimensionPolicy}).
 */
public final class DimensionDiscovery {
    private DimensionDiscovery() {}

    /**
     * The game is setting its level (null when it leaves one): records the server's dimensions and the one entered.
     *
     * @param levelId a level's dimension id
     * @param connection the connection to the server, or null without one
     * @param serverLevels the dimension ids the server sent
     * @param packNamed the dimensions the shader pack in use names
     */
    public static <L, C> void levelSet(
            @Nullable L level,
            Function<L, String> levelId,
            Supplier<@Nullable C> connection,
            Function<C, List<String>> serverLevels,
            Supplier<Set<String>> packNamed) {
        if (!Features.available(Feature.KEEP_PIPELINES)) {
            return;
        }
        Features.guard(Feature.KEEP_PIPELINES, () -> {
            C c = connection.get();
            if (c == null) {
                return;
            }
            PreheatConfig.discoverAndSave(
                    serverLevels.apply(c), level == null ? "" : levelId.apply(level), packNamed.get());
        });
    }
}
