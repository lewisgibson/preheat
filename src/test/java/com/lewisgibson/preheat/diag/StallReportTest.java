package com.lewisgibson.preheat.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StallReportTest {
    @Test
    void ownersByTimeWithTheirTopMethod() {
        StallReport r = new StallReport();
        r.add("distanthorizons|PriorityTaskPicker.shutdownNow", 2_000_000_000L);
        r.add("distanthorizons|AbstractDhRepo.close", 500_000_000L);
        r.add("minecraft|Minecraft.renderFrame", 300_000_000L);
        r.add("distanthorizons|AbstractDhRepo.close", 100_000_000L);
        assertEquals(
                List.of(
                        "distanthorizons 2600 ms (PriorityTaskPicker.shutdownNow 2000 ms)",
                        "minecraft 300 ms (Minecraft.renderFrame 300 ms)"),
                r.lines(8));
        assertEquals(1, r.lines(1).size());
    }

    @Test
    void aSampleWithoutAMethodCountsForItsOwnerAlone() {
        StallReport r = new StallReport();
        r.add("unknown", 3_000_000L);
        assertEquals(3_000_000L, r.nanos("unknown"));
    }
}
