package com.lewisgibson.preheat.mixin.cache;

import com.lewisgibson.preheat.cache.PatchCache;
import java.util.Map;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris patches every program's stages here (its own in-memory patch cache is consulted inside, and is never cleared in
 * a session). With a patch saved by an earlier launch the result is returned before Iris patches; without one Iris
 * patches as before and the result is saved ({@link PatchCache}).
 */
@Mixin(value = TransformPatcher.class, remap = false)
public class TransformPatcherCacheMixin {
    @Inject(method = "transform", at = @At("HEAD"), cancellable = true)
    private static void preheat$loadGraphics(
            String name,
            @Nullable String vertex,
            @Nullable String geometry,
            @Nullable String tessControl,
            @Nullable String tessEval,
            @Nullable String fragment,
            Parameters parameters,
            CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        preheat$use(
                PatchCache.beforeGraphics(name, vertex, geometry, tessControl, tessEval, fragment, parameters), cir);
    }

    @Inject(method = "transformCompute", at = @At("HEAD"), cancellable = true)
    private static void preheat$loadCompute(
            String name,
            @Nullable String compute,
            Parameters parameters,
            CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        preheat$use(PatchCache.beforeCompute(name, compute, parameters), cir);
    }

    /** A saved patch is returned in Iris's place; without one Iris patches. */
    @Unique
    private static void preheat$use(
            @Nullable Map<PatchShaderType, String> saved, CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        if (saved != null) {
            cir.setReturnValue(saved);
        }
    }

    @Inject(
            method = {"transform", "transformCompute"},
            at = @At("RETURN"))
    private static void preheat$save(CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        PatchCache.after(cir.getReturnValue());
    }
}
