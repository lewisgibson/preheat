package com.lewisgibson.preheat.mixin.keep;

import net.irisshaders.iris.compat.dh.DHCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DHCompat.class, remap = false)
public interface DHCompatAccessor {
    @Accessor("compatInternalInstance")
    Object preheat$internal();

    @Accessor("lastIncompatible")
    // Mixin replaces this body with the accessor; it is never what runs, and callers must be able to call it.
    @SuppressWarnings("DoNotCallSuggester")
    static void preheat$setLastIncompatible(boolean value) {
        throw new AssertionError();
    }
}
