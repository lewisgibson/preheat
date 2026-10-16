package net.irisshaders.preheattest;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

/** The shape of Iris's {@code SodiumParameters}. */
public class FakeSodiumParameters extends FakeParameters {
    public final AlphaTest alpha;
    public final boolean shadow;

    public FakeSodiumParameters(
            Patch patch,
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
            Set<String> overrides,
            AlphaTest alpha,
            boolean shadow) {
        super(patch, textureMap, overrides);
        this.alpha = alpha;
        this.shadow = shadow;
    }
}
