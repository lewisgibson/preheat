package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DimensionChangeTest {
    private boolean seen;

    // Stand-ins named as the merged methods are at runtime: Mixin keeps the handler's name as the suffix.
    private void handler$zab000$iris$resetPipeline() {
        destroyPipeline();
    }

    private void handler$zab000$iris$reload() {
        destroyPipeline();
    }

    private void destroyPipeline() {
        seen = DimensionChange.isCaller();
    }

    @Test
    void recognisesTheDimensionChangeHandlerOnTheRealStack() {
        handler$zab000$iris$resetPipeline();
        assertTrue(seen);
    }

    @Test
    void ignoresEveryOtherCaller() {
        handler$zab000$iris$reload();
        assertFalse(seen);
        destroyPipeline();
        assertFalse(seen);
    }

    @Test
    void looksOnlyAtTheDirectCaller() {
        // A dimension-change handler further down the stack does not count.
        assertFalse(DimensionChange.callerIsHandler(
                List.of("isCaller", "destroyPipeline", "reload", "handler$x$iris$resetPipeline")
                        .iterator()));
        assertTrue(
                DimensionChange.callerIsHandler(List.of("isCaller", "destroyPipeline", "handler$x$iris$resetPipeline")
                        .iterator()));
        assertFalse(DimensionChange.callerIsHandler(
                List.of("isCaller", "preparePipeline").iterator()));
    }
}
