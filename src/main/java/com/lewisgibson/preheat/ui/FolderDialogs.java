package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.Classes;

/** The game's {@link FolderPicker}: SDL's dialog where SDL is present. */
final class FolderDialogs {
    // A lambda, so SDL's classes are looked up only when it runs, which is only where they are present.
    static final FolderPicker GAME = new FolderPicker(
            Classes.present("org.lwjgl.sdl.SDLDialog"),
            (window, start, chosen) -> SdlFolderDialog.show(window, start, chosen));

    private FolderDialogs() {}
}
