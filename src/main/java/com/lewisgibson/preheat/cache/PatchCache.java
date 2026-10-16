package com.lewisgibson.preheat.cache;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import org.jspecify.annotations.Nullable;

/** The game's {@link SavedPatches}: Iris's stage type, the render device's depth range and Iris's debug options. */
public final class PatchCache {
    private static final SavedPatches<PatchShaderType> SAVED = new SavedPatches<>(
            PatchShaderType.class,
            ProgramBinaries.CACHE,
            RenderSystem::isOnRenderThread,
            () -> RenderSystem.getDevice().getDeviceInfo().isZZeroToOne(),
            () -> Iris.getIrisConfig().areDebugOptionsEnabled(),
            Boolean.getBoolean("preheat.verifyPatchCache"));

    private PatchCache() {}

    /** Before Iris patches a graphics program: the saved result, or null to let Iris patch (and save what it makes). */
    public static @Nullable Map<PatchShaderType, String> beforeGraphics(
            String name,
            @Nullable String vertex,
            @Nullable String geometry,
            @Nullable String tessControl,
            @Nullable String tessEval,
            @Nullable String fragment,
            Parameters parameters) {
        return SAVED.beforeGraphics(name, vertex, geometry, tessControl, tessEval, fragment, parameters);
    }

    /** Before Iris patches a compute program: the saved result, or null to let Iris patch (and save what it makes). */
    public static @Nullable Map<PatchShaderType, String> beforeCompute(
            String name, @Nullable String compute, Parameters parameters) {
        return SAVED.beforeCompute(name, compute, parameters);
    }

    /** After Iris patched: saves the result under the key {@link #beforeGraphics} or {@link #beforeCompute} worked out. */
    public static void after(@Nullable Map<PatchShaderType, String> result) {
        SAVED.after(result);
    }
}
