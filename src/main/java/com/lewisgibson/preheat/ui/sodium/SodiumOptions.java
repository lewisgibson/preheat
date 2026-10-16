package com.lewisgibson.preheat.ui.sodium;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.ui.LiveText;
import com.lewisgibson.preheat.ui.SettingsFacts;
import com.lewisgibson.preheat.ui.SettingsScreens;
import com.lewisgibson.preheat.ui.SettingsText;
import java.util.List;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Preheat in Sodium's Video Settings, next to Iris: a General page whose section heading is the plain status line
 * ("Ready: Overworld, Nether, End", worked out each time it is drawn) over a tick per dimension and one for dimensions
 * not visited yet, and a Shader Cache entry that opens Preheat's own screen, as Iris's Shader Packs entry opens Iris's.
 * Sodium's options cannot be nested or indented and draw a disabled option struck through, so no tick depends on
 * another: every dimension is its own switch and Preheat is off when none is on. Called by Sodium only (the
 * {@code sodium:config_api_user} entrypoint on Fabric, the annotation on NeoForge); if Sodium's API ever changes shape,
 * the page is skipped and Preheat's own screen still has every setting.
 */
@ConfigEntryPointForge(Preheat.MOD_ID)
public final class SodiumOptions implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            register(builder);
        } catch (RuntimeException | LinkageError e) {
            Preheat.LOG.warn(
                    "PREHEAT Could not add Preheat's page to Sodium's video settings; its settings are on Preheat's own screen",
                    e);
        }
    }

    private static void register(ConfigBuilder builder) {
        OptionGroupBuilder dims = builder.createOptionGroup().setName(LiveText.of(SettingsText::generalStatus));
        List<String> ids = SettingsText.listedDimensions();
        for (String dim : ids) {
            String name = KnownDimensions.display(dim);
            dims.addOption(builder.createBooleanOption(id(SettingsFacts.optionPath(dim)))
                    .setName(Component.literal(name))
                    .setTooltip(Component.translatable("preheat.option.dimension.description", name))
                    .setDefaultValue(true)
                    .setEnabled(SettingsText.available())
                    .setBinding(
                            v -> PreheatConfig.change(n -> n.dimensions.put(dim, v)),
                            () -> PreheatConfig.get().dimensions.getOrDefault(dim, true))
                    .setStorageHandler(() -> {}));
        }
        dims.addOption(builder.createBooleanOption(id("new_dimensions"))
                .setName(Component.translatable("preheat.option.moddedDimensions"))
                .setTooltip(Component.translatable("preheat.option.moddedDimensions.description"))
                .setDefaultValue(true)
                .setEnabled(SettingsText.available())
                .setBinding(
                        v -> PreheatConfig.change(n -> n.moddedDimensions = v),
                        () -> PreheatConfig.get().moddedDimensions)
                .setStorageHandler(() -> {}));
        OptionPageBuilder general = builder.createOptionPage()
                .setName(Component.translatable("preheat.page.general"))
                .addOptionGroup(dims);
        ExternalPageBuilder cache = builder.createExternalPage()
                .setName(Component.translatable("preheat.section.cache"))
                .setScreenConsumer(parent -> Minecraft.getInstance().gui.setScreen(SettingsScreens.create(parent)));
        builder.registerOwnModOptions()
                .setName(Preheat.NAME)
                .formatVersion(v -> Preheat.displayVersion(v))
                .setNonTintedIcon(Identifier.fromNamespaceAndPath(Preheat.MOD_ID, "icon.png"))
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0xFF8A3D))
                .addPage(general)
                .addPage(cache);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Preheat.MOD_ID, path);
    }
}
