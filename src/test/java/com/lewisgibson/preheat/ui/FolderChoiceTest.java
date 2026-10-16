package com.lewisgibson.preheat.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.cache.ProgramBinaryStore;
import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.config.FolderProblem;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Choosing the cache folder, against a fake cache and screen. */
class FolderChoiceTest {
    @TempDir
    Path tmp;

    final List<String> calls = new ArrayList<>();
    final List<FolderChoice.Message> said = new ArrayList<>();
    final List<Runnable> later = new ArrayList<>();
    ProgramBinaryStore.Usage usage = new ProgramBinaryStore.Usage(0, 0);

    @Nullable
    FolderProblem problem;

    @Nullable
    FolderProblem changeFails;

    MoveCacheScreen.Choice answer = MoveCacheScreen.Choice.MOVE;
    Path root;

    final FolderChoice.Cache cache = new FolderChoice.Cache() {
        @Override
        public Path root() {
            return root;
        }

        @Override
        public @Nullable FolderProblem problem() {
            return problem;
        }

        @Override
        public ProgramBinaryStore.Usage usage() {
            return usage;
        }

        @Override
        public void change(String target, boolean move, Consumer<@Nullable FolderProblem> done) {
            calls.add("change '" + target + "' " + (move ? "moving" : "fresh"));
            done.accept(changeFails);
        }

        @Override
        public void use(String target) {
            calls.add("use '" + target + "'");
        }
    };

    final FolderChoice.View view = new FolderChoice.View() {
        @Override
        public void say(FolderChoice.Message message) {
            said.add(message);
        }

        @Override
        public void askMove(String size, Consumer<MoveCacheScreen.Choice> answered) {
            calls.add("ask " + size);
            answered.accept(answer);
        }
    };

    FolderChoice choice;

    @BeforeEach
    void setUp() {
        ConfigFiles.use(tmp.resolve("preheat.json"));
        root = tmp.resolve("game").resolve("preheat").resolve("shader-cache");
        choice = new FolderChoice(cache, view, later::add, folder -> calls.add("changed '" + folder + "'"));
    }

    @AfterEach
    void tearDown() {
        ConfigFiles.use(null);
    }

    private String lastKey() {
        return String.valueOf(said.get(said.size() - 1).words().key());
    }

    private void runLater() {
        List<Runnable> now = new ArrayList<>(later);
        later.clear();
        now.forEach(Runnable::run);
    }

    @Test
    void theBoxStartsWithTheFolderInUseUnlessOneUsableIsChosen() {
        assertEquals(root.toString(), choice.initial());
        assertFalse(choice.customInUse(), "the default is in use");
        PreheatConfig.change(c -> c.cacheFolder = "/games/cache");
        assertEquals("/games/cache", choice.initial());
        assertTrue(choice.customInUse());
        problem = FolderProblem.NOT_WRITABLE;
        assertEquals(root.toString(), choice.initial());
    }

    @Test
    void aFolderThatCannotBeUsedOrIsAlreadyChosenChangesNothing() throws Exception {
        choice.useTyped("relative/path");
        assertEquals(FolderProblem.NOT_FULL_PATH.key, lastKey());
        assertEquals(FolderChoice.Tone.ERROR, said.get(0).tone());
        Files.createDirectories(root);
        choice.useTyped(" " + root + " ");
        assertEquals("preheat.cache.folder.same", lastKey(), "the default folder itself");
        Path chosen = Files.createDirectories(tmp.resolve("chosen"));
        PreheatConfig.change(c -> c.cacheFolder = chosen.toString());
        choice.useTyped(chosen.toString());
        assertEquals("preheat.cache.folder.same", lastKey());
        assertTrue(calls.isEmpty(), calls.toString());
    }

    @Test
    void anEmptyCacheSwitchesStraightAway() throws Exception {
        Path chosen = Files.createDirectories(tmp.resolve("chosen"));
        choice.useTyped(chosen.toString());
        assertTrue(choice.busy());
        assertEquals("preheat.cache.folder.switching", lastKey());
        runLater();
        assertFalse(choice.busy());
        assertEquals(
                List.of("change '" + chosen + "' fresh", "use '" + chosen + "'", "changed '" + chosen + "'"), calls);
        assertEquals("preheat.cache.folder.done", lastKey());
        assertEquals(FolderChoice.Tone.DONE, said.get(said.size() - 1).tone());
    }

    @Test
    void whatIsSavedIsMovedOrLeftAsThePlayerSaysOrNothingHappens() {
        usage = new ProgramBinaryStore.Usage(3, 38L << 20);
        choice.choose("");
        runLater();
        assertEquals(List.of("ask 38 MB", "change '' moving", "use ''", "changed ''"), calls);
        assertEquals("preheat.cache.folder.done.default", lastKey());
        assertEquals(
                "preheat.cache.folder.moving",
                String.valueOf(said.get(0).words().key()));
        calls.clear();
        answer = MoveCacheScreen.Choice.START_FRESH;
        choice.choose("");
        runLater();
        assertEquals("change '' fresh", calls.get(1));
        calls.clear();
        answer = MoveCacheScreen.Choice.CANCEL;
        choice.choose("");
        assertEquals(List.of("ask 38 MB"), calls);
    }

    @Test
    void aFailedChangeSaysWhyAndSwitchesNothing() {
        changeFails = FolderProblem.MOVE_FAILED;
        choice.choose("");
        runLater();
        assertEquals(List.of("change '' fresh"), calls);
        assertEquals(FolderProblem.MOVE_FAILED.key, lastKey());
        assertFalse(choice.busy());
    }

    @Test
    void theScreenShowsWhatHappenedOrWhyTheChosenFolderIsNotUsed() {
        assertEquals("", FolderChoice.shown(null, null).getString());
        Component warning = FolderChoice.shown(null, Words.plain("not used"));
        assertEquals("not used", warning.getString());
        assertEquals(Style.EMPTY.withColor(ChatFormatting.GOLD), warning.getStyle());
        Component error = FolderChoice.shown(
                new FolderChoice.Message(Words.plain("cannot"), FolderChoice.Tone.ERROR), Words.plain("not used"));
        assertEquals("cannot", error.getString());
        assertEquals(Style.EMPTY.withColor(ChatFormatting.RED), error.getStyle());
    }
}
