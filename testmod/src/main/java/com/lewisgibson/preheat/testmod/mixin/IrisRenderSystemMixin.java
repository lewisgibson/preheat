package com.lewisgibson.preheat.testmod.mixin;

import com.lewisgibson.preheat.testmod.TestMod;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * llvmpipe answers GL_NVX_gpu_memory_info with 30 MiB, and Iris refuses Complementary's 810 MB of storage buffers
 * against that and turns shaders off. The lab reports 32 GiB, what Iris reads on a large real GPU.
 */
@Mixin(value = IrisRenderSystem.class, remap = false)
public class IrisRenderSystemMixin {
    /**
     * Iris binds Distant Horizons' depth texture as -1 until DH's first texture-clear event (a new DHCompatInternal's
     * storedDepthTex), on every fresh pipeline in stock Iris. NVIDIA rejects the bind as a GL error and goes on;
     * llvmpipe segfaults in glBindTextureUnit. The lab binds 0 instead, as NVIDIA effectively does.
     */
    @ModifyVariable(method = "bindTextureToUnit", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static int testmod$noInvalidTexture(int texture) {
        return TestMod.MESA && texture < 0 ? 0 : texture;
    }

    @Inject(method = "getVRAM", at = @At("HEAD"), cancellable = true)
    private static void testmod$hisVram(CallbackInfoReturnable<Long> cir) {
        if (TestMod.MESA) {
            cir.setReturnValue(32L * 1024 * 1024 * 1024);
        }
    }
}
