package com.lewisgibson.preheat.progress;

import org.lwjgl.sdl.SDLEvents;

/**
 * Moves the window's pending messages into SDL's queue without handling them (Minecraft 26.3 and later run on SDL).
 * Loaded only where SDL is present. Minecraft registers no SDL event watcher or filter, so no game code runs here;
 * Minecraft handles the queued events on its next frame. On GLFW (Minecraft 26.2 and earlier) there is no way to pump
 * without dispatching input callbacks mid-build, so nothing is pumped there.
 */
final class SdlPump {
    private SdlPump() {}

    static void pump() {
        SDLEvents.SDL_PumpEvents();
    }
}
