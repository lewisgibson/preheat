package com.lewisgibson.preheat.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

/** Text that is worked out each time it is drawn, for Sodium's pages, which are built once. */
class LiveTextTest {
    @Test
    void theTextIsWorkedOutEachTimeItIsRead() {
        AtomicReference<String> now = new AtomicReference<>("23 MB saved");
        MutableComponent live = LiveText.of(() -> Component.literal(now.get()));
        assertEquals("23 MB saved", live.getString());
        now.set("40 MB saved");
        assertEquals("40 MB saved", live.getString());
        StringBuilder styled = new StringBuilder();
        live.visit(
                (style, text) -> {
                    styled.append(text);
                    return java.util.Optional.empty();
                },
                Style.EMPTY);
        assertEquals("40 MB saved", styled.toString());
        assertEquals("live(40 MB saved)", live.getContents().toString());
    }

    @Test
    void writtenDownItIsWhatItShowsAtThatMoment() {
        ComponentContents contents =
                LiveText.of(() -> Component.literal("Ready: Nether")).getContents();
        @SuppressWarnings("unchecked")
        Codec<ComponentContents> codec =
                (Codec<ComponentContents>) contents.codec().codec();
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, contents).getOrThrow();
        assertEquals("{\"text\":\"Ready: Nether\"}", json.toString());
        ComponentContents back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("live(Ready: Nether)", back.toString());
    }
}
