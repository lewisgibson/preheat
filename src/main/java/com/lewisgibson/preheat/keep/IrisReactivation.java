package com.lewisgibson.preheat.keep;

import com.lewisgibson.preheat.dh.DhBridge;
import com.lewisgibson.preheat.dh.DhOverrides;
import com.lewisgibson.preheat.mixin.keep.IrisRenderingPipelineAccessor;
import com.lewisgibson.preheat.mixin.keep.RenderTargetsAccessor;
import com.lewisgibson.preheat.mixin.keep.ShaderStorageBufferAccessor;
import com.lewisgibson.preheat.mixin.keep.ShaderStorageBufferHolderAccessor;
import com.lewisgibson.preheat.mixin.keep.ShadowRenderTargetsAccessor;
import com.lewisgibson.preheat.platform.Platform;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.util.Arrays;
import java.util.List;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.sampler.SamplerLimits;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.ClearPass;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.irisshaders.iris.vertices.sodium.terrain.FormatAnalyzer;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/** {@link Reactivation.Iris} on Iris's own classes, and the game's {@link Reactivation}. */
public final class IrisReactivation
        implements Reactivation.Iris<WorldRenderingPipeline, ShaderStorageBuffer, ComputeProgram> {
    private static final int GL_R8 = 0x8229;
    private static final int GL_RED = 0x1903;
    private static final int GL_UNSIGNED_BYTE = 0x1401;

    private static final Reactivation<WorldRenderingPipeline, ShaderStorageBuffer, ComputeProgram> GAME =
            new Reactivation<>(new IrisReactivation(), Platform.get().isLoaded("distanthorizons"));

    private IrisReactivation() {}

    /** The GL state and Distant Horizons bindings destroy() would have released, without freeing anything. */
    public static void deactivate(WorldRenderingPipeline pipeline) {
        GAME.deactivate(pipeline);
    }

    /** Called when a kept pipeline becomes the active one again. */
    public static void reactivate(WorldRenderingPipeline pipeline) {
        GAME.reactivate(pipeline);
    }

    private static IrisRenderingPipelineAccessor state(WorldRenderingPipeline pipeline) {
        return (IrisRenderingPipelineAccessor) pipeline;
    }

    private static ShaderStorageBufferHolderAccessor holder(WorldRenderingPipeline pipeline) {
        return (ShaderStorageBufferHolderAccessor) state(pipeline).preheat$ssbos();
    }

    @Override
    public boolean isIris(WorldRenderingPipeline pipeline) {
        return pipeline instanceof IrisRenderingPipeline;
    }

    @Override
    public void activeTexture(int unit) {
        GlStateManager._activeTexture(unit);
    }

    @Override
    public void unbindAllSamplers() {
        IrisRenderSystem.unbindAllSamplers();
    }

    @Override
    public void bindTexture(int texture) {
        GlStateManager._bindTexture(texture);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        GlStateManager._glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GlStateManager._glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        IrisRenderSystem.bindBufferBase(target, index, buffer);
    }

    @Override
    public boolean hasStorageBuffers(WorldRenderingPipeline pipeline) {
        return state(pipeline).preheat$ssbos() != null;
    }

    @Override
    public List<@Nullable ShaderStorageBuffer> storageBuffers(WorldRenderingPipeline pipeline) {
        return Arrays.asList(holder(pipeline).preheat$buffers());
    }

    @Override
    public int storageWidth(WorldRenderingPipeline pipeline) {
        return holder(pipeline).preheat$width();
    }

    @Override
    public int storageHeight(WorldRenderingPipeline pipeline) {
        return holder(pipeline).preheat$height();
    }

    @Override
    public int index(ShaderStorageBuffer buffer) {
        return buffer.getIndex();
    }

    @Override
    public int id(ShaderStorageBuffer buffer) {
        return buffer.getId();
    }

    @Override
    public boolean relative(ShaderStorageBuffer buffer) {
        return ((ShaderStorageBufferAccessor) buffer).preheat$info().relative();
    }

    @Override
    public void resize(ShaderStorageBuffer buffer, int width, int height) {
        buffer.resizeIfRelative(width, height);
    }

    @Override
    public boolean hasContent(ShaderStorageBuffer buffer) {
        return ((ShaderStorageBufferAccessor) buffer).preheat$content() != null;
    }

    @Override
    public void uploadContent(ShaderStorageBuffer buffer) {
        GlStateManager._glBufferSubData(
                Reactivation.GL_SHADER_STORAGE_BUFFER, 0L, ((ShaderStorageBufferAccessor) buffer).preheat$content());
    }

    @Override
    public void zero(ShaderStorageBuffer buffer) {
        IrisRenderSystem.clearBufferSubData(
                Reactivation.GL_SHADER_STORAGE_BUFFER,
                GL_R8,
                0L,
                ((ShaderStorageBufferAccessor) buffer).preheat$info().size(),
                GL_RED,
                GL_UNSIGNED_BYTE,
                new int[] {0});
    }

    @Override
    public void setupBuffers(WorldRenderingPipeline pipeline) {
        state(pipeline).preheat$ssbos().setupBuffers();
    }

    @Override
    public int maxStorageUnits() {
        return SamplerLimits.get().getMaxShaderStorageUnits();
    }

    @Override
    public void resetSystemTime() {
        SystemTimeUniforms.COUNTER.reset();
        SystemTimeUniforms.TIMER.reset();
    }

    @Override
    public boolean reloadRequired() {
        return WorldRenderingSettings.INSTANCE.isReloadRequired();
    }

    @Override
    public boolean levelLoaded() {
        return Minecraft.getInstance().levelExtractor != null;
    }

    @Override
    public void remeshLevel() {
        Minecraft.getInstance().levelExtractor.allChanged();
    }

    @Override
    public void clearReloadRequired() {
        WorldRenderingSettings.INSTANCE.clearReloadRequired();
    }

    @Override
    public void applyPackSettings(WorldRenderingPipeline pipeline) {
        IrisRenderingPipeline iris = (IrisRenderingPipeline) pipeline;
        PackDirectives directives = state(pipeline).preheat$packDirectives();
        ShaderPack pack = state(pipeline).preheat$pack();
        // The block ids are set again by beginLevelRendering on the next frame, as for a new pipeline, which also
        // remeshes the level.
        WorldRenderingSettings settings = WorldRenderingSettings.INSTANCE;
        settings.setVertexFormat(FormatAnalyzer.createFormat(true, true, true, true));
        settings.setEntityIds(pack.getIdMap().getEntityIdMap());
        settings.setItemIds(pack.getIdMap().getItemIdMap());
        settings.setAmbientOcclusionLevel(directives.getAmbientOcclusionLevel());
        settings.setDisableDirectionalShading(iris.shouldDisableDirectionalShading());
        settings.setUseSeparateAo(directives.shouldUseSeparateAo());
        settings.setBreaksAnisotropy(directives.breaksAnisotropy());
        settings.setVoxelizeLightBlocks(directives.shouldVoxelizeLightBlocks());
        settings.setSeparateEntityDraws(directives.shouldUseSeparateEntityDraws());
        state(pipeline).preheat$setInitializedBlockIds(false);
    }

    @Override
    public void clearCustomImages(WorldRenderingPipeline pipeline) {
        state(pipeline).preheat$customImages().forEach(GlImage::clear);
    }

    @Override
    public void requireFullClear(WorldRenderingPipeline pipeline) {
        ((RenderTargetsAccessor) state(pipeline).preheat$renderTargets()).preheat$setFullClearRequired(true);
    }

    @Override
    public boolean hasShadowTargets(WorldRenderingPipeline pipeline) {
        return state(pipeline).preheat$shadowRenderTargets() != null;
    }

    @Override
    public void requireShadowFullClear(WorldRenderingPipeline pipeline) {
        ((ShadowRenderTargetsAccessor) state(pipeline).preheat$shadowRenderTargets())
                .preheat$setFullClearRequired(true);
    }

    @Override
    public List<@Nullable ComputeProgram> setupPrograms(WorldRenderingPipeline pipeline) {
        return Arrays.asList(state(pipeline).preheat$setup());
    }

    @Override
    public void fullClear(WorldRenderingPipeline pipeline) {
        state(pipeline).preheat$renderTargets().onFullClear();
        Vector3d fog = CapturedRenderingState.INSTANCE.getFogColor();
        Vector4f fogColor = new Vector4f((float) fog.x, (float) fog.y, (float) fog.z, 1.0F);
        for (ClearPass pass : state(pipeline).preheat$clearPassesFull()) {
            pass.execute(fogColor);
        }
    }

    @Override
    public void dispatch(ComputeProgram program) {
        program.use();
        program.dispatch(1.0F, 1.0F);
    }

    @Override
    public void unbindCompute() {
        ComputeProgram.unbind();
    }

    @Override
    public DhOverrides.@Nullable Internal distantHorizons(WorldRenderingPipeline pipeline) {
        return DhBridge.of(((IrisRenderingPipeline) pipeline).getDHCompat());
    }
}
