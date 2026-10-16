package net.irisshaders.preheattest;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

/** The shape of Iris's {@code TextureStageParameters}. */
@SuppressWarnings("UnusedVariable") // read by PatchKey through reflection
public class FakeStageParameters extends FakeParameters {
    private final TextureStage stage;

    public FakeStageParameters(
            Patch patch,
            TextureStage stage,
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
            Set<String> overrides) {
        super(patch, textureMap, overrides);
        this.stage = stage;
    }
}
