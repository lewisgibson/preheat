package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.CacheFolder;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Choosing where shaders are saved ({@link CacheFolderScreen}): a new folder is checked before it is used, the saved
 * shaders are offered for moving, and anything that goes wrong is said in plain words while nothing changes.
 */
final class FolderChoice {
    /** How a message reads: an error, a plain note, success, or a warning. */
    enum Tone {
        ERROR(ChatFormatting.RED),
        NOTE(ChatFormatting.GRAY),
        DONE(ChatFormatting.GREEN),
        WARNING(ChatFormatting.GOLD);

        final ChatFormatting colour;

        Tone(ChatFormatting colour) {
            this.colour = colour;
        }
    }

    /** One line under the folder box. */
    record Message(Words words, Tone tone) {
        Component component() {
            return words.component().copy().withStyle(tone.colour);
        }
    }

    /** The shader cache, as far as choosing its folder goes. */
    interface Cache {
        Path root();

        @Nullable
        FolderProblem problem();

        ProgramBinaryStore.Usage usage();

        /** Gets the folder ready, moving what is saved; {@code done} hears null or why not, in the background. */
        void change(String target, boolean move, Consumer<@Nullable FolderProblem> done);

        /** Switches to the folder, once it is ready. */
        void use(String target);
    }

    /** The screen, as far as choosing a folder goes. */
    interface View {
        void say(Message message);

        /** Asks whether to bring {@code size} of saved shaders along. */
        void askMove(String size, Consumer<MoveCacheScreen.Choice> answer);
    }

    private final Cache cache;
    private final View view;
    private final Executor gameThread;
    private final Consumer<String> changed;
    private boolean busy;

    /**
     * @param gameThread runs the end of a change on the game's thread
     * @param changed hears the new folder setting ("" for the default) once a change has gone through
     */
    FolderChoice(Cache cache, View view, Executor gameThread, Consumer<String> changed) {
        this.cache = cache;
        this.view = view;
        this.gameThread = gameThread;
        this.changed = changed;
    }

    /** What the folder box starts with: the folder chosen, or the one in use when none is (or it cannot be used). */
    String initial() {
        String chosen = PreheatConfig.get().cacheFolder;
        return chosen.isBlank() || cache.problem() != null ? cache.root().toString() : chosen;
    }

    /** Whether a change is under way: the buttons wait, and the screen cannot be left. */
    boolean busy() {
        return busy;
    }

    /** Whether "Use the default" has anything to do. */
    boolean customInUse() {
        return !PreheatConfig.get().cacheFolder.isBlank();
    }

    /**
     * What the screen says: the last thing that happened, or before anything has, why the chosen folder is not used
     * (if it is not).
     */
    static Component shown(@Nullable Message said, @Nullable Words folderWarning) {
        if (said != null) {
            return said.component();
        }
        return folderWarning == null ? Component.empty() : new Message(folderWarning, Tone.WARNING).component();
    }

    /** The folder typed in the box: checked, and if it is a new one, switched to. */
    void useTyped(String typed) {
        String text = typed.trim();
        FolderProblem problem = CacheFolder.check(text);
        if (problem != null) {
            view.say(new Message(Words.translated(problem.key), Tone.ERROR));
            return;
        }
        if (alreadyChosen(text)) {
            view.say(new Message(Words.translated("preheat.cache.folder.same"), Tone.NOTE));
            return;
        }
        choose(text);
    }

    /** Whether {@code text} is the folder already chosen (the default folder itself counts too). */
    private boolean alreadyChosen(String text) {
        Path typed = Path.of(text).toAbsolutePath().normalize();
        String chosen = PreheatConfig.get().cacheFolder.trim();
        if (chosen.isBlank()) {
            return typed.equals(cache.root().toAbsolutePath().normalize());
        }
        return typed.equals(Path.of(chosen).toAbsolutePath().normalize());
    }

    /** Switches to {@code folder} ("" for the default), first asking whether to bring the saved shaders along. */
    void choose(String folder) {
        ProgramBinaryStore.Usage usage = cache.usage();
        if (usage.files() == 0) {
            apply(folder, false);
            return;
        }
        view.askMove(SettingsFacts.size(usage.bytes()), choice -> {
            if (choice != MoveCacheScreen.Choice.CANCEL) {
                apply(folder, choice == MoveCacheScreen.Choice.MOVE);
            }
        });
    }

    private void apply(String folder, boolean move) {
        busy = true;
        view.say(new Message(
                Words.translated(move ? "preheat.cache.folder.moving" : "preheat.cache.folder.switching"), Tone.NOTE));
        cache.change(
                folder,
                move,
                failure -> gameThread.execute(() -> {
                    busy = false;
                    if (failure == null) {
                        cache.use(folder);
                        changed.accept(folder);
                        view.say(new Message(
                                Words.translated(
                                        folder.isBlank()
                                                ? "preheat.cache.folder.done.default"
                                                : "preheat.cache.folder.done"),
                                Tone.DONE));
                    } else {
                        view.say(new Message(Words.translated(failure.key), Tone.ERROR));
                    }
                }));
    }
}
