package com.lewisgibson.preheat.ui;

import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

/**
 * Where everything on Preheat's screen goes ({@link PreheatScreen}): the two panels side by side, or stacked on a
 * narrow window, the dimension rows scrolled inside theirs, the cache panel's facts and buttons, and the note under the
 * panels. Pure arithmetic on the window's size, so it is tested without a window.
 */
final class PanelLayout {
    static final int ROW = 18;
    static final int HEADER = 20;
    static final int PAD = 8;
    static final int GAP = 10;
    static final int MAX_WIDTH = 560;

    final int left;
    final int right;
    final int panelWidth;
    final int top;
    final int bottom;
    final boolean stacked;
    /** Where the dimension rows end, unscrolled: every row, then the new dimensions row a little apart. */
    final int dimsBottom;

    final int cacheTop;
    private final int height;

    /** For a window of this size with {@code dimensions} rows, plus the row for dimensions not visited yet. */
    PanelLayout(int width, int height, int dimensions) {
        int w = contentWidth(width);
        this.height = height;
        stacked = w < 2 * 200 + GAP;
        panelWidth = stacked ? w : (w - GAP) / 2;
        left = (width - w) / 2;
        right = stacked ? left : left + panelWidth + GAP;
        top = 44;
        bottom = height - 34;
        dimsBottom = firstRowY() + dimensions * ROW + 4 + ROW + PAD;
        cacheTop = stacked ? Math.min(dimsBottom, top + HEADER + 4 * ROW + PAD) + GAP : top;
    }

    /** The width the panels share, at most {@link #MAX_WIDTH}. */
    static int contentWidth(int width) {
        return Math.min(width - 2 * PAD, MAX_WIDTH);
    }

    private int firstRowY() {
        return top + HEADER + 2;
    }

    /** The bottom of the dimensions panel: its content, but never past the space it has. */
    int dimsEnd() {
        int limit = stacked ? cacheTop - GAP : bottom;
        int end = stacked ? dimsBottom : Math.max(dimsBottom, cacheBottom());
        return Math.min(end, limit);
    }

    /** Where the cache panel's buttons start: under its three facts. */
    int buttonsTop() {
        return cacheTop + HEADER + 4 + 3 * ROW + 4;
    }

    /** Where the cache panel ends: under its three buttons. */
    int cacheBottom() {
        return buttonsTop() + 64 + PAD - 2;
    }

    /** Where the cache panel is drawn to: beside the dimensions as long as they are, or stacked within the window. */
    int cacheEnd() {
        return stacked ? Math.min(cacheBottom(), bottom) : Math.max(dimsEnd(), cacheBottom());
    }

    /** {@code scroll} kept within what the rows can scroll. */
    int clampScroll(int scroll) {
        int max = Math.max(0, dimsBottom - dimsEnd());
        return Math.max(0, Math.min(scroll, max));
    }

    /** Where row {@code i} of {@code rows} goes at this scroll; the last row (new dimensions) sits a little apart. */
    int rowY(int i, int rows, int scroll) {
        return firstRowY() + i * ROW + (i == rows - 1 ? 4 : 0) - scroll;
    }

    /** Whether a row at {@code y} is wholly inside the dimensions panel. */
    boolean rowVisible(int y) {
        return y >= top + HEADER && y + ROW <= dimsEnd();
    }

    /** Whether the pointer is over the dimension rows. */
    boolean overDimensions(double x, double y) {
        return x >= left && x < left + panelWidth && y >= top && y < dimsEnd();
    }

    /** The scroll after the wheel moved by {@code amount} (one row a notch), before it is clamped. */
    static int scrolled(int scroll, double amount) {
        return scroll - (int) (amount * ROW);
    }

    /** The note's line under both panels. */
    int noteY() {
        return Math.max(dimsEnd(), cacheEnd()) + 3;
    }

    /** Whether a note line of this height fits above the Done button. */
    boolean noteFits(int lineHeight) {
        return noteY() + lineHeight <= height - 30;
    }

    /** Where the title starts so that the icon, the name and the version are centred together. */
    static int titleX(int width, int nameWidth, int versionWidth) {
        int titleWidth = 16 + 6 + nameWidth + 6 + versionWidth;
        return width / 2 - titleWidth / 2;
    }

    /**
     * {@code value} as it fits in {@code room}: whole, or cut short with "..." after it.
     *
     * @param width the drawn width of a text
     * @param cut the longest start of a text that fits in a width
     */
    static String fit(String value, int room, ToIntFunction<String> width, BiFunction<String, Integer, String> cut) {
        String v = cut.apply(value, room);
        if (v.length() < value.length()) {
            v = cut.apply(value, room - width.applyAsInt("...")) + "...";
        }
        return v;
    }

    /** Whether the pointer is on a note line that was cut short, so the whole of it shows as a tooltip. */
    static boolean showWholeNote(String shown, String whole, int mouseY, int noteY, int lineHeight) {
        return shown.length() < whole.length() && mouseY >= noteY && mouseY < noteY + lineHeight;
    }
}
