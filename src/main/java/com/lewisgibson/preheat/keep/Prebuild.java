package com.lewisgibson.preheat.keep;

import com.lewisgibson.preheat.config.DimensionPolicy;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

/** Which pipelines are built while the game loads: every dimension switched on, under the key Iris files it by. */
public final class Prebuild {
    private Prebuild() {}

    /**
     * The pipeline keys to build ahead for the dimensions given (those switched on and in {@code prepareAtStartup}), in
     * order, without duplicates and without the overworld's (Iris builds that one itself). A dimension the shader pack
     * does not name shares another's pipeline: the Nether's and the End's fall back to Iris's own keys, and any other
     * needs nothing built.
     */
    public static <K> List<K> keys(
            List<String> enabledIds,
            Set<K> packDimensions,
            java.util.function.Function<String, K> key,
            K end,
            K overworld) {
        List<K> out = new ArrayList<>();
        for (String id : enabledIds) {
            K k = key.apply(id);
            K chosen;
            if (packDimensions.contains(k)) {
                chosen = k;
            } else if (id.equals(KnownDimensions.END)) {
                chosen = end;
            } else if (id.equals(KnownDimensions.NETHER)) {
                chosen = k;
            } else {
                continue;
            }
            if (!chosen.equals(overworld) && !out.contains(chosen)) {
                out.add(chosen);
            }
        }
        return out;
    }

    /**
     * The dimensions to prepare while the game loads: those listed in the settings, switched on and in
     * {@code prepareAtStartup}, the vanilla three first. A modpack's other dimensions wait for the first visit.
     */
    public static List<String> enabledIds(PreheatConfig c) {
        List<String> ids = new ArrayList<>();
        c.dimensions.forEach((id, on) -> {
            if (c.dimensionOn(id) && DimensionPolicy.preparesAtStartup(c, id)) {
                ids.add(id);
            }
        });
        ids.sort(KnownDimensions::order);
        return ids;
    }

    /** The keys for the dimensions the shader pack in use names, and the settings in use. */
    public static List<NamespacedId> forPack(Set<NamespacedId> packDimensions) {
        return keys(
                enabledIds(PreheatConfig.get()),
                packDimensions,
                NamespacedId::new,
                DimensionId.END,
                DimensionId.OVERWORLD);
    }
}
