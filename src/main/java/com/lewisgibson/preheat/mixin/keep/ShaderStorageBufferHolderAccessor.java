package com.lewisgibson.preheat.mixin.keep;

import java.util.List;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ShaderStorageBufferHolder.class, remap = false)
public interface ShaderStorageBufferHolderAccessor {
    @Accessor("buffers")
    ShaderStorageBuffer[] preheat$buffers();

    @Accessor("cachedWidth")
    int preheat$width();

    @Accessor("cachedHeight")
    int preheat$height();

    /** Every storage buffer any pipeline holds, in creation order. */
    @SuppressWarnings("DoNotCallSuggester")
    @Accessor("ACTIVE_BUFFERS")
    static List<ShaderStorageBuffer> preheat$active() {
        throw new AssertionError();
    }
}
