package com.lewisgibson.preheat.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric (and Quilt, which provides the same API). */
final class FabricPlatform implements Platform {
    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public List<String> mods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(c -> c.getMetadata().getId() + "@"
                        + c.getMetadata().getVersion().getFriendlyString())
                .sorted()
                .toList();
    }

    @Override
    public boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public String loader() {
        return "Fabric " + modVersion("fabricloader").orElse("?");
    }
}
