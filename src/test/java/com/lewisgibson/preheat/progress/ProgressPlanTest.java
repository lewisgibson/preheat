package com.lewisgibson.preheat.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressPlanTest {
    @TempDir
    Path tmp;

    private static ProgressPlan.Line line(@Nullable String dimension, long done, long total, boolean known) {
        return new ProgressPlan.Line(dimension, done, total, known);
    }

    @Test
    void theLineCountsTheWholeStartupLikeTheBar() {
        ProgressPlan p = new ProgressPlan();
        p.add("minecraft:the_nether", 101);
        p.start("minecraft:overworld", 99);
        assertEquals(List.of("minecraft:the_nether", "minecraft:overworld"), p.dimensions());
        p.progress(33);
        assertEquals(line("Overworld", 33, 200, true), p.line());
        assertEquals(33f / 200, p.fraction(), 1e-6);
        p.finish("minecraft:overworld", 99);
        p.start("minecraft:the_nether", 101);
        p.progress(34);
        assertEquals(line("Nether", 133, 200, true), p.line());
        assertEquals((99f + 34) / 200, p.fraction(), 1e-6);
        p.finish("minecraft:the_nether", 101);
        assertEquals(0.99f, p.fraction(), 1e-6, "never full until the overlay is finished");
        assertEquals(line(null, 200, 200, true), p.line());
    }

    @Test
    void aFirstBuildHasNoTotalUntilItIsKnown() {
        ProgressPlan p = new ProgressPlan();
        p.start("minecraft:the_end", null);
        p.progress(140);
        assertEquals(line("End", 100, 100, false), p.line(), "capped at the guess, and marked unknown");
        assertTrue(p.fraction() <= 0.99f);
        p.finish("minecraft:the_end", 140);
        assertTrue(p.line().totalKnown());
    }

    @Test
    void aBuildWithMoreProgramsThanLastTimeRaisesTheTotal() {
        ProgressPlan p = new ProgressPlan();
        p.start("minecraft:overworld", 50);
        p.progress(60);
        assertEquals(line("Overworld", 60, 60, true), p.line());
    }

    @Test
    void theLineIsWordedInTheGamesStyle() {
        assertEquals("preheat.loading.generic", key(ProgressReload.text(line(null, 0, 0, true))));
        assertEquals("preheat.loading.lineFirst", key(ProgressReload.text(line("End", 3, 100, false))));
        assertEquals("preheat.loading.line", key(ProgressReload.text(line("Nether", 43, 303, true))));
    }

    private static String key(Component c) {
        return ((TranslatableContents) c.getContents()).getKey();
    }

    @Test
    void countsPersistPerPackAndDimension() throws Exception {
        Path f = tmp.resolve("preheat").resolve("build-counts.properties");
        BuildCounts c = new BuildCounts(f);
        c.put("Complementary.zip", "minecraft:the_nether", 101);
        assertEquals(101, new BuildCounts(f).get("Complementary.zip", "minecraft:the_nether"));
        assertEquals(null, new BuildCounts(f).get("Other.zip", "minecraft:the_nether"));
        Files.writeString(f, "Complementary.zip|minecraft\\:the_nether=nope\n");
        assertEquals(null, new BuildCounts(f).get("Complementary.zip", "minecraft:the_nether"));
    }

    @Test
    void theReloadReportsProgressAndFinishes() {
        ProgressReload r = new ProgressReload();
        r.update(1.7f, Component.literal("x"));
        assertEquals(1f, r.getActualProgress());
        assertEquals("x", r.line().getString());
        assertTrue(!r.isDone());
        r.finish();
        assertTrue(r.isDone());
    }

    @Test
    void anEmptyPlanIsAnEmptyBarAndProgressBeforeAnyBuildIsIgnored() {
        ProgressPlan p = new ProgressPlan();
        assertEquals(0f, p.fraction());
        p.progress(5);
        assertEquals(line(null, 0, 0, true), p.line());
    }

    @Test
    void aCountOfNothingFromLastTimeIsAGuessAndAGuessIsNeverRaised() {
        ProgressPlan p = new ProgressPlan();
        p.start("minecraft:the_end", 0);
        p.progress(150);
        assertEquals(line("End", 100, 100, false), p.line(), "the guess stands until the build ends");
    }

    @Test
    void aKnownCountIsRaisedOnlyWhenTheBuildPassesIt() {
        ProgressPlan p = new ProgressPlan();
        p.start("minecraft:the_nether", 101);
        p.progress(50);
        assertEquals(line("Nether", 50, 101, true), p.line());
        p.progress(120);
        assertEquals(line("Nether", 120, 120, true), p.line());
    }

    @Test
    void finishingAnotherDimensionLeavesTheCurrentBuildRunning() {
        ProgressPlan p = new ProgressPlan();
        p.add("minecraft:the_end", 40);
        p.start("minecraft:the_nether", 100);
        p.progress(10);
        p.finish("minecraft:the_end", 40);
        assertEquals(line("Nether", 50, 140, true), p.line());
    }

    @Test
    void anUnreadableCountsFileIsNoCountsAndAnUnwritableOneLosesOnlyTheCount() throws Exception {
        Path f = tmp.resolve("build-counts.properties");
        Files.writeString(f, "pack|minecraft\\:overworld=\\uZZZZ\n");
        assertEquals(null, new BuildCounts(f).get("pack", "minecraft:overworld"));

        Path blocked = tmp.resolve("not-a-folder");
        Files.writeString(blocked, "a file where the folder should be");
        BuildCounts c = new BuildCounts(blocked.resolve("build-counts.properties"));
        c.put("pack", "minecraft:overworld", 99);
        assertEquals(99, c.get("pack", "minecraft:overworld"), "still known for this session");
    }

    @Test
    void anUnchangedCountIsNotWrittenAgain() throws Exception {
        Path f = tmp.resolve("build-counts.properties");
        BuildCounts c = new BuildCounts(f);
        c.put("pack", "minecraft:overworld", 99);
        Files.writeString(f, "pack|minecraft\\:overworld=99\n# left as it is\n");
        c.put("pack", "minecraft:overworld", 99);
        assertTrue(Files.readString(f).contains("# left as it is"));
    }

    @Test
    void theReloadShowsAPlanLineAndIsFoundOnlyOnPreheatsOverlay() {
        ProgressReload r = new ProgressReload();
        r.update(0.5f, line("Nether", 43, 303, true));
        assertEquals(0.5f, r.getActualProgress());
        assertEquals("preheat.loading.line", key(r.line()));
        assertEquals(r.line(), ProgressReload.lineOf(r));
        assertEquals(null, ProgressReload.lineOf("another overlay's reload"));
        assertTrue(!r.finished());
        r.finish();
        assertTrue(r.finished());
    }
}
