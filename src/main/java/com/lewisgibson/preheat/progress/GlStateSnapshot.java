package com.lewisgibson.preheat.progress;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

/**
 * The GL state a loading-screen frame can change, read from the driver before the frame and put back after it, so
 * a frame drawn in the middle of Iris building a pipeline leaves that build exactly where it was: the program, the
 * framebuffers, the vertex array and buffer bindings, the uniform buffer bindings Minecraft's GUI uses, the textures
 * and samplers of the first units, the viewport and scissor, blending, depth, culling, the colour mask and the pixel
 * transfer settings. State that GlStateManager tracks is restored through it, so its cache matches the driver again.
 * The calls themselves go through {@link StateGl}, so what is read and put back, and in which order, is tested.
 */
final class GlStateSnapshot {
    private static final int UNITS = 8;
    private static final int UNIFORM_BINDINGS = 8;

    private int program;
    private int drawFramebuffer;
    private int readFramebuffer;
    private int vertexArray;
    private int arrayBuffer;
    private int pixelPackBuffer;
    private int pixelUnpackBuffer;
    private int uniformBuffer;
    private final int[] uniformBindings = new int[UNIFORM_BINDINGS];
    private final long[] uniformOffsets = new long[UNIFORM_BINDINGS];
    private final long[] uniformSizes = new long[UNIFORM_BINDINGS];
    private int activeTexture;
    private final int[] textures = new int[UNITS];
    private final int[] samplers = new int[UNITS];
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private boolean scissor;
    private boolean blend;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private boolean depthTest;
    private int depthFunc;
    private boolean depthMask;
    private boolean cull;
    private int colorMask;
    private final int[] pixelStore = new int[PIXEL_STORE.length];

    // Iris's own state, which a pack's programs use and the GUI frame may touch: storage buffers, image units, the
    // other texture targets, stencil, polygon offset, the clear colour and sRGB writes.
    private int storageBuffer;
    private final int[] storageBindings = new int[UNITS];
    private final long[] storageOffsets = new long[UNITS];
    private final long[] storageSizes = new long[UNITS];
    private final int[][] images = new int[UNITS][6];
    private final int[][] otherTextures = new int[UNITS][OTHER_TARGETS.length];
    private boolean stencil;
    private final int[] stencilState = new int[STENCIL.length];
    private boolean polygonOffset;
    private float polygonFactor;
    private float polygonUnits;
    private final float[] clearColor = new float[4];
    private boolean srgb;

    private static final int[] PIXEL_STORE = {
        GL11C.GL_UNPACK_ALIGNMENT, GL11C.GL_UNPACK_ROW_LENGTH, GL11C.GL_UNPACK_SKIP_ROWS, GL11C.GL_UNPACK_SKIP_PIXELS,
        GL11C.GL_PACK_ALIGNMENT, GL11C.GL_PACK_ROW_LENGTH, GL11C.GL_PACK_SKIP_ROWS, GL11C.GL_PACK_SKIP_PIXELS
    };

    private static final int[] OTHER_TARGETS = {
        GL30C.GL_TEXTURE_2D_ARRAY, GL12C.GL_TEXTURE_3D, GL13C.GL_TEXTURE_CUBE_MAP
    };
    private static final int[] OTHER_BINDINGS = {
        GL30C.GL_TEXTURE_BINDING_2D_ARRAY, GL12C.GL_TEXTURE_BINDING_3D, GL13C.GL_TEXTURE_BINDING_CUBE_MAP
    };
    private static final int[] STENCIL = {
        GL11C.GL_STENCIL_FUNC,
        GL11C.GL_STENCIL_REF,
        GL11C.GL_STENCIL_VALUE_MASK,
        GL11C.GL_STENCIL_FAIL,
        GL11C.GL_STENCIL_PASS_DEPTH_FAIL,
        GL11C.GL_STENCIL_PASS_DEPTH_PASS,
        GL11C.GL_STENCIL_WRITEMASK
    };
    private static final int[] IMAGE = {
        GL42C.GL_IMAGE_BINDING_NAME, GL42C.GL_IMAGE_BINDING_LEVEL, GL42C.GL_IMAGE_BINDING_LAYERED,
        GL42C.GL_IMAGE_BINDING_LAYER, GL42C.GL_IMAGE_BINDING_ACCESS, GL42C.GL_IMAGE_BINDING_FORMAT
    };

