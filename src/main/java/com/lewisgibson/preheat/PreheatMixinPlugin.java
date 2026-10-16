package com.lewisgibson.preheat;

import com.lewisgibson.preheat.feature.OwnClasses;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Mixin's hook into Preheat: every decision is {@link MixinGate}'s. Preheat's own classes come from its jar, everything
 * else from the mixin service ({@link OwnClasses}).
 */
public final class PreheatMixinPlugin implements IMixinConfigPlugin {
    private static final MixinGate GATE = new MixinGate(
            () -> OwnClasses.over(PreheatMixinPlugin::classNode, PreheatMixinPlugin.class.getClassLoader()),
            System::nanoTime);

    @Override
    public void onLoad(String mixinPackage) {
        GATE.evaluate();
    }

    /** A class file by internal name from the mixin service, without loading or transforming it. */
    private static @Nullable ClassNode classNode(String internalName) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(internalName.replace('/', '.'), false);
        } catch (ClassNotFoundException | IOException | RuntimeException e) {
            // Missing or unreadable: the feature that needs it is off, which is the answer this check exists to give.
            return null;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return GATE.shouldApply(mixinClassName);
    }

    @Override
    public @Nullable String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        GATE.applied(targetClassName, targetClass, mixinClassName);
    }
}
