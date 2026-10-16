package com.lewisgibson.preheat.progress;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.system.MemoryStack;

/** {@link StateGl} on the game's OpenGL context. Render thread only. */
final class LwjglStateGl implements StateGl {
    static final LwjglStateGl INSTANCE = new LwjglStateGl();

    private LwjglStateGl() {}

    @Override
    public int getInteger(int pname) {
        return GL11C.glGetInteger(pname);
    }

    @Override
    public int getIntegeri(int pname, int index) {
        return GL30C.glGetIntegeri(pname, index);
    }

    @Override
    public long getInteger64i(int pname, int index) {
        return GL33C.glGetInteger64i(pname, index);
    }

    @Override
    public void getIntegers(int pname, int[] into) {
        GL11C.glGetIntegerv(pname, into);
    }

    @Override
    public boolean isEnabled(int cap) {
        return GL11C.glIsEnabled(cap);
    }

    @Override
    public boolean isEnabledi(int cap, int index) {
        return GL30C.glIsEnabledi(cap, index);
    }

    @Override
    public boolean getBoolean(int pname) {
        return GL11C.glGetBoolean(pname);
    }

    @Override
    public float getFloat(int pname) {
        return GL11C.glGetFloat(pname);
    }

    @Override
    public void getFloats(int pname, float[] into) {
        GL11C.glGetFloatv(pname, into);
    }

    @Override
    public void activeTextureUnit(int unit) {
        GL14C.glActiveTexture(unit);
    }

    @Override
    public void useProgram(int program) {
        GlStateManager._glUseProgram(program);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GlStateManager._glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void bindVertexArray(int array) {
        GlStateManager._glBindVertexArray(array);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GlStateManager._glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferRaw(int target, int buffer) {
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        GL30C.glBindBufferBase(target, index, buffer);
    }

    @Override
    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        GL30C.glBindBufferRange(target, index, buffer, offset, size);
    }

    @Override
    public void activeTexture(int unit) {
        GlStateManager._activeTexture(unit);
    }

    @Override
    public void bindTexture(int texture) {
        GlStateManager._bindTexture(texture);
    }

    @Override
    public void bindTextureTarget(int target, int texture) {
        GL11C.glBindTexture(target, texture);
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        GL33C.glBindSampler(unit, sampler);
    }

    @Override
    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    @Override
    public void scissorBox(int x, int y, int width, int height) {
        GlStateManager._scissorBox(x, y, width, height);
    }

    @Override
    public void enableScissorTest() {
        GlStateManager._enableScissorTest();
    }

    @Override
    public void disableScissorTest() {
        GlStateManager._disableScissorTest();
    }

    @Override
    public void enableBlend() {
        GlStateManager._enableBlend(0);
    }

    @Override
    public void disableBlend() {
        GlStateManager._disableBlend(0);
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int rgb, int alpha) {
        GlStateManager._blendEquationSeparate(rgb, alpha);
    }

    @Override
    public void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    @Override
    public void disableDepthTest() {
        GlStateManager._disableDepthTest();
    }

    @Override
    public void depthFunc(int func) {
        GlStateManager._depthFunc(func);
    }

    @Override
    public void depthMask(boolean write) {
        GlStateManager._depthMask(write);
    }

    @Override
    public void enableCull() {
        GlStateManager._enableCull();
    }

    @Override
    public void disableCull() {
        GlStateManager._disableCull();
    }

    @Override
    public void colorMask(int mask) {
        GlStateManager._colorMask(mask);
    }

    @Override
    public void pixelStore(int pname, int value) {
        GlStateManager._pixelStore(pname, value);
    }

    @Override
    public void enable(int cap) {
        GL11C.glEnable(cap);
    }

    @Override
    public void disable(int cap) {
        GL11C.glDisable(cap);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        GL11C.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int depthFail, int pass) {
        GL11C.glStencilOp(fail, depthFail, pass);
    }

    @Override
    public void stencilMask(int mask) {
        GL11C.glStencilMask(mask);
    }

    @Override
    public void enablePolygonOffset() {
        GlStateManager._enablePolygonOffset();
    }

    @Override
    public void disablePolygonOffset() {
        GlStateManager._disablePolygonOffset();
    }

    @Override
    public void polygonOffset(float factor, float units) {
        GlStateManager._polygonOffset(factor, units);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        GL11C.glClearColor(r, g, b, a);
    }

    @Override
    public byte[] colorWriteMask() {
        byte[] mask = new byte[4];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer b = stack.malloc(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, b);
            b.get(mask);
        }
        return mask;
    }
}
