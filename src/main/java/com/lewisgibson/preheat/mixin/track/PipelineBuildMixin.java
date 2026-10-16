package com.lewisgibson.preheat.mixin.track;

import com.lewisgibson.preheat.build.BuildTracker;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A preparePipeline that has to build (no pipeline for that dimension yet, a shader pack loaded) is one tracked build. */
@Mixin(value = PipelineManager.class, remap = false)
public abstract class PipelineBuildMixin {
    @Shadow
    @Final
    private Map<NamespacedId, WorldRenderingPipeline> pipelinesPerDimension;

    @Inject(method = "preparePipeline", at = @At("HEAD"))
    private void preheat$buildBegins(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        BuildTracker.prepareStarted(
                dimension,
                this.pipelinesPerDimension.containsKey(dimension),
                () -> Iris.getCurrentPack().isPresent());
    }

    @Inject(method = "preparePipeline", at = @At("RETURN"))
    private void preheat$buildEnds(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        BuildTracker.end();
    }
}
