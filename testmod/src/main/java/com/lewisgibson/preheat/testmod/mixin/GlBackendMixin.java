package com.lewisgibson.preheat.testmod.mixin;

import com.lewisgibson.preheat.testmod.TestMod;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Xvfb's GLX has no sRGB-capable visual, so the window Minecraft asks for (SDL_GL_FRAMEBUFFER_SRGB_CAPABLE) cannot be
 * made and the game falls back to Vulkan, which Iris cannot use. The lab drops that one request. It changes only the
 * gamma of the final image, the same in every arm, and none of Iris's pipeline work.
 */
@Mixin(value = GlBackend.class, remap = false)
public class GlBackendMixin {
    private static final int SDL_GL_FRAMEBUFFER_SRGB_CAPABLE = 22;

    @Redirect(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_GL_SetAttribute(II)Z"))
    private boolean testmod$noSrgbVisual(int attr, int value) {
        return (TestMod.MESA && attr == SDL_GL_FRAMEBUFFER_SRGB_CAPABLE) || SDLVideo.SDL_GL_SetAttribute(attr, value);
    }
}
