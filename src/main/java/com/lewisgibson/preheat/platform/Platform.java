package com.lewisgibson.preheat.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** What Preheat needs from the mod loader, on Fabric and NeoForge alike. */
public interface Platform {
    /** The game (instance) directory. */
    Path gameDir();

    /** The config directory. */
    Path configDir();

    /** A loaded mod's version as the loader reports it, or empty when it is not loaded. */
    Optional<String> modVersion(String modId);

    /** Whether this is the game client (Preheat does nothing on a dedicated server). */
    boolean isClient();

    /** Every loaded mod as {@code id@version}, sorted. */
    List<String> mods();

    /** The loader and its version, for the log ("Fabric 0.19.5"). */
    String loader();

    default boolean isLoaded(String modId) {
        return modVersion(modId).isPresent();
    }

    /** The platform this game runs on: Fabric when its loader is on the classpath, otherwise NeoForge. */
    static Platform get() {
        return Holder.current();
    }

    /** Lazily picks the implementation, so neither loader's classes are touched on the other. */
    final class Holder {
        private static volatile @Nullable Platform override;

        private Holder() {}

        static Platform current() {
            Platform o = override;
            return o != null ? o : Loader.INSTANCE;
        }

        /** Answers with {@code p} instead of the loader (null for the loader again); the tests' seam. */
        static void use(@Nullable Platform p) {
            override = p;
        }

        /** Fabric when {@code fabricLoader} can be found, otherwise NeoForge. */
        static Platform pick(String fabricLoader) {
            try {
                Class.forName(fabricLoader, false, Platform.class.getClassLoader());
                return new FabricPlatform();
            } catch (ClassNotFoundException e) {
                return new NeoForgePlatform();
            }
        }

        private static final class Loader {
            static final Platform INSTANCE = pick("net.fabricmc.loader.api.FabricLoader");
        }
    }
}
