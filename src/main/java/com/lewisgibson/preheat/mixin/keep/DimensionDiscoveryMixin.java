package com.lewisgibson.preheat.mixin.keep;

import com.lewisgibson.preheat.keep.DimensionDiscovery;
import java.util.Set;
import java.util.stream.Collectors;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each level the game sets, for the dimensions the settings list ({@link DimensionDiscovery}). */
@Mixin(value = Minecraft.class, remap = false)
public class DimensionDiscoveryMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void preheat$discover(ClientLevel level, CallbackInfo ci) {
        DimensionDiscovery.levelSet(
                level,
                l -> l.dimension().identifier().toString(),
                () -> Minecraft.getInstance().getConnection(),
                c -> c.levels().stream().map(k -> k.identifier().toString()).toList(),
                () -> Iris.getCurrentPack()
                        .map(p -> p.getDimensionMap().keySet().stream()
                                .map(String::valueOf)
                                .collect(Collectors.toSet()))
                        .orElse(Set.of()));
    }
}
