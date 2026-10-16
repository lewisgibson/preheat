package com.lewisgibson.preheat.mixin.cache;

import com.google.common.collect.ImmutableSet;
import com.lewisgibson.preheat.build.BuildTracker;
import com.lewisgibson.preheat.cache.CacheHooks;
import com.lewisgibson.preheat.cache.ProgramBinaries;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris's composite, deferred, final, shadowcomp, setup and compute programs (and the vanilla-format programs built the
 * same way): with a cached binary the program is loaded instead of compiled; without one it compiles as before and its
 * binary is saved. ProgramCreator binds the same fixed attribute locations for every one of them, so the key is the
 * kind of program and its sources.
 */
@Mixin(value = ProgramBuilder.class, remap = false)
public class ProgramBuilderCacheMixin {
    /** KHR_debug's object type for a program. */
    @Unique
    private static final int GL_PROGRAM = 0x82E2;

    @Unique
    private static final ThreadLocal<String> preheat$key = new ThreadLocal<>();

    @Inject(method = "begin", at = @At("HEAD"), cancellable = true)
    private static void preheat$loadGraphics(
            String name,
            String vertex,
            String geometry,
            String fragment,
            ImmutableSet<Integer> reserved,
            CallbackInfoReturnable<ProgramBuilder> cir) {
        preheat$tryLoad(
                "builder",
                List.of(
                        "vertex",
                        String.valueOf(vertex),
                        "geometry",
                        String.valueOf(geometry),
                        "fragment",
                        String.valueOf(fragment)),
                name,
                reserved,
                cir);
    }

    @Inject(method = "beginCompute", at = @At("HEAD"), cancellable = true)
    private static void preheat$loadCompute(
            String name, String source, ImmutableSet<Integer> reserved, CallbackInfoReturnable<ProgramBuilder> cir) {
        preheat$tryLoad("compute", List.of("compute", String.valueOf(source)), name, reserved, cir);
    }

    @Inject(
            method = {"begin", "beginCompute"},
            at = @At("RETURN"))
    private static void preheat$save(CallbackInfoReturnable<ProgramBuilder> cir) {
        String key = preheat$key.get();
        preheat$key.remove();
        CacheHooks.afterCompile(
                key,
                () -> ProgramBinaries.save(
                        ((ProgramBuilderAccessor) cir.getReturnValue()).preheat$program(),
                        Objects.requireNonNull(key)));
    }

    @Unique
    private static void preheat$tryLoad(
            String kind,
            List<String> stages,
            String name,
            ImmutableSet<Integer> reserved,
            CallbackInfoReturnable<ProgramBuilder> cir) {
        preheat$key.remove();
        List<String> parts = new ArrayList<>();
        String key = CacheHooks.beforeCompile(
                () -> ProgramBinaries.keyFor(kind, () -> "ProgramCreator fixed attribute locations", stages, parts),
                ProgramBinaries::load,
                program -> {
                    // As ProgramCreator.create names a program it links, for graphics debuggers.
                    GLDebug.nameObject(GL_PROGRAM, program, name);
                    cir.setReturnValue(ProgramBuilderAccessor.preheat$new(name, program, reserved));
                    BuildTracker.TIMER.program(true);
                },
                k -> ProgramBinaries.debugMiss(String.valueOf(name), k, parts),
                ProgramBinaries::discard);
        if (key != null) {
            preheat$key.set(key);
        }
    }
}
