package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheGuardTest {
    private static final String GEN = "0123456789abcdef";

    @TempDir
    Path tmp;

    @BeforeEach
    void owned() throws Exception {
        CacheDir.ensureOwned(tmp);
        Files.createDirectories(tmp.resolve(GEN));
        Files.writeString(tmp.resolve(GEN).resolve("k.bin"), "x");
    }

    /** A game with this pid; no other game is running. */
    private CacheGuard game(long pid) {
        return new CacheGuard(tmp, pid, p -> false);
    }

    @Test
    void theFirstUncleanStopDeletesThatGenerationAndASecondOnTheSameOnePauses() throws Exception {
        CacheGuard g = game(1);
        assertNull(g.checkAtStartup());
        g.arm(GEN, "minecraft:the_nether");
        g.disarm();
        assertNull(game(2).checkAtStartup(), "armed and disarmed: no pause");
        assertTrue(Files.exists(tmp.resolve(GEN)));

        g.arm(GEN, "minecraft:the_nether");
        // The game stops here without disarming: a strike, and that generation starts again.
        assertNull(game(2).checkAtStartup(), "the first unclean stop is tried again");
        assertEquals(new CacheGuard.Strikes(GEN, 1), game(2).strikes());
        assertFalse(Files.exists(tmp.resolve(GEN)), "the generation being loaded is deleted, not handed over again");

        game(2).arm(GEN, "minecraft:the_nether");
        assertEquals(CacheGuard.STOPPED_WHILE_LOADING, game(3).checkAtStartup());
        assertEquals(CacheGuard.STOPPED_WHILE_LOADING, game(4).checkAtStartup(), "stays paused on every launch");
        CacheDir.purge(tmp);
        assertNull(game(5).checkAtStartup(), "deleting the cache clears the pause");
    }

    @Test
    void strikesOnDifferentGenerationsDoNotAddUp() throws Exception {
        game(1).arm(GEN, "x");
        assertNull(game(2).checkAtStartup());
        game(2).arm("fedcba9876543210", "x");
        assertNull(game(3).checkAtStartup(), "a stop on another generation starts the count again");
        assertEquals(new CacheGuard.Strikes("fedcba9876543210", 1), game(3).strikes());
    }

    @Test
    void aCleanLoadClearsAStrike() throws Exception {
        game(1).arm(GEN, "x");
        assertNull(game(2).checkAtStartup());
        CacheGuard g = game(2);
        g.arm(GEN, "x");
        g.disarm();
        assertEquals(0, g.strikes().count());
        game(3).arm(GEN, "x");
        assertNull(game(4).checkAtStartup(), "strikes have to be in a row");
        Files.writeString(tmp.resolve(CacheGuard.STRIKES), "junk");
        assertEquals(0, g.strikes().count());
    }

    @Test
    void anotherGameStillRunningIsNotAStrike() throws Exception {
        new CacheGuard(tmp, 7, p -> false).arm(GEN, "x");
        CacheGuard other = new CacheGuard(tmp, 8, p -> p == 7);
        assertNull(other.checkAtStartup());
        assertEquals(0, other.strikes().count(), "game 7 is loading right now, not stopped");
        assertTrue(Files.exists(tmp.resolve("loading-7.marker")));
    }

    @Test
    void aMarkerFromAnOlderVersionStillCounts() throws Exception {
        Files.writeString(tmp.resolve(CacheGuard.LEGACY_LOADING), "minecraft:overworld");
        assertNull(game(1).checkAtStartup());
        assertEquals(1, game(1).strikes().count());
    }

    @Test
    void theInterfaceHashIgnoresTheDriversListingOrder() {
        byte[] a = ProgramInterface.of(List.of("a pos 35665 1 0", "u gtexture 35678 1", "b 1"));
        byte[] b = ProgramInterface.of(List.of("b 1", "u gtexture 35678 1", "a pos 35665 1 0"));
        assertTrue(Arrays.equals(a, b));
        assertEquals(32, a.length);
        assertNotEquals(
                Arrays.toString(a),
                Arrays.toString(ProgramInterface.of(List.of("a pos 35665 1 1", "u gtexture 35678 1", "b 1"))),
                "a different attribute location is a different interface");
    }

    @Test
    void markersOfGamesStillRunningAreLeftAndThoseOfStoppedOnesCount() throws Exception {
        long running = ProcessHandle.current().parent().orElseThrow().pid();
        Process finished = new ProcessBuilder("true").start();
        finished.waitFor();
        Path theirs = tmp.resolve("loading-" + running + ".marker");
        Path stopped = tmp.resolve("loading-" + finished.pid() + ".marker");
        Files.writeString(theirs, GEN + "\nminecraft:overworld\n");
        Files.writeString(stopped, GEN + "\nminecraft:overworld\n");
        assertNull(new CacheGuard(tmp).checkAtStartup());
        assertTrue(Files.exists(theirs), "another running game is loading from the same folder");
        assertFalse(Files.exists(stopped));
        assertEquals(new CacheGuard.Strikes(GEN, 1), game(1).strikes());
    }

    @Test
    void thisGamesOwnLeftoverMarkerCountsEvenThoughItIsRunning() throws Exception {
        Files.writeString(tmp.resolve("loading-7.marker"), GEN + "\n");
        assertNull(new CacheGuard(tmp, 7, p -> true).checkAtStartup());
        assertEquals(new CacheGuard.Strikes(GEN, 1), game(7).strikes());
    }

    @Test
    void aStrikeDeletesOnlyAGenerationOfAFolderPreheatOwns() throws Exception {
        Path notOwned = Files.createDirectories(tmp.resolve("elsewhere"));
        Files.createDirectories(notOwned.resolve(GEN));
        Files.writeString(notOwned.resolve("loading-3.marker"), GEN + "\n");
        assertNull(new CacheGuard(notOwned, 1, p -> false).checkAtStartup());
        assertTrue(Files.exists(notOwned.resolve(GEN)), "not Preheat's folder");

        Files.writeString(tmp.resolve("loading-3.marker"), "not-a-generation\n");
        Files.createDirectories(tmp.resolve("not-a-generation"));
        assertNull(game(1).checkAtStartup());
        assertTrue(Files.exists(tmp.resolve("not-a-generation")), "only folders named like a generation");
        assertEquals(new CacheGuard.Strikes("not-a-generation", 1), game(1).strikes());
    }

    @Test
    void aStrikesFileThatCannotBeReadIsNoStrike() throws Exception {
        Files.writeString(tmp.resolve(CacheGuard.STRIKES), GEN + " many");
        assertEquals(new CacheGuard.Strikes("", 0), game(1).strikes());
        Files.delete(tmp.resolve(CacheGuard.STRIKES));
        Files.createDirectories(tmp.resolve(CacheGuard.STRIKES));
        assertEquals(new CacheGuard.Strikes("", 0), game(1).strikes());
    }
}