    static GlStateSnapshot capture(StateGl gl) {
        GlStateSnapshot s = new GlStateSnapshot();
        s.program = gl.getInteger(GL20C.GL_CURRENT_PROGRAM);
        s.drawFramebuffer = gl.getInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        s.readFramebuffer = gl.getInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        s.vertexArray = gl.getInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        s.arrayBuffer = gl.getInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        s.pixelPackBuffer = gl.getInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        s.pixelUnpackBuffer = gl.getInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        s.uniformBuffer = gl.getInteger(GL31C.GL_UNIFORM_BUFFER_BINDING);
        for (int i = 0; i < UNIFORM_BINDINGS; i++) {
            s.uniformBindings[i] = gl.getIntegeri(GL31C.GL_UNIFORM_BUFFER_BINDING, i);
            s.uniformOffsets[i] = gl.getInteger64i(GL31C.GL_UNIFORM_BUFFER_START, i);
            s.uniformSizes[i] = gl.getInteger64i(GL31C.GL_UNIFORM_BUFFER_SIZE, i);
        }
        s.activeTexture = gl.getInteger(GL14C.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < UNITS; i++) {
            gl.activeTextureUnit(GL14C.GL_TEXTURE0 + i);
            s.textures[i] = gl.getInteger(GL11C.GL_TEXTURE_BINDING_2D);
            s.samplers[i] = gl.getInteger(GL33C.GL_SAMPLER_BINDING);
        }
        gl.activeTextureUnit(s.activeTexture);
        gl.getIntegers(GL11C.GL_VIEWPORT, s.viewport);
        gl.getIntegers(GL11C.GL_SCISSOR_BOX, s.scissorBox);
        s.scissor = gl.isEnabled(GL11C.GL_SCISSOR_TEST);
        s.blend = gl.isEnabledi(GL11C.GL_BLEND, 0);
        s.blendSrcRgb = gl.getInteger(GL14C.GL_BLEND_SRC_RGB);
        s.blendDstRgb = gl.getInteger(GL14C.GL_BLEND_DST_RGB);
        s.blendSrcAlpha = gl.getInteger(GL14C.GL_BLEND_SRC_ALPHA);
        s.blendDstAlpha = gl.getInteger(GL14C.GL_BLEND_DST_ALPHA);
        s.blendEquationRgb = gl.getInteger(GL20C.GL_BLEND_EQUATION_RGB);
        s.blendEquationAlpha = gl.getInteger(GL20C.GL_BLEND_EQUATION_ALPHA);
        s.depthTest = gl.isEnabled(GL11C.GL_DEPTH_TEST);
        s.depthFunc = gl.getInteger(GL11C.GL_DEPTH_FUNC);
        s.depthMask = gl.getBoolean(GL11C.GL_DEPTH_WRITEMASK);
        s.cull = gl.isEnabled(GL11C.GL_CULL_FACE);
        byte[] mask = gl.colorWriteMask();
        s.colorMask = (mask[0] != 0 ? 1 : 0) | (mask[1] != 0 ? 2 : 0) | (mask[2] != 0 ? 4 : 0) | (mask[3] != 0 ? 8 : 0);
        for (int i = 0; i < PIXEL_STORE.length; i++) {
            s.pixelStore[i] = gl.getInteger(PIXEL_STORE[i]);
        }
        s.storageBuffer = gl.getInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
        for (int i = 0; i < UNITS; i++) {
            s.storageBindings[i] = gl.getIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, i);
            s.storageOffsets[i] = gl.getInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_START, i);
            s.storageSizes[i] = gl.getInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_SIZE, i);
            for (int k = 0; k < IMAGE.length; k++) {
                s.images[i][k] = gl.getIntegeri(IMAGE[k], i);
            }
            gl.activeTextureUnit(GL14C.GL_TEXTURE0 + i);
            for (int k = 0; k < OTHER_BINDINGS.length; k++) {
                s.otherTextures[i][k] = gl.getInteger(OTHER_BINDINGS[k]);
            }
        }
        gl.activeTextureUnit(s.activeTexture);
        s.stencil = gl.isEnabled(GL11C.GL_STENCIL_TEST);
        for (int i = 0; i < STENCIL.length; i++) {
            s.stencilState[i] = gl.getInteger(STENCIL[i]);
        }
        s.polygonOffset = gl.isEnabled(GL11C.GL_POLYGON_OFFSET_FILL);
        s.polygonFactor = gl.getFloat(GL11C.GL_POLYGON_OFFSET_FACTOR);
        s.polygonUnits = gl.getFloat(GL11C.GL_POLYGON_OFFSET_UNITS);
        gl.getFloats(GL11C.GL_COLOR_CLEAR_VALUE, s.clearColor);
        s.srgb = gl.isEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        return s;
    }

    void restore(StateGl gl) {
        gl.useProgram(program);
        gl.bindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        gl.bindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
        gl.bindVertexArray(vertexArray);
        gl.bindBuffer(GL15C.GL_ARRAY_BUFFER, arrayBuffer);
        gl.bindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pixelPackBuffer);
        gl.bindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
        for (int i = 0; i < UNIFORM_BINDINGS; i++) {
            if (uniformBindings[i] == 0 || uniformSizes[i] == 0) {
                gl.bindBufferBase(GL31C.GL_UNIFORM_BUFFER, i, uniformBindings[i]);
            } else {
                gl.bindBufferRange(GL31C.GL_UNIFORM_BUFFER, i, uniformBindings[i], uniformOffsets[i], uniformSizes[i]);
            }
        }
        gl.bindBuffer(GL31C.GL_UNIFORM_BUFFER, uniformBuffer);
        for (int i = 0; i < UNITS; i++) {
            gl.activeTexture(GL14C.GL_TEXTURE0 + i);
            gl.bindTexture(textures[i]);
            gl.bindSampler(i, samplers[i]);
        }
        gl.activeTexture(activeTexture);
        gl.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        gl.scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (scissor) {
            gl.enableScissorTest();
        } else {
            gl.disableScissorTest();
        }
        if (blend) {
            gl.enableBlend();
        } else {
            gl.disableBlend();
        }
        gl.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        gl.blendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        if (depthTest) {
            gl.enableDepthTest();
        } else {
            gl.disableDepthTest();
        }
        gl.depthFunc(depthFunc);
        gl.depthMask(depthMask);
        if (cull) {
            gl.enableCull();
        } else {
            gl.disableCull();
        }
        gl.colorMask(colorMask);
        for (int i = 0; i < PIXEL_STORE.length; i++) {
            gl.pixelStore(PIXEL_STORE[i], pixelStore[i]);
        }
        for (int i = 0; i < UNITS; i++) {
            if (storageBindings[i] == 0 || storageSizes[i] == 0) {
                gl.bindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, i, storageBindings[i]);
            } else {
                gl.bindBufferRange(
                        GL43C.GL_SHADER_STORAGE_BUFFER, i, storageBindings[i], storageOffsets[i], storageSizes[i]);
            }
            int[] im = images[i];
            gl.bindImageTexture(i, im[0], im[1], im[2] != 0, im[3], im[4], im[5] == 0 ? GL30C.GL_R8 : im[5]);
            gl.activeTexture(GL14C.GL_TEXTURE0 + i);
            for (int k = 0; k < OTHER_TARGETS.length; k++) {
                gl.bindTextureTarget(OTHER_TARGETS[k], otherTextures[i][k]);
            }
        }
        gl.activeTexture(activeTexture);
        gl.bindBufferRaw(GL43C.GL_SHADER_STORAGE_BUFFER, storageBuffer);
        if (stencil) {
            gl.enable(GL11C.GL_STENCIL_TEST);
        } else {
            gl.disable(GL11C.GL_STENCIL_TEST);
        }
        gl.stencilFunc(stencilState[0], stencilState[1], stencilState[2]);
        gl.stencilOp(stencilState[3], stencilState[4], stencilState[5]);
        gl.stencilMask(stencilState[6]);
        if (polygonOffset) {
            gl.enablePolygonOffset();
        } else {
            gl.disablePolygonOffset();
        }
        gl.polygonOffset(polygonFactor, polygonUnits);
        gl.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        if (srgb) {
            gl.enable(GL30C.GL_FRAMEBUFFER_SRGB);
        } else {
            gl.disable(GL30C.GL_FRAMEBUFFER_SRGB);
        }
    }
}
