package com.lewisgibson.preheat.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.moddiscovery.ModInfo;

/** NeoForge. The loading mod list is read rather than ModList because the mixin plugin asks before ModList exists. */
final class NeoForgePlatform implements Platform {
    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Optional<String> modVersion(String modId) {
        for (ModInfo info : FMLLoader.getCurrent().getLoadingModList().getMods()) {
            if (info.getModId().equals(modId)) {
                return Optional.of(info.getVersion().toString());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<String> mods() {
        return FMLLoader.getCurrent().getLoadingModList().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted()
                .toList();
    }

    @Override
    public boolean isClient() {
        return FMLLoader.getCurrent().getDist().isClient();
    }

    @Override
    public String loader() {
        return "NeoForge " + modVersion("neoforge").orElse("?");
    }
}
