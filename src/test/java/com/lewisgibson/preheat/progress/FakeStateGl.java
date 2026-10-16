package com.lewisgibson.preheat.progress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A driver for {@link GlStateSnapshot}: answers reads from what the test set, and records every other call. */
final class FakeStateGl implements StateGl {
    final Map<Integer, Integer> ints = new HashMap<>();
    final Map<String, Integer> indexed = new HashMap<>();
    final Map<String, Long> longs = new HashMap<>();
    final Map<Integer, Float> floats = new HashMap<>();
    final Set<Integer> enabled = new HashSet<>();
    final Map<Integer, int[]> arrays = new HashMap<>();
    byte[] mask = {1, 1, 1, 1};
    final List<String> calls = new ArrayList<>();

    private void call(String name, Object... args) {
        StringBuilder sb = new StringBuilder(name);
        for (Object a : args) {
            sb.append(' ').append(a);
        }
        calls.add(sb.toString());
    }

    @Override
    public int getInteger(int pname) {
        return ints.getOrDefault(pname, 0);
    }

    @Override
    public int getIntegeri(int pname, int index) {
        return indexed.getOrDefault(pname + "/" + index, 0);
    }

    @Override
    public long getInteger64i(int pname, int index) {
        return longs.getOrDefault(pname + "/" + index, 0L);
    }

    @Override
    public void getIntegers(int pname, int[] into) {
        int[] v = arrays.getOrDefault(pname, new int[into.length]);
        System.arraycopy(v, 0, into, 0, into.length);
    }

    @Override
    public boolean isEnabled(int cap) {
        return enabled.contains(cap);
    }

    @Override
    public boolean isEnabledi(int cap, int index) {
        return enabled.contains(cap) && index == 0;
    }

    @Override
    public boolean getBoolean(int pname) {
        return enabled.contains(pname);
    }

    @Override
    public float getFloat(int pname) {
        return floats.getOrDefault(pname, 0f);
    }

    @Override
    public void getFloats(int pname, float[] into) {
        for (int i = 0; i < into.length; i++) {
            into[i] = floats.getOrDefault(pname * 10 + i, 0f);
        }
    }

    @Override
    public void activeTextureUnit(int unit) {
        call("unit", unit);
    }

    @Override
    public void useProgram(int program) {
        call("useProgram", program);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        call("bindFramebuffer", target, framebuffer);
    }

    @Override
    public void bindVertexArray(int array) {
        call("bindVertexArray", array);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        call("bindBuffer", target, buffer);
    }

    @Override
    public void bindBufferRaw(int target, int buffer) {
        call("bindBufferRaw", target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        call("bindBufferBase", target, index, buffer);
    }

    @Override
    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        call("bindBufferRange", target, index, buffer, offset, size);
    }

    @Override
    public void activeTexture(int unit) {
        call("activeTexture", unit);
    }

    @Override
    public void bindTexture(int texture) {
        call("bindTexture", texture);
    }

    @Override
    public void bindTextureTarget(int target, int texture) {
        call("bindTextureTarget", target, texture);
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        call("bindSampler", unit, sampler);
    }

    @Override
    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        call("bindImageTexture", unit, texture, level, layered, layer, access, format);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        call("viewport", x, y, width, height);
    }

    @Override
    public void scissorBox(int x, int y, int width, int height) {
        call("scissorBox", x, y, width, height);
    }

    @Override
    public void enableScissorTest() {
        call("enableScissorTest");
    }

    @Override
    public void disableScissorTest() {
        call("disableScissorTest");
    }

    @Override
    public void enableBlend() {
        call("enableBlend");
    }

    @Override
    public void disableBlend() {
        call("disableBlend");
    }

    @Override
    public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        call("blendFuncSeparate", srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquationSeparate(int rgb, int alpha) {
        call("blendEquationSeparate", rgb, alpha);
    }

    @Override
    public void enableDepthTest() {
        call("enableDepthTest");
    }

    @Override
    public void disableDepthTest() {
        call("disableDepthTest");
    }

    @Override
    public void depthFunc(int func) {
        call("depthFunc", func);
    }

    @Override
    public void depthMask(boolean write) {
        call("depthMask", write);
    }

    @Override
    public void enableCull() {
        call("enableCull");
    }

    @Override
    public void disableCull() {
        call("disableCull");
    }

    @Override
    public void colorMask(int mask) {
        call("colorMask", mask);
    }

    @Override
    public void pixelStore(int pname, int value) {
        call("pixelStore", pname, value);
    }

    @Override
    public void enable(int cap) {
        call("enable", cap);
    }

    @Override
    public void disable(int cap) {
        call("disable", cap);
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        call("stencilFunc", func, ref, mask);
    }

    @Override
    public void stencilOp(int fail, int depthFail, int pass) {
        call("stencilOp", fail, depthFail, pass);
    }

    @Override
    public void stencilMask(int mask) {
        call("stencilMask", mask);
    }

    @Override
    public void enablePolygonOffset() {
        call("enablePolygonOffset");
    }

    @Override
    public void disablePolygonOffset() {
        call("disablePolygonOffset");
    }

    @Override
    public void polygonOffset(float factor, float units) {
        call("polygonOffset", factor, units);
    }

    @Override
    public void clearColor(float r, float g, float b, float a) {
        call("clearColor", r, g, b, a);
    }

    @Override
    public byte[] colorWriteMask() {
        return mask.clone();
    }
}
