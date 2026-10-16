package com.lewisgibson.preheat.mixin.keep;

import com.google.common.collect.ImmutableList;
import java.util.Set;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.ClearPass;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pipeline state its constructor sets up once, which a reused pipeline has to set up again. */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public interface IrisRenderingPipelineAccessor {
    @Accessor("packDirectives")
    PackDirectives preheat$packDirectives();

    @Accessor("pack")
    ShaderPack preheat$pack();

    @Accessor("initializedBlockIds")
    void preheat$setInitializedBlockIds(boolean value);

    @Accessor("shaderStorageBufferHolder")
    ShaderStorageBufferHolder preheat$ssbos();

    @Accessor("customImages")
    Set<GlImage> preheat$customImages();

    @Accessor("renderTargets")
    RenderTargets preheat$renderTargets();

    @Accessor("shadowRenderTargets")
    ShadowRenderTargets preheat$shadowRenderTargets();

    @Accessor("setup")
    ComputeProgram[] preheat$setup();

    @Accessor("clearPassesFull")
    ImmutableList<ClearPass> preheat$clearPassesFull();
}
