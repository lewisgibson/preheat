package com.lewisgibson.preheat;

import com.lewisgibson.preheat.platform.Platform;
import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/** The mod's id, name and logger. Every line Preheat writes to latest.log comes from the "Preheat" logger. */
public final class Preheat {
    public static final String MOD_ID = "preheat";
    public static final String NAME = "Preheat";
    public static final Logger LOG = LogManager.getLogger(NAME);

    private static volatile @Nullable String version;

    private Preheat() {}

    /** This mod's version as the loader reports it ("1.0.0"). The jar's file name carries the Minecraft line. */
    public static String version() {
        String v = version;
        if (v == null) {
            v = Platform.get().modVersion(MOD_ID).orElse("dev");
            version = v;
        }
        return v;
    }

    /** The version as players see it, without build metadata ("1.0.0"), as Sodium and Iris show theirs. */
    public static String displayVersion() {
        return displayVersion(version());
    }

    public static String displayVersion(String v) {
        int plus = v.indexOf('+');
        return plus < 0 ? v : v.substring(0, plus);
    }

    /** Milliseconds from a nanosecond duration, one decimal, the way every Preheat timing is logged. */
    public static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1e6);
    }
}
