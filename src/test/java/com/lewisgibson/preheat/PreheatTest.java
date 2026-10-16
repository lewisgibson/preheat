package com.lewisgibson.preheat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreheatTest {
    @Test
    void timingsAreMillisecondsToOneDecimalInEveryLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertEquals("1657.8", Preheat.ms(1_657_800_000L));
            assertEquals("0.0", Preheat.ms(0));
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void namesAreFixed() {
        assertEquals("preheat", Preheat.MOD_ID);
        assertEquals("Preheat", Preheat.NAME);
        assertEquals("Preheat", Preheat.LOG.getName());
    }

    @Test
    void theShownVersionHasNoBuildMetadata() {
        assertEquals("1.0.0", Preheat.displayVersion("1.0.0+mc26.3"));
        assertEquals("1.0.0", Preheat.displayVersion("1.0.0"));
        assertEquals("0.9.2", Preheat.displayVersion("0.9.2+mc26.3+build.4"));
    }

    @Test
    void theVersionIsAskedOnceAndShownWithoutBuildMetadata() {
        Platforms.use(FakePlatform.client(Path.of("game"), "preheat", "1.0.0+mc26.3"));
        try {
            String v = Preheat.version();
            assertEquals(v, Preheat.version(), "worked out once");
            assertEquals(Preheat.displayVersion(v), Preheat.displayVersion());
        } finally {
            Platforms.use(null);
        }
    }
}
