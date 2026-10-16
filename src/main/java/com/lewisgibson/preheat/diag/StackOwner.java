package com.lewisgibson.preheat.diag;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Names which mod a render-thread stack is busy in: the innermost frame that belongs to a known mod (or is a Preheat mixin handler) wins. */
public final class StackOwner {
    /** Package prefix to owner, checked in order. */
    static final List<Map.Entry<String, String>> OWNERS = List.of(
            Map.entry("com.lewisgibson.preheat", "preheat"),
            Map.entry("com.seibel.distanthorizons", "distanthorizons"),
            Map.entry("de.maxhenkel.voicechat", "voicechat"),
            Map.entry("traben.entity_texture_features", "entity_texture_features"),
            Map.entry("traben.entity_model_features", "entity_model_features"),
            Map.entry("net.irisshaders", "iris"),
            Map.entry("net.caffeinemc", "sodium"),
            Map.entry("journeymap", "journeymap"),
            Map.entry("net.fabricmc.fabric", "fabric-api"),
            Map.entry("net.minecraft", "minecraft"),
            Map.entry("com.mojang", "minecraft"));

    private StackOwner() {}

    /** The owner and the frame it was in, as "owner|class.method"; frames are innermost first, as getStackTrace gives them. */
    public static String of(StackTraceElement[] frames) {
        for (StackTraceElement f : frames) {
            // Preheat's mixin handlers are merged into Iris and Minecraft classes; their names keep the prefix.
            String owner = f.getMethodName().contains("preheat$") ? "preheat" : ownerOf(f.getClassName());
            if (owner != null) {
                return owner + "|" + shortClass(f.getClassName()) + "." + f.getMethodName();
            }
        }
        return "other|"
                + (frames.length == 0 ? "?" : shortClass(frames[0].getClassName()) + "." + frames[0].getMethodName());
    }

    static @Nullable String ownerOf(String className) {
        for (Map.Entry<String, String> e : OWNERS) {
            if (className.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    static String shortClass(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
