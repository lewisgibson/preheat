package com.lewisgibson.preheat.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A mod loader for tests: its folders, the mods it reports and which side it runs on. */
public record FakePlatform(Path gameDir, Map<String, String> versions, boolean isClient) implements Platform {
    /** A client with these mods loaded ({@code id, version, id, version, ...}), its folders under {@code gameDir}. */
    public static FakePlatform client(Path gameDir, String... idsAndVersions) {
        Map<String, String> versions = new java.util.TreeMap<>();
        for (int i = 0; i + 1 < idsAndVersions.length; i += 2) {
            versions.put(idsAndVersions[i], idsAndVersions[i + 1]);
        }
        return new FakePlatform(gameDir, versions, true);
    }

    public FakePlatform server() {
        return new FakePlatform(gameDir, versions, false);
    }

    @Override
    public Path configDir() {
        return gameDir.resolve("config");
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return Optional.ofNullable(versions.get(modId));
    }

    @Override
    public List<String> mods() {
        return versions.entrySet().stream()
                .map(e -> e.getKey() + "@" + e.getValue())
                .toList();
    }

    @Override
    public String loader() {
        return "Fake " + modVersion("fake").orElse("?");
    }
}
