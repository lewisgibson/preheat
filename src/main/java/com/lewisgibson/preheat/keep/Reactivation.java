package com.lewisgibson.preheat.keep;

import com.lewisgibson.preheat.dh.DhOverrides;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Puts a kept pipeline aside and brings it back so that, once back, it is in the state a freshly built pipeline for
 * that dimension would be in. Each step mirrors something IrisRenderingPipeline's constructor, PipelineManager's
 * creation branch or IrisRenderingPipeline.destroy() does in Iris 1.11.6 and 1.11.7; README's "How it works" lists
 * them. {@link IrisReactivation} is the game's, on Iris's own classes.
 *
 * @param <P> a pipeline
 * @param <B> a shader storage buffer
 * @param <C> a compute program
 */
public final class Reactivation<P, B, C> {
    static final int GL_TEXTURE0 = 0x84C0;
    static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    static final int GL_FRAMEBUFFER = 0x8D40;
    static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;

    /** What reactivation does to Iris, its pipelines and the driver: every call, none of the decisions. */
    public interface Iris<P, B, C> {
        /** Whether the pipeline is Iris's own (a vanilla pipeline has nothing to put aside). */
        boolean isIris(P pipeline);

        void activeTexture(int unit);

        void unbindAllSamplers();

        void bindTexture(int texture);

        void bindFramebuffer(int target, int framebuffer);

        void bindBuffer(int target, int buffer);

        void bindBufferBase(int target, int index, int buffer);

        /** Whether the pipeline holds shader storage buffers (a holder), even if every slot is empty. */
        boolean hasStorageBuffers(P pipeline);

        List<@Nullable B> storageBuffers(P pipeline);

        int storageWidth(P pipeline);

        int storageHeight(P pipeline);

        int index(B buffer);

        int id(B buffer);

        /** Whether the buffer's size follows the screen's. */
        boolean relative(B buffer);

        /** Reallocates, zeroes and rebinds a relative buffer, as at creation. */
        void resize(B buffer, int width, int height);

        /** Whether the buffer was declared with content of its own. */
        boolean hasContent(B buffer);

        void uploadContent(B buffer);

        void zero(B buffer);

        void setupBuffers(P pipeline);

        int maxStorageUnits();

        /** PipelineManager.preparePipeline resets these whenever it builds a pipeline. */
        void resetSystemTime();

        boolean reloadRequired();

        boolean levelLoaded();

        /** The chunks have to be meshed again. */
        void remeshLevel();

        void clearReloadRequired();

        /** The global chunk-meshing settings, in the constructor's order, and the block ids to be set again. */
        void applyPackSettings(P pipeline);

        void clearCustomImages(P pipeline);

        void requireFullClear(P pipeline);

        boolean hasShadowTargets(P pipeline);

        void requireShadowFullClear(P pipeline);

        /** The pack's setup programs, in order; a slot may be empty. */
        List<@Nullable C> setupPrograms(P pipeline);

        /** A full clear to the fog colour, as before the setup programs at creation. */
        void fullClear(P pipeline);

        void dispatch(C program);

        void unbindCompute();

        DhOverrides.@Nullable Internal distantHorizons(P pipeline);
    }

    private final Iris<P, B, C> iris;
    private final boolean distantHorizons;

    /** {@code distantHorizons}: whether Distant Horizons is installed (its classes are touched only then). */
    public Reactivation(Iris<P, B, C> iris, boolean distantHorizons) {
        this.iris = iris;
        this.distantHorizons = distantHorizons;
    }

    /** The GL state and Distant Horizons bindings destroy() would have released, without freeing anything. */
    public void deactivate(P pipeline) {
        if (!iris.isIris(pipeline)) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            iris.activeTexture(GL_TEXTURE0 + i);
            iris.unbindAllSamplers();
            iris.bindTexture(0);
        }
        iris.activeTexture(GL_TEXTURE0);
        iris.bindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        iris.bindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        iris.bindFramebuffer(GL_FRAMEBUFFER, 0);
        if (iris.hasStorageBuffers(pipeline)) {
            for (B buffer : iris.storageBuffers(pipeline)) {
                if (buffer != null) {
                    iris.bindBufferBase(GL_SHADER_STORAGE_BUFFER, iris.index(buffer), 0);
                }
            }
        }
        if (distantHorizons) {
            DhOverrides.unbind(iris.distantHorizons(pipeline));
        }
    }

    /** Called when a kept pipeline becomes the active one again. */
    public void reactivate(P pipeline) {
        iris.resetSystemTime();
        if (iris.isIris(pipeline)) {
            reactivateIris(pipeline);
        }
        // preparePipeline's creation branch: a changed setting means the chunks have to be meshed again.
        if (iris.reloadRequired()) {
            if (iris.levelLoaded()) {
                iris.remeshLevel();
            }
            iris.clearReloadRequired();
        }
    }

    private void reactivateIris(P pipeline) {
        iris.applyPackSettings(pipeline);

        // Shader storage buffers: a new pipeline creates them zeroed (or holding their declared content) and binds
        // them; with none, it unbinds every unit.
        if (iris.hasStorageBuffers(pipeline)) {
            for (B buffer : iris.storageBuffers(pipeline)) {
                if (buffer != null) {
                    resetBuffer(buffer, iris.storageWidth(pipeline), iris.storageHeight(pipeline));
                }
            }
            iris.bindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            iris.setupBuffers(pipeline);
        } else {
            for (int i = 0; i < Math.min(16, iris.maxStorageUnits()); i++) {
                iris.bindBufferBase(GL_SHADER_STORAGE_BUFFER, i, 0);
            }
        }

        // Custom images (Complementary's colored-light voxel and flood-fill volumes) are cleared when created.
        iris.clearCustomImages(pipeline);

        // Render targets start with a full clear, the shadow targets too.
        iris.requireFullClear(pipeline);
        if (iris.hasShadowTargets(pipeline)) {
            iris.requireShadowFullClear(pipeline);
        }

        // The pack's setup programs run once at creation, after a full clear to the fog colour.
        boolean ran = false;
        for (C program : iris.setupPrograms(pipeline)) {
            if (program == null) {
                continue;
            }
            if (!ran) {
                ran = true;
                iris.fullClear(pipeline);
            }
            iris.dispatch(program);
        }
        if (ran) {
            iris.unbindCompute();
        }

        if (distantHorizons) {
            DhOverrides.reset(iris.distantHorizons(pipeline));
        }
    }

    private void resetBuffer(B buffer, int width, int height) {
        if (iris.relative(buffer)) {
            iris.resize(buffer, width, height);
            return;
        }
        iris.bindBuffer(GL_SHADER_STORAGE_BUFFER, iris.id(buffer));
        if (iris.hasContent(buffer)) {
            iris.uploadContent(buffer);
        } else {
            iris.zero(buffer);
        }
    }
}
