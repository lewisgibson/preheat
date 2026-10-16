package com.lewisgibson.preheat.progress;

import com.lewisgibson.preheat.Classes;
import com.lewisgibson.preheat.mixin.progress.MinecraftInvoker;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/** The game's {@link Frames}: Minecraft's own frame, its render thread and window, and SDL's message pump. */
public final class FrameRenderer {
    static final Frames<Minecraft> FRAMES = new Frames<>(
            System::nanoTime,
            RenderSystem::isOnRenderThread,
            Minecraft::getInstance,
            mc -> mc.level != null,
            mc -> mc.getWindow() != null,
            mc -> ((MinecraftInvoker) mc).preheat$renderFrame(false),
            LwjglStateGl.INSTANCE,
            // A lambda, so SDL's classes are looked up only when it runs, which is only where they are present.
            () -> SdlPump.pump(),
            Classes.present("org.lwjgl.sdl.SDLEvents"));

    private FrameRenderer() {}

    /** Called at the start and end of every frame (the mixin on Minecraft.renderFrame). */
    public static void enterFrame() {
        FRAMES.enterFrame();
    }

    public static void exitFrame() {
        FRAMES.exitFrame();
    }
}
