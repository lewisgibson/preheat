package com.lewisgibson.preheat.ui;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;

/** SDL's folder dialog. Loaded only where SDL is present ({@link FolderPicker}). */
final class SdlFolderDialog {
    /**
     * The last dialog's callback. It is never freed from inside its own invocation (the upcall stub is still running
     * then); the next dialog frees it instead, so at most one small stub is ever held.
     */
    private static @Nullable SDL_DialogFileCallback last;

    private SdlFolderDialog() {}

    static synchronized void show(long window, @Nullable String start, Consumer<String> chosen) {
        SDL_DialogFileCallback previous = last;
        if (previous != null) {
            previous.free();
        }
        SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            // filelist is a NULL-terminated array of UTF-8 paths; NULL itself means an error, an empty list a cancel.
            if (filelist != MemoryUtil.NULL) {
                long first = MemoryUtil.memGetAddress(filelist);
                if (first != MemoryUtil.NULL) {
                    chosen.accept(MemoryUtil.memUTF8(first));
                }
            }
        });
        last = callback;
        SDLDialog.SDL_ShowOpenFolderDialog(
                callback, MemoryUtil.NULL, window, start == null || start.isBlank() ? null : start, false);
    }
}
