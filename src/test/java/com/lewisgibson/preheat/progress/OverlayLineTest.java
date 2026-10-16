package com.lewisgibson.preheat.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/** The line Preheat adds under the loading overlay's bar, and the bar itself on Preheat's overlay. */
class OverlayLineTest {
    @Test
    void theBarJumpsStraightToTheBuildOnlyOnPreheatsOverlay() {
        ProgressReload r = new ProgressReload();
        r.update(0.4f, Component.literal("x"));
        assertEquals(0.4f, ProgressReload.barFill(r, 0.1f));
        assertEquals(0.1f, ProgressReload.barFill("another overlay's reload", 0.1f));
    }

    @Test
    void theLineFadesOutWithTheOverlayInASecond() {
        assertEquals(1f, ProgressReload.lineAlpha(-1L, 5_000));
        assertEquals(0.75f, ProgressReload.lineAlpha(4_000, 4_250), 1e-6);
        assertEquals(0f, ProgressReload.lineAlpha(4_000, 9_000));
    }

    @Test
    void aLineIsDrawnOnlyWhenThereIsOneAndItCanStillBeSeen() {
        ProgressReload r = new ProgressReload();
        assertNull(ProgressReload.lineToDraw(r, 1f), "nothing to say yet");
        r.update(0f, Component.literal("Preparing Nether shaders"));
        assertEquals(
                "Preparing Nether shaders", ProgressReload.lineToDraw(r, 1f).getString());
        assertNull(ProgressReload.lineToDraw(r, 0.02f), "faded out");
        assertNull(ProgressReload.lineToDraw("another overlay's reload", 1f));
        assertEquals((int) (1080 * 0.8325) + 12, ProgressReload.lineY(1080));
    }
}
