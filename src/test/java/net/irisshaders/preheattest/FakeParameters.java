package net.irisshaders.preheattest;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Set;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.texture.TextureStage;

/**
 * The shape of Iris's {@code Parameters} (whose real class needs glsl-transformer, which Iris ships nested in its jar):
 * the same base fields, under Iris's package prefix so PatchKey writes it down the way it writes Iris's own.
 */
// Read by PatchKey through reflection, as Iris's own private fields are.
@SuppressWarnings("UnusedVariable")
public abstract class FakeParameters {
    public final Patch patch;
    private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap;
    private final Set<String> textureOverrides;
    public PatchShaderType type;
    public String name;

    protected FakeParameters(
            Patch patch,
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap,
            Set<String> overrides) {
        this.patch = patch;
        this.textureMap = textureMap;
        this.textureOverrides = overrides;
    }
}
