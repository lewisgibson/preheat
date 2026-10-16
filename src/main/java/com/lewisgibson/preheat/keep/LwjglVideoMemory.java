package com.lewisgibson.preheat.keep;

import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;

/** {@link VideoMemory.Gl} on the game's OpenGL context, and the game's {@link VideoMemory}. */
public final class LwjglVideoMemory implements VideoMemory.Gl {
    public static final VideoMemory GAME = new VideoMemory(new LwjglVideoMemory());

    private LwjglVideoMemory() {}

    @Override
    public @Nullable String renderer() {
        return GL11C.glGetString(GL11C.GL_RENDERER);
    }

    @Override
    public boolean nvxMemoryInfo() {
        return GL.getCapabilities().GL_NVX_gpu_memory_info;
    }

    @Override
    public boolean atiMemInfo() {
        return GL.getCapabilities().GL_ATI_meminfo;
    }

    @Override
    public int error() {
        return GL11C.glGetError();
    }

    @Override
    public int integer(int pname) {
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void integers(int pname, int[] into) {
        GL11C.glGetIntegerv(pname, into);
    }
}
