package com.lewisgibson.preheat.neoforge;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.ui.SettingsScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** NeoForge's entry point: registers the settings screen with the mod list's Config button. Never loaded on Fabric. */
@Mod(value = Preheat.MOD_ID, dist = Dist.CLIENT)
public final class PreheatNeoForge {
    public PreheatNeoForge(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> SettingsScreens.create(parent));
    }
}
