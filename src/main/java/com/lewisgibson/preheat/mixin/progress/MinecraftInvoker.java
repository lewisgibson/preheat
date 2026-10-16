package com.lewisgibson.preheat.mixin.progress;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Minecraft.renderFrame, which is private before 26.2. */
@Mixin(value = Minecraft.class, remap = false)
public interface MinecraftInvoker {
    @Invoker("renderFrame")
    void preheat$renderFrame(boolean advanceGameTime);
}
