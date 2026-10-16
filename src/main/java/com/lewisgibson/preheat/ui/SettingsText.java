package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.keep.PipelineManagerExt;
import java.util.List;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The words on Preheat's settings screens (keys in assets/preheat/lang): the game's {@link SettingsFacts}, on Iris. */
public final class SettingsText {
    private static final SettingsFacts FACTS = new SettingsFacts(
            () -> Iris.getCurrentPack()
                    .map(p -> p.getDimensionMap().keySet().stream()
                            .map(String::valueOf)
                            .collect(Collectors.toSet())),
            () -> Iris.getPipelineManager() instanceof PipelineManagerExt m
                    ? id -> m.preheat$isBuilt(new NamespacedId(id))
                    : null,
            ProgramBinaries::usage,
            ProgramBinaries::folderProblem,
            ProgramBinaries::whyOff,
            System::nanoTime);

    private SettingsText() {}

    static Component title() {
        return Component.translatable("preheat.title");
    }

    static Component section(String id) {
        return Component.translatable("preheat.section." + id);
    }

    public static boolean available() {
        return FACTS.available();
    }

    public static Component generalStatus() {
        return FACTS.status().component();
    }

    static boolean ready() {
        return FACTS.ready();
    }

    public static Component savedValue() {
        return FACTS.savedValue().component();
    }

    static Component readyValue() {
        return FACTS.readyValue().component();
    }

    static Component folderValue() {
        return FACTS.folderValue().component();
    }

    static @Nullable Component cacheNote() {
        return Words.componentOf(FACTS.cacheNote());
    }

    static @Nullable Component folderWarning() {
        return Words.componentOf(FACTS.folderWarning());
    }

    static @Nullable Words folderWarningWords() {
        return FACTS.folderWarning();
    }

    public static List<String> listedDimensions() {
        return FACTS.listedDimensions();
    }
}
