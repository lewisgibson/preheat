package com.lewisgibson.preheat.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where everything on Preheat's screen goes, for wide and narrow windows. */
class PanelLayoutTest {
    @Test
    void aWideWindowPutsThePanelsSideBySideWithinTheMaximumWidth() {
        PanelLayout l = new PanelLayout(1000, 600, 3);
        assertFalse(l.stacked);
        assertEquals(560, PanelLayout.contentWidth(1000));
        assertEquals(275, l.panelWidth);
        assertEquals(220, l.left);
        assertEquals(220 + 275 + 10, l.right);
        assertEquals(44, l.cacheTop);
        assertEquals(44 + 20 + 2 + 3 * 18 + 4 + 18 + 8, l.dimsBottom);
        assertEquals(l.buttonsTop() + 70, l.cacheBottom());
        assertEquals(Math.max(l.dimsBottom, l.cacheBottom()), l.dimsEnd(), "as long as the cache panel beside it");
        assertEquals(l.dimsEnd(), l.cacheEnd());
        assertEquals(l.dimsEnd() + 3, l.noteY());
        assertTrue(l.noteFits(9));
    }

    @Test
    void aNarrowWindowStacksThemAndTheDimensionsScroll() {
        PanelLayout l = new PanelLayout(300, 240, 20);
        assertTrue(l.stacked);
        assertEquals(284, l.panelWidth);
        assertEquals(l.left, l.right);
        assertEquals(44 + 20 + 4 * 18 + 8 + 10, l.cacheTop, "the dimensions get four rows before the cache");
        assertEquals(l.cacheTop - 10, l.dimsEnd());
        assertEquals(240 - 34, l.cacheEnd(), "the cache panel stops at the window's bottom");
        assertEquals(0, l.clampScroll(-5));
        int max = l.dimsBottom - l.dimsEnd();
        assertEquals(max, l.clampScroll(10_000));
        assertEquals(44 + 20 + 2 + 18, l.rowY(1, 21, 0));
        assertEquals(44 + 20 + 2 + 20 * 18 + 4 - 18, l.rowY(20, 21, 18), "the last row sits apart");
        assertTrue(l.rowVisible(l.rowY(0, 21, 0)));
        assertFalse(l.rowVisible(l.rowY(10, 21, 0)), "below the panel");
        assertFalse(l.rowVisible(l.top), "under the header");
        assertFalse(l.noteFits(9), "no room for a note above Done");
    }

    @Test
    void aShortListStacksWithoutScrolling() {
        PanelLayout l = new PanelLayout(300, 600, 1);
        assertEquals(l.dimsBottom, l.dimsEnd());
        assertEquals(l.dimsBottom + 10, l.cacheTop);
        assertEquals(0, l.clampScroll(50));
    }

    @Test
    void thePointerAndTheWheel() {
        PanelLayout l = new PanelLayout(1000, 600, 3);
        assertTrue(l.overDimensions(l.left, l.top));
        assertFalse(l.overDimensions(l.left - 1, l.top));
        assertFalse(l.overDimensions(l.left + l.panelWidth, l.top));
        assertFalse(l.overDimensions(l.left, l.top - 1));
        assertFalse(l.overDimensions(l.left, l.dimsEnd()));
        assertEquals(-18, PanelLayout.scrolled(0, 1));
        assertEquals(36, PanelLayout.scrolled(0, -2));
    }

    @Test
    void textIsCentredAndCutToFit() {
        assertEquals(500 - (16 + 6 + 40 + 6 + 30) / 2, PanelLayout.titleX(1000, 40, 30));
        assertEquals("short", PanelLayout.fit("short", 100, String::length, PanelLayoutTest::cut));
        assertEquals("a long fol...", PanelLayout.fit("a long folder name", 13, String::length, PanelLayoutTest::cut));
        assertTrue(PanelLayout.showWholeNote("cut", "cut short", 105, 100, 9));
        assertFalse(PanelLayout.showWholeNote("whole", "whole", 105, 100, 9));
        assertFalse(PanelLayout.showWholeNote("cut", "cut short", 99, 100, 9));
        assertFalse(PanelLayout.showWholeNote("cut", "cut short", 109, 100, 9));
    }

    /** One character per unit of width. */
    private static String cut(String s, int width) {
        return s.substring(0, Math.min(s.length(), Math.max(0, width)));
    }
}
