package com.lewisgibson.preheat.ui;

import net.minecraft.client.gui.screens.Screen;

/** Preheat's screen, for Mod Menu on Fabric, the mod list on NeoForge and the Shader Cache entry in Sodium's video settings. */
public final class SettingsScreens {
    private SettingsScreens() {}

    public static Screen create(Screen parent) {
        return new PreheatScreen(parent);
    }
}
