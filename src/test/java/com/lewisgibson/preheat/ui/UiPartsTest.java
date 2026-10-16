package com.lewisgibson.preheat.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.LogLines;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The small pieces behind the settings screens: words, the folder dialog, the cache buttons, one answer. */
class UiPartsTest {
    @TempDir
    Path tmp;

    @Test
    void wordsBecomeTheGamesTextWithNestedWordsTranslatedToo() {
        Component plain = Words.plain("38 MB").component();
        assertEquals("38 MB", plain.getString());
        Component nested = Words.translated("preheat.cache.folder.notUsed", Words.translated("preheat.problem"), 3)
                .component();
        TranslatableContents t = (TranslatableContents) nested.getContents();
        assertEquals("preheat.cache.folder.notUsed", t.getKey());
        assertEquals("preheat.problem", ((TranslatableContents) ((Component) t.getArgs()[0]).getContents()).getKey());
        assertEquals(3, t.getArgs()[1]);
        assertNull(Words.componentOf(null));
        assertEquals("x", Words.componentOf(Words.plain("x")).getString());
    }

    @Test
    void theFolderDialogOpensOnlyWithSdlAndAFailureIsSaidNotThrown() {
        List<String> opened = new ArrayList<>();
        FolderPicker without = new FolderPicker(false, (w, s, c) -> opened.add(s));
        assertFalse(without.available());
        assertFalse(without.pick(1, "/games", c -> {}));
        FolderPicker with = new FolderPicker(true, (w, s, c) -> {
            opened.add(w + " " + s);
            c.accept("/chosen");
        });
        assertTrue(with.available());
        List<String> chosen = new ArrayList<>();
        assertTrue(with.pick(7, "/games", chosen::add));
        assertEquals(List.of("7 /games"), opened);
        assertEquals(List.of("/chosen"), chosen);
        FolderPicker broken = new FolderPicker(true, (w, s, c) -> {
            throw new UnsatisfiedLinkError("no SDL dialog here");
        });
        try (LogLines log = LogLines.capture()) {
            assertFalse(broken.pick(7, "/games", c -> {}));
            assertTrue(log.has("WARN PREHEAT Could not open the folder dialog; type the folder instead"));
        }
    }

    @Test
    void openingTheCacheFolderMakesItFirstAndAFailureIsSaid() throws Exception {
        List<String> said = new ArrayList<>();
        List<Path> opened = new ArrayList<>();
        Path root = tmp.resolve("preheat").resolve("shader-cache");
        CacheButtons.openFolder(root, opened::add, said::add);
        assertEquals(List.of(root), opened);
        assertTrue(Files.isDirectory(root));
        CacheButtons.openFolder(
                root,
                p -> {
                    throw new IllegalStateException("no file manager");
                },
                said::add);
        Path file = Files.writeString(tmp.resolve("a file"), "x");
        CacheButtons.openFolder(file.resolve("below"), opened::add, said::add);
        assertEquals(List.of("preheat.cache.open.failed", "preheat.cache.open.failed"), said);
    }

    @Test
    void deletingTheCacheSaysHowItWentOnlyAfterAYes() {
        List<String> calls = new ArrayList<>();
        CacheButtons.answered(
                false, done -> calls.add("purged"), Runnable::run, calls::add, () -> calls.add("refresh"));
        assertTrue(calls.isEmpty());
        CacheButtons.answered(true, done -> done.accept(true), Runnable::run, calls::add, () -> calls.add("refresh"));
        CacheButtons.answered(true, done -> done.accept(false), Runnable::run, calls::add, () -> calls.add("refresh"));
        assertEquals(List.of("preheat.cache.deleted", "refresh", "preheat.cache.deleteFailed", "refresh"), calls);
    }

    @Test
    void onlyTheFirstAnswerCounts() {
        List<String> heard = new ArrayList<>();
        OneAnswer<String> answer = new OneAnswer<>(heard::add);
        answer.accept("move");
        answer.accept("cancel");
        assertEquals(List.of("move"), heard);
    }
}
