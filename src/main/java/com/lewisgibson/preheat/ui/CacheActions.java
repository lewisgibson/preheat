package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.cache.ProgramBinaries;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The cache buttons: open the folder in the system's file manager, and delete the saved shaders after asking, with a
 * notification either way so the result shows on any screen (Sodium's pages too). What they do is
 * {@link CacheButtons}'s.
 */
public final class CacheActions {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();

    private CacheActions() {}

    public static void openFolder() {
        CacheButtons.openFolder(ProgramBinaries.root(), Blaze3D::openPath, CacheActions::toast);
    }

    /** Asks first; on yes, deletes the saved shaders, says how it went, and runs {@code refresh} on the game thread. */
    public static void confirmDelete(Screen back, Runnable refresh) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(
                yes -> {
                    CacheButtons.answered(yes, ProgramBinaries::purge, mc, CacheActions::toast, refresh);
                    mc.gui.setScreen(back);
                },
                Component.translatable("preheat.cache.delete.title"),
                Component.translatable("preheat.cache.delete.message"),
                Component.translatable("preheat.cache.delete.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    /** A notification in the corner: the key is the title, {@code key + ".detail"} the line under it. */
    static void toast(String key) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(
                mc.gui.toastManager(), TOAST, Component.translatable(key), Component.translatable(key + ".detail"));
    }
}
