package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.FolderProblem;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Choosing where shaders are saved: the default folder or one of the player's, typed or picked with the system's
 * folder dialog where the game has one. What happens is {@link FolderChoice}'s; this is the screen it is shown on.
 */
public final class CacheFolderScreen extends Screen implements FolderChoice.View, FolderChoice.Cache {
    private final Screen parent;
    private final FolderChoice choice;
    private final String initial;
    private @Nullable EditBox path;
    private FolderChoice.@Nullable Message status;

    /** {@code changed} hears the new folder setting ("" for the default) once a change has gone through. */
    public CacheFolderScreen(Screen parent, Consumer<String> changed) {
        super(Component.translatable("preheat.cache.folder.title"));
        this.parent = parent;
        this.choice = new FolderChoice(this, this, task -> this.minecraft.execute(task), changed);
        this.initial = choice.initial();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 4;
        this.addRenderableWidget(new StringWidget(cx - 155, y - 30, 310, 12, this.title, this.font));
        this.addRenderableWidget(new StringWidget(
                        cx - 155, y - 13, 310, 10, Component.translatable("preheat.cache.folder.label"), this.font)
                .setMaxWidth(310));
        EditBox previous = path;
        EditBox box =
                new EditBox(this.font, cx - 155, y, 310, 20, Component.translatable("preheat.cache.folder.label"));
        box.setMaxLength(4096);
        box.setValue(previous == null ? initial : previous.getValue());
        path = box;
        this.addRenderableWidget(box);

        Button browse = Button.builder(Component.translatable("preheat.cache.folder.browse"), b -> browse(box))
                .bounds(cx - 155, y + 26, 150, 20)
                .build();
        browse.active = FolderDialogs.GAME.available() && !choice.busy();
        if (!FolderDialogs.GAME.available()) {
            browse.setTooltip(Tooltip.create(Component.translatable("preheat.cache.folder.browse.unavailable")));
        }
        this.addRenderableWidget(browse);
        Button use = Button.builder(
                        Component.translatable("preheat.cache.folder.use"), b -> choice.useTyped(box.getValue()))
                .bounds(cx + 5, y + 26, 150, 20)
                .build();
        use.active = !choice.busy();
        this.addRenderableWidget(use);
        Button useDefault = Button.builder(
                        Component.translatable("preheat.cache.folder.useDefault"), b -> choice.choose(""))
                .bounds(cx - 155, y + 52, 310, 20)
                .build();
        useDefault.active = !choice.busy() && choice.customInUse();
        this.addRenderableWidget(useDefault);

        this.addRenderableWidget(new MultiLineTextWidget(
                        cx - 155, y + 80, FolderChoice.shown(status, SettingsText.folderWarningWords()), this.font)
                .setMaxWidth(310));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx - 100, this.height - 32, 200, 20)
                .build());
    }

    private void browse(EditBox box) {
        if (!FolderDialogs.GAME.pick(
                this.minecraft.getWindow().handle(),
                box.getValue(),
                chosen -> this.minecraft.execute(() -> setPath(chosen)))) {
            say(new FolderChoice.Message(
                    Words.translated("preheat.cache.folder.browse.failed"), FolderChoice.Tone.ERROR));
        }
    }

    /** A folder the dialog chose, put into the box the screen shows now (it may have been rebuilt meanwhile). */
    private void setPath(String chosen) {
        EditBox box = path;
        if (box != null) {
            box.setValue(chosen);
        }
    }

    @Override
    public void say(FolderChoice.Message message) {
        status = message;
        this.rebuildWidgets();
    }

    @Override
    public void askMove(String size, Consumer<MoveCacheScreen.Choice> answer) {
        this.minecraft.gui.setScreen(new MoveCacheScreen(this, size, c -> {
            this.minecraft.gui.setScreen(this);
            answer.accept(c);
        }));
    }

    @Override
    public Path root() {
        return ProgramBinaries.root();
    }

    @Override
    public @Nullable FolderProblem problem() {
        return ProgramBinaries.folderProblem();
    }

    @Override
    public ProgramBinaryStore.Usage usage() {
        return ProgramBinaries.usage();
    }

    @Override
    public void change(String target, boolean move, Consumer<@Nullable FolderProblem> done) {
        ProgramBinaries.changeFolder(target, move, done);
    }

    @Override
    public void use(String target) {
        ProgramBinaries.useFolder(target);
    }

    @Override
    public void onClose() {
        if (!choice.busy()) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
