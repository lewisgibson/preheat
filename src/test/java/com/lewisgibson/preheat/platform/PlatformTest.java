package com.lewisgibson.preheat.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlatformTest {
    @AfterEach
    void loaderAgain() {
        Platforms.use(null);
    }

    @Test
    void fabricWhenItsLoaderIsThereOtherwiseNeoForge() {
        assertInstanceOf(FabricPlatform.class, Platform.Holder.pick("net.fabricmc.loader.api.FabricLoader"));
        assertInstanceOf(NeoForgePlatform.class, Platform.Holder.pick("no.such.Loader"));
        assertInstanceOf(FabricPlatform.class, Platform.get(), "the test classpath has Fabric's loader");
    }

    @Test
    void aStandInAnswersUntilTheLoaderIsHandedBack() {
        FakePlatform fake = FakePlatform.client(Path.of("game"), "iris", "1.11.7");
        Platforms.use(fake);
        assertSame(fake, Platform.get());
        assertTrue(Platform.get().isLoaded("iris"));
        assertFalse(Platform.get().isLoaded("sodium"));
        Platforms.use(null);
        assertInstanceOf(FabricPlatform.class, Platform.get());
    }
}
