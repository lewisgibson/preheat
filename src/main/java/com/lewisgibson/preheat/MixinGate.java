package com.lewisgibson.preheat;

import com.lewisgibson.preheat.cache.CachePrefetch;
import com.lewisgibson.preheat.config.CacheFolder;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureCheck;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.feature.InjectionCheck;
import com.lewisgibson.preheat.feature.ShapeCheck;
import com.lewisgibson.preheat.platform.Platform;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

/**
 * Decides which of Preheat's mixins apply ({@link PreheatMixinPlugin} asks it for Mixin). Before any applies, every
 * feature is checked against the class files actually installed (Iris, Minecraft, Sodium, Distant Horizons): a feature
 * whose targets fit applies whole, one that does not applies not at all, and the log says which and why. There are no
 * version pins: a new Iris either fits or costs only the features it changed.
 */
public final class MixinGate {
    private final Supplier<ShapeCheck.ClassSource> classes;
    private final LongSupplier clock;
    private @Nullable Map<Feature, FeatureCheck.Result> results;

    /**
     * @param classes class files by name, before anything is applied ({@link com.lewisgibson.preheat.feature.OwnClasses})
     * @param clock nanoseconds, for the log line
     */
    public MixinGate(Supplier<ShapeCheck.ClassSource> classes, LongSupplier clock) {
        this.classes = classes;
        this.clock = clock;
    }

    /** Every feature's state, worked out on the first call and recorded in {@link Features}. */
    public synchronized Map<Feature, FeatureCheck.Result> evaluate() {
        Map<Feature, FeatureCheck.Result> done = results;
        if (done != null) {
            return done;
        }
        Platform p = Platform.get();
        if (!p.isClient()) {
            // A dedicated server (NeoForge loads client mods' jars there too): nothing to do, and nothing to say.
            return record(all(FeatureCheck.State.NOT_NEEDED, "a dedicated server"));
        }
        if (!p.isLoaded("iris")) {
            Preheat.LOG.warn("PREHEAT {} {} is doing nothing: Iris is not installed", Preheat.NAME, Preheat.version());
            return record(all(FeatureCheck.State.OFF, "Iris is not installed"));
        }
        long t0 = clock.getAsLong();
        Map<Feature, FeatureCheck.Result> checked =
                record(FeatureCheck.evaluate(classes.get(), p.isLoaded("distanthorizons")));
        List<String> on = new ArrayList<>();
        for (Feature f : Feature.values()) {
            FeatureCheck.Result r = Features.result(f);
            if (r.on()) {
                on.add(f.name().toLowerCase(Locale.ROOT));
            } else if (r.state() == FeatureCheck.State.OFF) {
                Preheat.LOG.warn("PREHEAT Turned off {}: {}", f.label, String.join("; ", r.problems()));
            }
        }
        Preheat.LOG.info(
                "PREHEAT {} {} on {}, Minecraft {}, Iris {}, Sodium {}, Distant Horizons {}; checked in {} ms; features: {}",
                Preheat.NAME,
                Preheat.version(),
                p.loader(),
                p.modVersion("minecraft").orElse("?"),
                p.modVersion("iris").orElse("?"),
                p.modVersion("sodium").orElse("absent"),
                p.modVersion("distanthorizons").orElse("absent"),
                Preheat.ms(clock.getAsLong() - t0),
                String.join(", ", on));
        if (Features.on(Feature.PROGRAM_CACHE)) {
            readCacheAhead(p);
        }
        return checked;
    }

    private Map<Feature, FeatureCheck.Result> record(Map<Feature, FeatureCheck.Result> r) {
        results = r;
        Features.set(r);
        return r;
    }

    private static Map<Feature, FeatureCheck.Result> all(FeatureCheck.State state, String why) {
        Map<Feature, FeatureCheck.Result> out = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) {
            out.put(f, new FeatureCheck.Result(state, List.of(why)));
        }
        return out;
    }

    /**
     * Starts reading the saved shaders while the game loads ({@link CachePrefetch}). Only plain Java and Preheat's own
     * file code runs here: no game or GL class may load before the mixins are applied.
     */
    private static void readCacheAhead(Platform p) {
        CachePrefetch.start(
                () -> CacheFolder.resolve(p.gameDir(), PreheatConfig.get().cacheFolder)
                        .folder(),
                stats -> Preheat.LOG.info(
                        "PREHEAT Shader cache: read {} saved shaders ({}) ahead in {} ms",
                        stats.entries(),
                        String.format(Locale.ROOT, "%.1f MB", stats.bytes() / 1048576.0),
                        Preheat.ms(stats.nanos())));
    }

    /** Whether a mixin (fully qualified) applies: its feature fits, and a Distant Horizons one only with it installed. */
    public boolean shouldApply(String mixinClassName) {
        Map<Feature, FeatureCheck.Result> checked = evaluate();
        Feature f = Feature.ofMixin(mixinClassName);
        FeatureCheck.Result r = f == null ? null : checked.get(f);
        if (r == null || !r.on()) {
            return false;
        }
        String relative = mixinClassName.substring(mixinClassName.indexOf(".mixin.") + ".mixin.".length());
        return !Feature.distantHorizonsEntry(relative) || Platform.get().isLoaded("distanthorizons");
    }

    /** After a mixin applied: a feature whose injectors found nothing to hook (another mod changed it) turns off. */
    public void applied(String targetClassName, ClassNode targetClass, String mixinClassName) {
        Feature f = Feature.ofMixin(mixinClassName);
        ClassNode mixin = classes.get().get(mixinClassName.replace('.', '/'));
        if (f == null || mixin == null) {
            return;
        }
        List<String> missing = InjectionCheck.notPlaced(mixin, targetClass);
        if (!missing.isEmpty()) {
            Features.fail(
                    f,
                    new IllegalStateException("could not hook into " + targetClassName + " ("
                            + String.join(", ", missing) + "); another mod has probably changed that code"));
        }
    }
}
