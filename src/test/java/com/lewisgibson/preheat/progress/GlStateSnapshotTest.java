package com.lewisgibson.preheat.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

/** What a frame drawn mid-build reads from the driver and puts back, against a fake driver. */
class GlStateSnapshotTest {
    private static int index(List<String> calls, String call) {
        int i = calls.indexOf(call);
        assertTrue(i >= 0, call + " in " + calls);
        return i;
    }

    @Test
    void whatWasBoundIsBoundAgainThroughTheSameCalls() {
        FakeStateGl gl = new FakeStateGl();
        gl.ints.put(GL20C.GL_CURRENT_PROGRAM, 7);
        gl.ints.put(GL30C.GL_DRAW_FRAMEBUFFER_BINDING, 3);
        gl.ints.put(GL14C.GL_ACTIVE_TEXTURE, GL14C.GL_TEXTURE0 + 2);
        gl.ints.put(GL11C.GL_DEPTH_FUNC, GL11C.GL_LEQUAL);
        gl.indexed.put(GL31C.GL_UNIFORM_BUFFER_BINDING + "/1", 11);
        gl.longs.put(GL31C.GL_UNIFORM_BUFFER_START + "/1", 256L);
        gl.longs.put(GL31C.GL_UNIFORM_BUFFER_SIZE + "/1", 64L);
        gl.indexed.put(GL31C.GL_UNIFORM_BUFFER_BINDING + "/2", 12);
        gl.indexed.put(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING + "/0", 21);
        gl.longs.put(GL43C.GL_SHADER_STORAGE_BUFFER_SIZE + "/0", 1024L);
        gl.indexed.put(GL42C.GL_IMAGE_BINDING_NAME + "/0", 31);
        gl.indexed.put(GL42C.GL_IMAGE_BINDING_FORMAT + "/0", GL30C.GL_RGBA16F);
        gl.indexed.put(GL42C.GL_IMAGE_BINDING_NAME + "/1", 32);
        gl.indexed.put(GL42C.GL_IMAGE_BINDING_LAYERED + "/0", 1);
        gl.indexed.put(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING + "/1", 22);
        gl.arrays.put(GL11C.GL_VIEWPORT, new int[] {0, 0, 1920, 1080});
        gl.mask = new byte[] {1, 0, 1, 0};

        GlStateSnapshot s = GlStateSnapshot.capture(gl);
        assertEquals("unit " + (GL14C.GL_TEXTURE0 + 2), gl.calls.get(gl.calls.size() - 1), "the active unit is left");
        gl.calls.clear();
        s.restore(gl);
        List<String> c = gl.calls;
        assertEquals("useProgram 7", c.get(0));
        assertEquals("bindFramebuffer " + GL30C.GL_DRAW_FRAMEBUFFER + " 3", c.get(1));
        assertTrue(c.contains("bindBufferRange " + GL31C.GL_UNIFORM_BUFFER + " 1 11 256 64"), "a bound range");
        assertTrue(c.contains("bindBufferBase " + GL31C.GL_UNIFORM_BUFFER + " 2 12"), "a binding with no size");
        assertTrue(c.contains("bindBufferBase " + GL31C.GL_UNIFORM_BUFFER + " 0 0"), "nothing bound");
        assertTrue(c.contains("bindBufferRange " + GL43C.GL_SHADER_STORAGE_BUFFER + " 0 21 0 1024"));
        assertTrue(c.contains("bindBufferBase " + GL43C.GL_SHADER_STORAGE_BUFFER + " 1 22"), "a binding with no size");
        assertTrue(c.contains("bindImageTexture 0 31 0 true 0 0 " + GL30C.GL_RGBA16F), "a layered image");
        assertTrue(c.contains("bindImageTexture 1 32 0 false 0 0 " + GL30C.GL_R8), "no format reads back as zero");
        assertTrue(c.contains("viewport 0 0 1920 1080"));
        assertTrue(c.contains("colorMask 5"), "red and blue");
        assertTrue(c.contains("depthFunc " + GL11C.GL_LEQUAL));
        assertTrue(
                index(c, "bindTexture 0") > index(c, "activeTexture " + GL14C.GL_TEXTURE0),
                "a unit's texture is bound after the unit is selected");
        assertEquals(
                "activeTexture " + (GL14C.GL_TEXTURE0 + 2),
                c.get(c.lastIndexOf("bindBufferRaw " + GL43C.GL_SHADER_STORAGE_BUFFER + " 0") - 1),
                "the active unit is selected again after the other targets");
    }

    @Test
    void everySwitchIsPutBackTheWayItWas() {
        FakeStateGl on = new FakeStateGl();
        for (int cap : new int[] {
            GL11C.GL_SCISSOR_TEST,
            GL11C.GL_BLEND,
            GL11C.GL_DEPTH_TEST,
            GL11C.GL_DEPTH_WRITEMASK,
            GL11C.GL_CULL_FACE,
            GL11C.GL_STENCIL_TEST,
            GL11C.GL_POLYGON_OFFSET_FILL,
            GL30C.GL_FRAMEBUFFER_SRGB
        }) {
            on.enabled.add(cap);
        }
        on.floats.put(GL11C.GL_POLYGON_OFFSET_FACTOR, 1.5f);
        on.floats.put(GL11C.GL_COLOR_CLEAR_VALUE * 10 + 3, 1f);
        GlStateSnapshot.capture(on).restore(on);
        for (String call : List.of(
                "enableScissorTest",
                "enableBlend",
                "enableDepthTest",
                "depthMask true",
                "enableCull",
                "enable " + GL11C.GL_STENCIL_TEST,
                "enablePolygonOffset",
                "polygonOffset 1.5 0.0",
                "clearColor 0.0 0.0 0.0 1.0",
                "enable " + GL30C.GL_FRAMEBUFFER_SRGB)) {
            assertTrue(on.calls.contains(call), call + " in " + on.calls);
        }

        FakeStateGl off = new FakeStateGl();
        off.mask = new byte[] {0, 1, 0, 1};
        GlStateSnapshot.capture(off).restore(off);
        assertTrue(off.calls.contains("colorMask 10"), "green and alpha");
        for (String call : List.of(
                "disableScissorTest",
                "disableBlend",
                "disableDepthTest",
                "depthMask false",
                "disableCull",
                "disable " + GL11C.GL_STENCIL_TEST,
                "disablePolygonOffset",
                "disable " + GL30C.GL_FRAMEBUFFER_SRGB)) {
            assertTrue(off.calls.contains(call), call + " in " + off.calls);
        }
        assertFalse(off.calls.contains("enableBlend"));
    }
}
