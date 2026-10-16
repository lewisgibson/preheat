package com.lewisgibson.preheat.mixin.cache;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ProgramBuilder.class, remap = false)
public interface ProgramBuilderAccessor {
    @Invoker("<init>")
    // Mixin replaces this body with the accessor; it is never what runs, and callers must be able to call it.
    @SuppressWarnings("DoNotCallSuggester")
    static ProgramBuilder preheat$new(String name, int program, ImmutableSet<Integer> reservedTextureUnits) {
        throw new AssertionError();
    }

    @Accessor("program")
    int preheat$program();
}
