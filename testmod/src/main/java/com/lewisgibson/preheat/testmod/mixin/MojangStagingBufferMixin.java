package com.lewisgibson.preheat.testmod.mixin;

import com.lewisgibson.preheat.testmod.TestMod;
import com.mojang.renderpearl.api.device.DeviceFeatures;
import net.caffeinemc.mods.sodium.client.gpu.arena.staging.MojangStagingBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mesa's D3D12 driver in WSL crashes (libgallium, native) on Sodium's first write into its persistently mapped
 * staging buffer. The lab sends Sodium down its own non-mapped fallback, which uploads through writeToBuffer. Chunk
 * uploads only; nothing in Iris's pipeline goes through it.
 */
@Mixin(value = MojangStagingBuffer.class, remap = false)
public class MojangStagingBufferMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/DeviceFeatures;persistentMapping()Z"))
    private boolean testmod$noPersistentMapping(DeviceFeatures features) {
        return !TestMod.MESA && features.persistentMapping();
    }
}
