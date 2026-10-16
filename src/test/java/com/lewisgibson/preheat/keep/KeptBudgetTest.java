package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeptBudgetTest {
    private static final List<KeptBudget.Kept<String>> KEPT = List.of(
            new KeptBudget.Kept<>("nether", true, 30, 900),
            new KeptBudget.Kept<>("end", true, 10, 800),
            new KeptBudget.Kept<>("mod:old", false, 5, 700),
            new KeptBudget.Kept<>("mod:new", false, 40, 0));

    @Test
    void plentyOfMemoryKeepsEverything() {
        assertEquals(List.of(), KeptBudget.toDrop(KEPT, new KeptBudget.Memory(20_000, 32_000)));
    }

    @Test
    void lowMemoryLetsModdedGoFirstOldestFirstThenVanilla() {
        // Reserve on a 32 GB card is 4.8 GB; 4.1 GB free is 700 MB short: the oldest modded one covers it.
        assertEquals(List.of("mod:old"), KeptBudget.toDrop(KEPT, new KeptBudget.Memory(4_100, 32_000)));
        // 1.6 GB short: both modded (700 + the 512 MB default) and then the End, the vanilla one used longest ago.
        assertEquals(
                List.of("mod:old", "mod:new", "end"), KeptBudget.toDrop(KEPT, new KeptBudget.Memory(3_200, 32_000)));
        assertEquals(
                4, KeptBudget.toDrop(KEPT, new KeptBudget.Memory(0, 32_000)).size(), "everything, if need be");
    }

    @Test
    void theReserveIsAtLeastOneGigabyte() {
        assertEquals(List.of(), KeptBudget.toDrop(KEPT, new KeptBudget.Memory(1_100, 0)));
        assertEquals(List.of("mod:old"), KeptBudget.toDrop(KEPT, new KeptBudget.Memory(900, 0)));
    }

    @Test
    void withoutMemoryInfoOnlyTwoModdedAreKept() {
        List<KeptBudget.Kept<String>> many = List.of(
                new KeptBudget.Kept<>("end", true, 1, 0),
                new KeptBudget.Kept<>("mod:a", false, 3, 0),
                new KeptBudget.Kept<>("mod:b", false, 1, 0),
                new KeptBudget.Kept<>("mod:c", false, 2, 0),
                new KeptBudget.Kept<>("mod:d", false, 4, 0));
        assertEquals(List.of("mod:b", "mod:c"), KeptBudget.toDrop(many, null));
        assertEquals(List.of(), KeptBudget.toDrop(KEPT, null));
    }

    @Test
    void aPipelineIsPreparedAheadOnlyIfItFits() {
        assertTrue(KeptBudget.roomFor(900, null));
        assertTrue(KeptBudget.roomFor(900, new KeptBudget.Memory(10_000, 32_000)));
        assertFalse(KeptBudget.roomFor(900, new KeptBudget.Memory(5_000, 32_000)));
        assertFalse(KeptBudget.roomFor(0, new KeptBudget.Memory(1_400, 0)), "the default estimate");
    }

    @Test
    void readingsThatCannotBeRightAreNotUsed() {
        assertEquals(new KeptBudget.Memory(20, 32), KeptBudget.Memory.of(20 * 1024, 32 * 1024, 0));
        assertEquals(new KeptBudget.Memory(20, 32), KeptBudget.Memory.of(20 * 1024, 32 * 1024, 32 * 1024));
        assertEquals(new KeptBudget.Memory(20, 0), KeptBudget.Memory.of(20 * 1024, 0, 0), "free only (AMD)");
        assertEquals(null, KeptBudget.Memory.of(28 * 1024, -1_597_066 * 1024L, 0), "a negative total");
        assertEquals(null, KeptBudget.Memory.of(-1, 0, 0));
        assertEquals(null, KeptBudget.Memory.of(40 * 1024, 32 * 1024, 0), "more free than there is");
        assertEquals(null, KeptBudget.Memory.of(1024, 204_306 * 1024L, 88_490 * 1024L), "a total that changed");
    }
}
