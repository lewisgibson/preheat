package com.lewisgibson.preheat.progress;

/**
 * The OpenGL calls {@link GlStateSnapshot} reads and puts back state with, as an interface so what it reads and the order
 * it restores in are tested without a driver. {@link LwjglStateGl} is the real one: raw GL where GlStateManager has no
 * call, GlStateManager where it has one, so its cache matches the driver again.
 */
interface StateGl {
    int getInteger(int pname);

    int getIntegeri(int pname, int index);

    long getInteger64i(int pname, int index);

    void getIntegers(int pname, int[] into);

    boolean isEnabled(int cap);

    boolean isEnabledi(int cap, int index);

    boolean getBoolean(int pname);

    float getFloat(int pname);

    void getFloats(int pname, float[] into);

    /** Selects a texture unit while reading, past GlStateManager, whose cache the restore puts right afterwards. */
    void activeTextureUnit(int unit);

    void useProgram(int program);

    void bindFramebuffer(int target, int framebuffer);

    void bindVertexArray(int array);

    void bindBuffer(int target, int buffer);

    /** Binds a buffer to a target GlStateManager does not track (the shader storage buffer). */
    void bindBufferRaw(int target, int buffer);

    void bindBufferBase(int target, int index, int buffer);

    void bindBufferRange(int target, int index, int buffer, long offset, long size);

    void activeTexture(int unit);

    /** Binds a 2D texture on the active unit, through GlStateManager. */
    void bindTexture(int texture);

    /** Binds a texture to a target GlStateManager does not track (arrays, 3D, cube maps). */
    void bindTextureTarget(int target, int texture);

    void bindSampler(int unit, int sampler);

    void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format);

    void viewport(int x, int y, int width, int height);

    void scissorBox(int x, int y, int width, int height);

    void enableScissorTest();

    void disableScissorTest();

    /** Blending on draw buffer 0, as GlStateManager tracks it. */
    void enableBlend();

    void disableBlend();

    void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);

    void blendEquationSeparate(int rgb, int alpha);

    void enableDepthTest();

    void disableDepthTest();

    void depthFunc(int func);

    void depthMask(boolean write);

    void enableCull();

    void disableCull();

    void colorMask(int mask);

    void pixelStore(int pname, int value);

    /** A capability GlStateManager does not track (stencil, sRGB writes). */
    void enable(int cap);

    void disable(int cap);

    void stencilFunc(int func, int ref, int mask);

    void stencilOp(int fail, int depthFail, int pass);

    void stencilMask(int mask);

    void enablePolygonOffset();

    void disablePolygonOffset();

    void polygonOffset(float factor, float units);

    void clearColor(float r, float g, float b, float a);

    /** The colour write mask, one byte per channel (red, green, blue, alpha), non-zero where writing is on. */
    byte[] colorWriteMask();
}
