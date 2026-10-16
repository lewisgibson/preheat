package com.lewisgibson.preheat.mixin.keep;

import java.nio.ByteBuffer;
import net.irisshaders.iris.gl.buffer.BuiltShaderStorageInfo;
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ShaderStorageBuffer.class, remap = false)
public interface ShaderStorageBufferAccessor {
    @Accessor("content")
    ByteBuffer preheat$content();

    @Accessor("info")
    BuiltShaderStorageInfo preheat$info();

    @Invoker("destroy")
    void preheat$destroy();
}
