package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Regression test for the review's must-fix 2: a reused program name never files one binary under another's key. */
class PendingSavesTest {
    @Test
    void aDeletedProgramsNameReusedLaterInTheBuildIsNotSavedUnderTheOldKey() {
        PendingSaves p = new PendingSaves();
        p.add(7, "shadow-terrain");
        p.add(8, "gbuffers-water");
        // Iris deletes the unused shadow program mid-build, and the driver hands name 7 to a compute program.
        p.forget(7);
        assertEquals(List.of(Map.entry(8, "gbuffers-water")), p.drain());
        assertTrue(p.drain().isEmpty(), "drained once");
    }

    @Test
    void aBuildThatNeverEndedLeavesNothingForTheNext() {
        PendingSaves p = new PendingSaves();
        p.add(3, "old-build");
        p.clear();
        p.add(4, "new-build");
        assertEquals(List.of(Map.entry(4, "new-build")), p.drain());
    }

    @Test
    void theNewestKeyForANameWins() {
        PendingSaves p = new PendingSaves();
        p.add(5, "first");
        p.add(5, "second");
        assertEquals(List.of(Map.entry(5, "second")), p.drain());
    }
}
