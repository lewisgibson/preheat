package com.lewisgibson.preheat.ui;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

/**
 * Text worked out each time it is drawn. Sodium builds its settings pages once at startup, so a status line such as
 * "23 MB saved" would otherwise be fixed at the value it had then. Only for display: it is never saved or sent.
 */
public final class LiveText implements ComponentContents {
    private final Supplier<Component> text;

    private LiveText(Supplier<Component> text) {
        this.text = text;
    }

    public static MutableComponent of(Supplier<Component> text) {
        return MutableComponent.create(new LiveText(text));
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> output, Style style) {
        return text.get().visit(output, style);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> output) {
        return text.get().visit(output);
    }

    /** Plain text of what it shows at the time, read back as a fixed string: never serialized in practice. */
    private static final MapCodec<LiveText> CODEC = PlainTextContents.MAP_CODEC.xmap(
            plain -> new LiveText(() -> Component.literal(plain.text())),
            live -> PlainTextContents.create(live.text.get().getString()));

    @Override
    public MapCodec<? extends ComponentContents> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "live(" + text.get().getString() + ")";
    }
}
