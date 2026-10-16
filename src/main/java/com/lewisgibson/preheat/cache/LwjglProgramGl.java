package com.lewisgibson.preheat.cache;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** {@link ProgramGl} and {@link DriverGl} on the game's OpenGL context, through Minecraft's GlStateManager where it has the call. */
final class LwjglProgramGl implements ProgramGl, DriverGl {
    static final LwjglProgramGl INSTANCE = new LwjglProgramGl();

    private static final int GL_PROGRAM_BINARY_LENGTH = 0x8741;

    private LwjglProgramGl() {}

    @Override
    public void clearErrors() {
        GlStateManager.clearGlErrors();
    }

    @Override
    public int error() {
        return GL11C.glGetError();
    }

    @Override
    public int createProgram() {
        return GlStateManager.glCreateProgram();
    }

    @Override
    public void programBinary(int program, int format, byte[] binary) {
        ByteBuffer buf = MemoryUtil.memAlloc(binary.length);
        try {
            buf.put(binary).flip();
            GL41C.glProgramBinary(program, format, buf);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    @Override
    public boolean linked(int program) {
        return GlStateManager.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_TRUE;
    }

    @Override
    public int programInt(int program, int pname) {
        return GL20C.glGetProgrami(program, pname);
    }

    @Override
    public Active activeAttribute(int program, int index) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            String name = GL20C.glGetActiveAttrib(program, index, size, type);
            return new Active(name, type.get(0), size.get(0));
        }
    }

    @Override
    public int attributeLocation(int program, String name) {
        return GL20C.glGetAttribLocation(program, name);
    }

    @Override
    public Active activeUniform(int program, int index) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer size = stack.mallocInt(1);
            IntBuffer type = stack.mallocInt(1);
            String name = GL20C.glGetActiveUniform(program, index, size, type);
            return new Active(name, type.get(0), size.get(0));
        }
    }

    @Override
    public boolean programBinarySupported() {
        return GL.getCapabilities().OpenGL41 || GL.getCapabilities().GL_ARB_get_program_binary;
    }

    @Override
    public int integer(int pname) {
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void integers(int pname, int[] into) {
        GL11C.glGetIntegerv(pname, into);
    }

    @Override
    public @Nullable String string(int name) {
        return GL11C.glGetString(name);
    }

    @Override
    public boolean isProgram(int program) {
        return GL20C.glIsProgram(program);
    }

    @Override
    public void programParameter(int program, int pname, int value) {
        GL41C.glProgramParameteri(program, pname, value);
    }

    @Override
    public int binaryLength(int program) {
        return GlStateManager.glGetProgrami(program, GL_PROGRAM_BINARY_LENGTH);
    }

    @Override
    public Binary binary(int program, int length) {
        ByteBuffer buf = MemoryUtil.memAlloc(length);
        try {
            int[] len = new int[1];
            int[] format = new int[1];
            GL41C.glGetProgramBinary(program, len, format, buf);
            byte[] bytes = new byte[Math.max(0, len[0])];
            buf.get(0, bytes);
            return new Binary(format[0], bytes);
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    @Override
    public void deleteProgram(int program) {
        GlStateManager.glDeleteProgram(program);
    }
}
