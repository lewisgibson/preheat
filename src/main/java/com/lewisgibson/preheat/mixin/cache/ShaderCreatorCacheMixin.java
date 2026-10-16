package com.lewisgibson.preheat.mixin.cache;

import com.lewisgibson.preheat.build.BuildTimer;
import com.lewisgibson.preheat.build.BuildTracker;
import com.lewisgibson.preheat.cache.BinaryCache;
import com.lewisgibson.preheat.cache.CacheHooks;
import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.Features;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.irisshaders.iris.pipeline.programs.PartialShader;
import net.irisshaders.iris.pipeline.programs.ShaderCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris's gbuffer and shadow programs. Their attribute locations come from the vertex format's elements (and whether
 * the program is a fallback), so those are part of the key. A loaded program has no shader objects, which
 * PartialShader already allows (it detaches only handles of 0 or more). Iris checks these links later, so the binary
 * of a compiled one is saved when the build ends.
 */
@Mixin(value = ShaderCreator.class, remap = false)
public class ShaderCreatorCacheMixin {
    @Unique
    private static final ThreadLocal<String> preheat$key = new ThreadLocal<>();

    @Inject(method = "link", at = @At("HEAD"), cancellable = true)
    private static void preheat$load(
            String name,
            String vertex,
            String geometry,
            String tessControl,
            String tessEval,
            String fragment,
            VertexFormat vertexFormat,
            boolean isFallback,
            CallbackInfoReturnable<PartialShader> cir) {
        preheat$key.remove();
        List<String> parts = new ArrayList<>();
        String key = CacheHooks.beforeCompile(
                () -> ProgramBinaries.keyFor(
                        "gbuffer",
                        () -> BinaryCache.attributes(
                                vertexFormat.getElements().stream()
                                        .map(VertexFormatElement::name)
                                        .toList(),
                                isFallback),
                        List.of(
                                "vertex",
                                String.valueOf(vertex),
                                "geometry",
                                String.valueOf(geometry),
                                "tessControl",
                                String.valueOf(tessControl),
                                "tessEval",
                                String.valueOf(tessEval),
                                "fragment",
                                String.valueOf(fragment)),
                        parts),
                ProgramBinaries::load,
                program -> {
                    cir.setReturnValue(new PartialShader(program, -1, -1, -1, -1, -1));
                    // The tracking mixin's link step (if it ran first) ends here: the RETURN hooks do not run for a
                    // cancelled call.
                    BuildTracker.TIMER.exit(BuildTimer.Step.LINK);
                    BuildTracker.TIMER.program(true);
                },
                k -> ProgramBinaries.debugMiss(String.valueOf(name), k, parts),
                ProgramBinaries::discard);
        if (key != null) {
            preheat$key.set(key);
        }
    }

    @Inject(method = "link", at = @At("RETURN"))
    private static void preheat$saveLater(CallbackInfoReturnable<PartialShader> cir) {
        String key = preheat$key.get();
        preheat$key.remove();
        CacheHooks.afterCompile(
                key,
                () -> ProgramBinaries.saveLater(
                        ((PartialShaderAccessor) (Object) cir.getReturnValue()).preheat$program(),
                        Objects.requireNonNull(key)));
    }

    @ModifyArg(
            method = "link",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/mojang/renderpearl/backend/opengl/GlStateManager;glLinkProgram(I)V"))
    private static int preheat$retrievable(int program) {
        Features.guard(Feature.PROGRAM_CACHE, () -> ProgramBinaries.retrievable(program));
        return program;
    }
}
