package com.lewisgibson.preheat.ui;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.config.KnownDimensions;
import com.lewisgibson.preheat.config.PreheatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Preheat's own screen, in the style of Iris's shader pack screen and Sodium's pages rather than a vanilla options list:
 * the title with the icon and version, one plain line saying what Preheat is doing, and two panels side by side
 * (stacked on a narrow window). Dimensions: a tick per dimension and one for dimensions not visited yet. Shader Cache:
 * what is saved, what is ready and where, with Open Folder, Delete Cache and Change Folder. Opened from Sodium's video
 * settings (the Shader Cache entry), from Mod Menu and from NeoForge's mod list. A tick is saved the moment it changes.
 */
public final class PreheatScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(Preheat.MOD_ID, "icon.png");
    /** Sodium's colour theme for Preheat's pages, so the two read as one. */
    static final int ACCENT = 0xFFFF8A3D;

    private static final int PANEL_BG = 0xA0000000;
    private static final int HEADER_BG = 0xC0000000;
    private static final int ROW = PanelLayout.ROW;
    private static final int HEADER = PanelLayout.HEADER;
    private static final int PAD = PanelLayout.PAD;
    private static final int GREY = 0xFFA0A0A0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int WARN = 0xFFFFC060;

    private final Screen parent;
    private PanelLayout layout = new PanelLayout(0, 0, 0);
    private final List<Tick> ticks = new ArrayList<>();
    private int scroll;

    public PreheatScreen(Screen parent) {
        super(SettingsText.title());
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<String> ids = SettingsText.listedDimensions();
        layout = new PanelLayout(this.width, this.height, ids.size());
        int left = layout.left;
        int right = layout.right;
        int panelWidth = layout.panelWidth;

        ticks.clear();
        boolean available = SettingsText.available();
        for (String id : ids) {
            String name = KnownDimensions.display(id);
            ticks.add(addRenderableWidget(new Tick(
                            left + 2,
                            0,
                            panelWidth - 4,
                            Component.literal(name),
                            () -> PreheatConfig.get().dimensions.getOrDefault(id, true),
                            on -> PreheatConfig.change(c -> c.dimensions.put(id, on))))
                    .tooltip(Component.translatable("preheat.option.dimension.description", name), available));
        }
        ticks.add(addRenderableWidget(new Tick(
                        left + 2,
                        0,
                        panelWidth - 4,
                        Component.translatable("preheat.option.moddedDimensions"),
                        () -> PreheatConfig.get().moddedDimensions,
                        on -> PreheatConfig.change(c -> c.moddedDimensions = on)))
                .tooltip(Component.translatable("preheat.option.moddedDimensions.description"), available));

        placeTicks();
        int by = layout.buttonsTop();
        int bw = panelWidth - 2 * PAD;
        addRenderableWidget(Button.builder(Component.translatable("preheat.cache.open"), b -> CacheActions.openFolder())
                .bounds(right + PAD, by, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("preheat.cache.open.description")))
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("preheat.cache.delete"), b -> CacheActions.confirmDelete(this, () -> {}))
                .bounds(right + PAD, by + 22, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("preheat.cache.delete.description")))
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("preheat.cache.folder.change"),
                        b -> this.minecraft.gui.setScreen(new CacheFolderScreen(this, folder -> {})))
                .bounds(right + PAD, by + 44, bw, 20)
                .tooltip(Tooltip.create(Component.translatable("preheat.cache.folder.change.description")))
                .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    /** Moves the dimension rows by the scroll, hiding any outside the panel (a modpack can have dozens). */
    private void placeTicks() {
        scroll = layout.clampScroll(scroll);
        for (int i = 0; i < ticks.size(); i++) {
            Tick t = ticks.get(i);
            int y = layout.rowY(i, ticks.size(), scroll);
            t.setY(y);
            t.visible = layout.rowVisible(y);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (layout.overDimensions(x, y)) {
            scroll = PanelLayout.scrolled(scroll, dy);
            placeTicks();
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Title: the icon, the name and the version, as Sodium's and Iris's headers show theirs.
        Component name = this.title;
        String version = Preheat.displayVersion();
        int tx = PanelLayout.titleX(this.width, this.font.width(name), this.font.width(version));
        g.blit(RenderPipelines.GUI_TEXTURED, ICON, tx, 10, 0f, 0f, 16, 16, 16, 16);
        g.text(this.font, name, tx + 22, 14, WHITE);
        g.text(this.font, version, tx + 22 + this.font.width(name) + 6, 14, GREY);
        g.centeredText(this.font, SettingsText.generalStatus(), this.width / 2, 30, statusColour());

        PanelLayout l = layout;
        panel(g, l.left, l.top, l.panelWidth, l.dimsEnd() - l.top, SettingsText.section("dimensions"));
        panel(g, l.right, l.cacheTop, l.panelWidth, l.cacheEnd() - l.cacheTop, SettingsText.section("cache"));

        int y = l.cacheTop + HEADER + 4;
        fact(g, y, Component.translatable("preheat.cache.saved"), SettingsText.savedValue());
        fact(g, y + ROW, Component.translatable("preheat.cache.ready"), SettingsText.readyValue());
        fact(g, y + 2 * ROW, Component.translatable("preheat.cache.folder.label"), SettingsText.folderValue());
        // Why the cache or the chosen folder is not in use: one line under the panels, the whole of it on hover.
        Component note = SettingsText.cacheNote();
        int noteY = l.noteY();
        if (note != null && l.noteFits(this.font.lineHeight)) {
            String line = this.font.plainSubstrByWidth(note.getString(), PanelLayout.contentWidth(this.width));
            g.centeredText(this.font, line, this.width / 2, noteY, WARN);
            if (PanelLayout.showWholeNote(line, note.getString(), mouseY, noteY, this.font.lineHeight)) {
                g.setTooltipForNextFrame(note, mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int statusColour() {
        return SettingsText.ready() ? WHITE : WARN;
    }

    /** A panel like Sodium's option groups: a darker header with the section name in the accent colour. */
    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, Component name) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + HEADER, HEADER_BG);
        g.fill(x, y + HEADER - 1, x + w, y + HEADER, ACCENT);
        g.text(this.font, name, x + PAD, y + (HEADER - this.font.lineHeight) / 2 + 1, ACCENT);
    }

    /** A label on the left of the cache panel and its value on the right. */
    private void fact(GuiGraphicsExtractor g, int y, Component label, Component value) {
        int ty = y + (ROW - this.font.lineHeight) / 2;
        int right = layout.right;
        int panelWidth = layout.panelWidth;
        g.text(this.font, label, right + PAD, ty, GREY);
        int room = panelWidth - 3 * PAD - this.font.width(label);
        String v = PanelLayout.fit(value.getString(), room, this.font::width, this.font::plainSubstrByWidth);
        g.text(this.font, v, right + panelWidth - PAD - this.font.width(v), ty, WHITE);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    /** A row with a name on the left and a tick box on the right, like Sodium's; it saves the moment it changes. */
    static final class Tick extends AbstractButton {
        private final Supplier<Boolean> value;
        private final Consumer<Boolean> set;

        Tick(int x, int y, int w, Component name, Supplier<Boolean> value, Consumer<Boolean> set) {
            super(x, y, w, ROW, name);
            this.value = value;
            this.set = set;
        }

        Tick tooltip(Component text, boolean active) {
            setTooltip(Tooltip.create(text));
            this.active = active;
            return this;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            set.accept(!value.get());
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            if (isHoveredOrFocused() && this.active) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x30FFFFFF);
            }
            int ty = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            g.text(font, getMessage(), getX() + PAD - 2, ty, this.active ? WHITE : GREY);
            int box = 10;
            int bx = getX() + getWidth() - PAD - box + 2;
            int by = getY() + (getHeight() - box) / 2;
            int colour = this.active ? ACCENT : GREY;
            g.outline(bx, by, box, box, colour);
            if (value.get()) {
                g.fill(bx + 2, by + 2, bx + box - 2, by + box - 2, colour);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
