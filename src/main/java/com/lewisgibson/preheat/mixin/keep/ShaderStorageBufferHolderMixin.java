package com.lewisgibson.preheat.mixin.keep;

import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.keep.PipelineManagerExt;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris's fallback after a pipeline fails to build deletes every storage buffer in the game, the kept pipelines'
 * included. The kept pipelines are destroyed properly first, so none is ever reactivated with deleted buffers.
 */
@Mixin(value = ShaderStorageBufferHolder.class, remap = false)
public class ShaderStorageBufferHolderMixin {
    @Inject(method = "forceDeleteBuffers", at = @At("HEAD"))
    private static void preheat$dropKeptFirst(CallbackInfo ci) {
        Features.guard(Feature.KEEP_PIPELINES, () -> {
            if (Iris.getPipelineManager() instanceof PipelineManagerExt m) {
                m.preheat$dropInactive("Iris cleared every storage buffer after a pipeline failed to build");
            }
        });
    }
}
