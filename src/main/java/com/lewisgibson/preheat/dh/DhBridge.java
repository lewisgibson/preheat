package com.lewisgibson.preheat.dh;

import com.lewisgibson.preheat.mixin.dh.DHCompatInternalAccessor;
import com.lewisgibson.preheat.mixin.keep.DHCompatAccessor;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiFramebuffer;
import com.seibel.distanthorizons.api.interfaces.override.rendering.IDhApiGenericObjectShaderProgram;
import com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector;
import net.irisshaders.iris.compat.dh.DHCompat;
import net.irisshaders.iris.compat.dh.DHCompatInternal;
import org.jspecify.annotations.Nullable;

/** {@link DhOverrides.Internal} on Iris's and Distant Horizons' own classes. Loaded only when Distant Horizons is installed. */
public final class DhBridge implements DhOverrides.Internal {
    private final DHCompatInternal dh;

    private DhBridge(DHCompatInternal dh) {
        this.dh = dh;
    }

    /** The pipeline's DHCompatInternal, or null when it has none. */
    public static DhOverrides.@Nullable Internal of(DHCompat compat) {
        return ((DHCompatAccessor) compat).preheat$internal() instanceof DHCompatInternal dh ? new DhBridge(dh) : null;
    }

    @Override
    public @Nullable Object solidFramebuffer() {
        return dh.getSolidFBWrapper();
    }

    @Override
    public @Nullable Object shadowFramebuffer() {
        return dh.getShadowFBWrapper();
    }

    @Override
    public @Nullable Object genericShader() {
        return dh.getGenericShader();
    }

    @Override
    public void unbindFramebuffer(Object framebuffer) {
        OverrideInjector.INSTANCE.unbind(IDhApiFramebuffer.class, (IDhApiFramebuffer) framebuffer);
    }

    @Override
    public void unbindShader(Object shader) {
        OverrideInjector.INSTANCE.unbind(
                IDhApiGenericObjectShaderProgram.class, (IDhApiGenericObjectShaderProgram) shader);
    }

    @Override
    public void resetForReuse() {
        ((DHCompatInternalAccessor) dh).preheat$setStoredDepthTex(-1);
        ((DHCompatInternalAccessor) dh).preheat$setTranslucentDepthDirty(true);
        DHCompatAccessor.preheat$setLastIncompatible(dh.incompatiblePack());
    }
}
