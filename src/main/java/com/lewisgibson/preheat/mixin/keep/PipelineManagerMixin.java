package com.lewisgibson.preheat.mixin.keep;

import com.lewisgibson.preheat.build.BuildTracker;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import com.lewisgibson.preheat.diag.FrameWatch;
import com.lewisgibson.preheat.keep.DimensionChange;
import com.lewisgibson.preheat.keep.IrisReactivation;
import com.lewisgibson.preheat.keep.KeptBudget;
import com.lewisgibson.preheat.keep.KeptPipelines;
import com.lewisgibson.preheat.keep.LwjglVideoMemory;
import com.lewisgibson.preheat.keep.PipelineManagerExt;
import java.util.List;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.DimensionId;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris already keeps a pipeline per dimension in PipelineManager, but its own hook on Minecraft.updateLevelInEngines
 * calls destroyPipeline() on every dimension change, which destroys all of them, and the new dimension's pipeline is
 * then built from nothing on the render thread. On that one call path this keeps the map and only puts the current
 * pipeline aside; preparePipeline then finds the destination's pipeline already built and brings it back. Every other
 * destroyPipeline() (shader reload, pack change, Distant Horizons toggled, F3+R) runs untouched and destroys them all.
 *
 * <p>Only the dimensions switched on in the settings are kept: leaving a dimension that is switched off destroys its
 * pipeline as stock Iris would, and a kept pipeline whose dimension has been switched off since is dropped at the next
 * change.
 *
 * <p>If putting a pipeline aside or bringing one back ever throws, keeping is turned off for the session and that
 * dimension's pipeline is built fresh, exactly as stock Iris would.
 */
@Mixin(value = PipelineManager.class, remap = false)
public abstract class PipelineManagerMixin
        implements PipelineManagerExt, KeptPipelines.Host<NamespacedId, WorldRenderingPipeline> {
    @Shadow
    @Final
    private Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension;

    @Shadow
    private WorldRenderingPipeline pipeline;

    // Written here so Sodium sees the change Iris's own destroy would have signalled; read by Iris itself.
    @SuppressWarnings("UnusedVariable")
    @Shadow
    private int versionCounterForSodiumShaderReload;

    @Unique
    private @Nullable KeptPipelines<NamespacedId, WorldRenderingPipeline> preheat$kept;

    /** Made on first use rather than in the constructor, so the manager is whole when it is handed over. */
    @Unique
    private KeptPipelines<NamespacedId, WorldRenderingPipeline> preheat$kept() {
        KeptPipelines<NamespacedId, WorldRenderingPipeline> k = this.preheat$kept;
        if (k == null) {
            k = new KeptPipelines<>(this);
            this.preheat$kept = k;
        }
        return k;
    }

    @Shadow
    protected abstract void resetTextureState();

    @Shadow
    public abstract void destroyPipeline();

    @Shadow
    public abstract WorldRenderingPipeline preparePipeline(NamespacedId currentDimension);

    @Inject(method = "destroyPipeline", at = @At("HEAD"), cancellable = true)
    private void preheat$keepOnDimensionChange(CallbackInfo ci) {
        if (!DimensionChange.isCaller()) {
            return;
        }
        FrameWatch.GAME.changing(Iris.lastDimension, Iris.getCurrentDimension());
        if (this.preheat$kept().onDimensionChange()) {
            ci.cancel();
        }
    }

    @Inject(method = "preparePipeline", at = @At("HEAD"))
    private void preheat$beforePrepare(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        this.preheat$kept().beforePrepare(dimension);
    }

    @Inject(method = "preparePipeline", at = @At("RETURN"), cancellable = true)
    private void preheat$afterPrepare(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        if (!this.preheat$kept().afterPrepare(dimension)) {
            // Back to stock: destroy every pipeline and build this dimension afresh.
            this.destroyPipeline();
            cir.setReturnValue(this.preparePipeline(dimension));
        }
    }

    @Override
    public void preheat$dropInactive(String why) {
        this.preheat$kept().dropInactive(why);
    }

    @Override
    public boolean preheat$isBuilt(NamespacedId dimension) {
        return this.pipelinesPerDimension.containsKey(dimension);
    }

    @Override
    public void preheat$prebuild(List<NamespacedId> dimensions) {
        this.preheat$kept()
                .prebuildFrom(
                        dimensions,
                        this.pipeline,
                        this.pipeline instanceof IrisRenderingPipeline,
                        Iris.getCurrentPack().isPresent());
    }

    @Override
    public Map<NamespacedId, WorldRenderingPipeline> preheat$pipelines() {
        return this.pipelinesPerDimension;
    }

    @Override
    public @Nullable WorldRenderingPipeline preheat$active() {
        return this.pipeline;
    }

    @Override
    @SuppressWarnings("NullAway") // Iris's own field, null between a destroy and the next prepare
    public void preheat$setActive(@Nullable WorldRenderingPipeline p) {
        this.pipeline = p;
    }

    @Override
    public void preheat$deactivate(WorldRenderingPipeline p) {
        IrisReactivation.deactivate(p);
    }

    @Override
    public void preheat$reactivate(WorldRenderingPipeline p) {
        IrisReactivation.reactivate(p);
    }

    @Override
    public void preheat$resetTextureState() {
        this.resetTextureState();
    }

    @Override
    public void preheat$destroy(NamespacedId id, WorldRenderingPipeline p) {
        // As destroyPipeline() does for each entry.
        Iris.logger.info("Destroying pipeline {}", id);
        p.destroy();
    }

    @Override
    public void preheat$signalSodium() {
        this.versionCounterForSodiumShaderReload++;
    }

    @Override
    public boolean preheat$dimensionOn(NamespacedId id) {
        return PreheatConfig.get().dimensionOn(String.valueOf(id));
    }

    @Override
    public boolean preheat$vanilla(NamespacedId id) {
        // The Overworld, the Nether and the End (Iris's own End key too) are let go last.
        return KnownDimensions.letGoLast(String.valueOf(id), id.equals(DimensionId.END));
    }

    @Override
    public KeptBudget.@Nullable Memory preheat$memory() {
        return LwjglVideoMemory.GAME.read();
    }

    @Override
    public WorldRenderingPipeline preheat$build(NamespacedId dimension) {
        List<ShaderStorageBuffer> buffers = ShaderStorageBufferHolderAccessor.preheat$active();
        int buffersBefore = buffers.size();
        BuildTracker.begin(String.valueOf(dimension));
        try {
            // Built directly rather than through Iris.createPipeline: a failure here must not trip Iris's fallback,
            // which deletes every storage buffer, the active pipeline's included, and turns shaders off.
            return new IrisRenderingPipeline(Iris.getCurrentPack().orElseThrow().getProgramSet(dimension));
        } catch (RuntimeException | LinkageError e) {
            // The storage buffers the failed constructor made are not anyone's: delete them, and only them.
            KeptPipelines.dropAddedSince(
                    buffers, buffersBefore, b -> ((ShaderStorageBufferAccessor) b).preheat$destroy());
            throw e;
        } finally {
            BuildTracker.end();
        }
    }

    @Override
    public long preheat$nanoTime() {
        return System.nanoTime();
    }
}
