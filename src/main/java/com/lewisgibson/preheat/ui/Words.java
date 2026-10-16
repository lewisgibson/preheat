package com.lewisgibson.preheat.ui;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Words for the settings screens, decided without the game ({@link SettingsFacts}): a language key with its arguments
 * (which may be words themselves), or plain text such as a folder or a size.
 */
public record Words(@Nullable String key, String text, List<Object> args) {
    public static Words translated(String key, Object... args) {
        return new Words(key, "", List.of(args));
    }

    public static Words plain(String text) {
        return new Words(null, text, List.of());
    }

    /** {@code words} as the game shows them, or null for none. */
    public static @Nullable Component componentOf(@Nullable Words words) {
        return words == null ? null : words.component();
    }

    /** The words as the game shows them. */
    public Component component() {
        String k = key;
        if (k == null) {
            return Component.literal(text);
        }
        Object[] shown =
                args.stream().map(a -> a instanceof Words w ? w.component() : a).toArray();
        return Component.translatable(k, shown);
    }
}
