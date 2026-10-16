package com.lewisgibson.preheat.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** "Bring your saved shaders?" with Move Them, Start Fresh and Cancel; Esc is Cancel. */
final class MoveCacheScreen extends Screen {
    enum Choice {
        MOVE,
        START_FRESH,
        CANCEL
    }

    private final Screen parent;
    private final String size;
    private final Consumer<Choice> answer;

    MoveCacheScreen(Screen parent, String size, Consumer<Choice> answer) {
        super(Component.translatable("preheat.cache.folder.move.title"));
        this.parent = parent;
        this.size = size;
        this.answer = new OneAnswer<>(answer);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 3;
        this.addRenderableWidget(new StringWidget(cx - 155, y - 24, 310, 12, this.title, this.font));
        this.addRenderableWidget(new MultiLineTextWidget(
                        cx - 155, y, Component.translatable("preheat.cache.folder.move.message", size), this.font)
                .setMaxWidth(310)
                .setCentered(true));
        int by = y + 50;
        this.addRenderableWidget(
                Button.builder(Component.translatable("preheat.cache.folder.move.yes"), b -> choose(Choice.MOVE))
                        .bounds(cx - 155, by, 100, 20)
                        .build());
        this.addRenderableWidget(
                Button.builder(Component.translatable("preheat.cache.folder.move.no"), b -> choose(Choice.START_FRESH))
                        .bounds(cx - 50, by, 100, 20)
                        .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> choose(Choice.CANCEL))
                .bounds(cx + 55, by, 100, 20)
                .build());
    }

    private void choose(Choice choice) {
        answer.accept(choice);
    }

    @Override
    public void onClose() {
        choose(Choice.CANCEL);
        if (this.minecraft.gui.screen() == this) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
