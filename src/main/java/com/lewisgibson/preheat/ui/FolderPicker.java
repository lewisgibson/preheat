package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.Preheat;
import java.util.function.Consumer;

/**
 * The system's own folder dialog, on games that run on SDL (Minecraft 26.3 and later). The dialog is SDL's, opened
 * over the game window; the choice arrives on SDL's callback and is handed back to the render thread. Where SDL is
 * absent the folder is typed instead. {@link FolderDialogs} is the game's.
 */
final class FolderPicker {
    /** Opens the dialog over a window, starting in a folder. */
    interface Dialog {
        void show(long window, String start, Consumer<String> chosen);
    }

    private final boolean sdl;
    private final Dialog dialog;

    FolderPicker(boolean sdl, Dialog dialog) {
        this.sdl = sdl;
        this.dialog = dialog;
    }

    boolean available() {
        return sdl;
    }

    /** Opens the dialog; returns false when it could not be opened (the screen then says so). */
    boolean pick(long window, String start, Consumer<String> chosen) {
        if (!sdl) {
            return false;
        }
        try {
            dialog.show(window, start, chosen);
            return true;
        } catch (RuntimeException | LinkageError e) {
            Preheat.LOG.warn("PREHEAT Could not open the folder dialog; type the folder instead", e);
            return false;
        }
    }
}
