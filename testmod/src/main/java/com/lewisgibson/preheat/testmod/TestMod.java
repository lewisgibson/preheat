package com.lewisgibson.preheat.testmod;

import com.google.common.base.Splitter;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test-only driver for the dimension-change lab: once the singleplayer world is up it settles, then walks the player
 * through a fixed list of dimensions by server command and records, per trip, how long the client spent inside
 * Minecraft.updateLevelInEngines (where Iris rebuilds its pipeline), the longest frame between the command and the end of
 * the dwell, and how long the arrival took. Never installed in a real instance.
 */
public final class TestMod {
    public static final Logger LOG = LogManager.getLogger("testmod");

    /** The workarounds software GL (Mesa's llvmpipe) needs. */
    public static final boolean MESA = Boolean.getBoolean("testmod.mesa");
    /** On a real desktop the lab window is moved off-screen so it never sits over other work. */
    private static final boolean HIDE_WINDOW = Boolean.getBoolean("testmod.hideWindow");
    private static boolean windowHidden;

    private static final String ARM = System.getProperty("testmod.arm", "unnamed");
    private static final long SETTLE_MS = Long.getLong("testmod.settleMs", 30_000L);
    private static final long DWELL_MS = Long.getLong("testmod.dwellMs", 15_000L);
    private static final long SHOT_AFTER_MS = Long.getLong("testmod.shotAfterMs", 10_000L);
    private static final String JFR_STEPS = System.getProperty("testmod.jfrSteps", "");
    private static final String TRIPS = System.getProperty(
        "testmod.trips", "the_nether,overworld,the_nether,overworld,the_end,overworld,the_nether,overworld");

    private enum State { WAIT_WORLD, SETTLE, ISSUE, TRAVEL, DWELL, DONE, RELOADING }

    private static State state = State.WAIT_WORLD;
    private static long stateStart;
    private static List<String> trips;
    private static int step;
    private static long issueNanos;
    private static long arrivalNanos;
    private static boolean shotTaken;
    private static Recording recording;

    // Frame and level-switch timing, written by the mixins on the render thread.
    private static long lastFrameNanos;
    private static long windowMaxFrameNanos;
    private static long levelSwitchStartNanos;
    private static long levelSwitchTotalNanos;
    private static int levelSwitchDepth;
    private static boolean measuring;

    private TestMod() {
    }

    public static void onFrame() {
        long now = System.nanoTime();
        if (measuring && lastFrameNanos != 0) {
            windowMaxFrameNanos = Math.max(windowMaxFrameNanos, now - lastFrameNanos);
        }
        lastFrameNanos = now;
    }

    public static void onLevelSwitchStart() {
        if (levelSwitchDepth++ == 0) {
            levelSwitchStartNanos = System.nanoTime();
        }
    }

    public static void onLevelSwitchEnd() {
        if (--levelSwitchDepth == 0 && measuring) {
            levelSwitchTotalNanos += System.nanoTime() - levelSwitchStartNanos;
        }
    }

    // Reconnect mode (-Dtestmod.reconnect=host:port): join a dedicated server, leave, join again, leave and quit at
    // once, so irisfixes' background Distant Horizons close and its barriers run on a multiplayer world.
    private static final String RECONNECT = System.getProperty("testmod.reconnect", "");
    private static int reconnectPhase;
    private static long reconnectAt;

    private static void reconnectTick(Minecraft mc, long now) {
        boolean inWorld = mc.level != null && mc.player != null;
        long ms = (now - reconnectAt) / 1_000_000L;
        switch (reconnectPhase) {
            case 0, 3 -> {
                if (inWorld) {
                    LOG.info("IRISLAB reconnect: in the world (join {})", reconnectPhase == 0 ? 1 : 2);
                    reconnectPhase++;
                    reconnectAt = now;
                }
            }
            case 1 -> {
                if (ms > SETTLE_MS) {
                    LOG.info("IRISLAB reconnect: leaving");
                    mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("testmod"));
                    reconnectPhase = 2;
                    reconnectAt = now;
                }
            }
            case 2 -> {
                if (!inWorld && ms > Long.getLong("testmod.titleMs", 1000L)) {
                    LOG.info("IRISLAB reconnect: joining again");
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(RECONNECT),
                        new net.minecraft.client.multiplayer.ServerData("lab", RECONNECT, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                    reconnectPhase = 3;
                    reconnectAt = now;
                }
            }
            case 4 -> {
                if (ms > 8000) {
                    LOG.info("IRISLAB reconnect: leaving and quitting at once");
                    mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("testmod"));
                    reconnectPhase = 5;
                    reconnectAt = now;
                }
            }
            case 5 -> {
                if (ms > 100) {
                    reconnectPhase = 6;
                    LOG.info("IRISLAB done");
                    mc.stop();
                }
            }
            default -> { }
        }
    }

    // Settings mode (-Dtestmod.openSettings=true): once the title screen is up, open Preheat's settings screen, so the
    // loading-screen lab can photograph it. Reflection keeps testmod free of a compile dependency on Preheat.
    // "true" opens Preheat's own screen, "video" the game's video settings (Sodium's, with Preheat's page in it).
    private static final String OPEN_SETTINGS_MODE = System.getProperty("testmod.openSettings", "false");
    private static final boolean OPEN_SETTINGS = !"false".equals(OPEN_SETTINGS_MODE);
    private static long titleSince;

    private static void settingsTick(Minecraft mc) {
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen title) || mc.gui.overlay() != null) {
            return;
        }
        if (titleSince == 0) {
            titleSince = System.nanoTime();
        } else if (System.nanoTime() - titleSince > 3_000_000_000L) {
            try {
                Object screen = "video".equals(OPEN_SETTINGS_MODE)
                    ? Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                        .getMethod("createScreen", net.minecraft.client.gui.screens.Screen.class).invoke(null, title)
                    : Class.forName("com.lewisgibson.preheat.ui.SettingsScreens")
                        .getMethod("create", net.minecraft.client.gui.screens.Screen.class).invoke(null, title);
                mc.gui.setScreen((net.minecraft.client.gui.screens.Screen) screen);
                LOG.info("IRISLAB opened Preheat's settings");
            } catch (ReflectiveOperationException e) {
                LOG.error("IRISLAB could not open Preheat's settings", e);
            }
        }
    }

    // -Dtestmod.openWorld=<save folder>: start at the title screen as a player does (so startup preparation runs),
    // then open that singleplayer world three seconds after the loading screen has gone.
    private static final String OPEN_WORLD = System.getProperty("testmod.openWorld", "");
    private static long titleReadySince;
    private static boolean worldOpened;

    private static void openWorldTick(Minecraft mc) {
        if (worldOpened || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) || mc.gui.overlay() != null) {
            return;
        }
        if (titleReadySince == 0) {
            titleReadySince = System.nanoTime();
        } else if (System.nanoTime() - titleReadySince > 3_000_000_000L) {
            worldOpened = true;
            LOG.info("IRISLAB opening world {} from the title screen", OPEN_WORLD);
            mc.createWorldOpenFlows().openWorld(OPEN_WORLD, () -> LOG.error("IRISLAB could not open world {}", OPEN_WORLD));
        }
    }

    // -Dtestmod.quitAtTitle=true: the startup benchmark (scripts/realgame/bench.sh). Once the title screen is up with no overlay,
    // log how long the game took to get there (JVM uptime) and quit.
    private static final boolean QUIT_AT_TITLE = Boolean.getBoolean("testmod.quitAtTitle");
    private static boolean quitting;

    private static void quitAtTitleTick(Minecraft mc) {
        if (HIDE_WINDOW && !windowHidden) {
            windowHidden = true;
            org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition(mc.getWindow().handle(), -5000, 100);
        }
        if (quitting || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) || mc.gui.overlay() != null) {
            return;
        }
        quitting = true;
        LOG.info("IRISLAB done");
        mc.stop();
    }

    private static boolean titleLogged;

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        // The startup benchmark's number: how long the game took to show the title screen with nothing over it.
        if (!titleLogged && mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen && mc.gui.overlay() == null) {
            titleLogged = true;
            LOG.info("IRISLAB title screen at {} ms", java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        }
        if (QUIT_AT_TITLE) {
            quitAtTitleTick(mc);
            return;
        }
        if (!OPEN_WORLD.isEmpty()) {
            openWorldTick(mc);
        }
        if (OPEN_SETTINGS) {
            settingsTick(mc);
            return;
        }
        if (!RECONNECT.isEmpty()) {
            long now = System.nanoTime();
            if (reconnectAt == 0) {
                reconnectAt = now;
            }
            reconnectTick(mc, now);
            return;
        }
        long now = System.nanoTime();
        if (HIDE_WINDOW && !windowHidden) {
            windowHidden = true;
            org.lwjgl.sdl.SDLVideo.SDL_SetWindowPosition(mc.getWindow().handle(), -5000, 100);
        }
        switch (state) {
            case WAIT_WORLD -> {
                if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                    LOG.info("IRISLAB world up, arm {}, settling {} ms", ARM, SETTLE_MS);
                    server("gamemode spectator @a");
                    server("time set 6000");
                    server("gamerule advance_time false");
                    server("gamerule doDaylightCycle false");
                    server("weather clear 1000000");
                    server("gamerule advance_weather false");
                    server("gamerule doWeatherCycle false");

                    server(tpCommand("overworld"));
                    trips = Splitter.on(',').splitToList(TRIPS);
                    enter(State.SETTLE, now);
                }
            }
            case SETTLE -> {
                if (elapsedMs(now) >= SETTLE_MS) {
                    // -Dtestmod.setup="cmd|cmd|...": extra server commands once the world has settled and the chunks
                    // around the player are loaded (a scene to photograph).
                    for (String cmd : Splitter.on('|').omitEmptyStrings().split(System.getProperty("testmod.setup", ""))) {
                        server(cmd);
                    }
                    enter(State.ISSUE, now);
                }
            }
            case ISSUE -> {
                String target = trips.get(step);
                if (target.equals("reload")) {
                    // A resource reload (F3+T, or a server resource pack) between trips.
                    LOG.info("IRISLAB step {} -> reloading resources", step + 1);
                    reload = mc.reloadResourcePacks();
                    enter(State.RELOADING, now);
                    return;
                }
                maybeStartJfr();
                measuring = true;
                windowMaxFrameNanos = 0;
                levelSwitchTotalNanos = 0;
                issueNanos = now;
                arrivalNanos = 0;
                shotTaken = false;
                LOG.info("IRISLAB step {} -> {}", step + 1, target);
                server(tpCommand(target));
                enter(State.TRAVEL, now);
            }
            case TRAVEL -> {
                String target = trips.get(step);
                if (target.contains("@")) {
                    target = target.substring(0, target.indexOf('@'));
                }
                if (mc.level != null
                        && (mc.level.dimension().identifier().getPath().equals(target)
                                || mc.level.dimension().identifier().toString().equals(target))) {
                    arrivalNanos = now;
                    enter(State.DWELL, now);
                } else if (elapsedMs(now) > 120_000) {
                    LOG.error("IRISLAB step {} never arrived in {}", step + 1, target);
                    finish(mc);
                }
            }
            case DWELL -> {
                if (!shotTaken && elapsedMs(now) >= SHOT_AFTER_MS) {
                    shotTaken = true;
                    String name = String.format(Locale.ROOT, "testmod-%s-step%02d-%s.png", ARM, step + 1, trips.get(step).replaceAll("[^a-z0-9_]", "_"));
                    Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, c -> { });
                }
                if (elapsedMs(now) >= DWELL_MS) {
                    measuring = false;
                    maybeStopJfr();
                    record(mc);
                    step++;
                    enter(step < trips.size() ? State.ISSUE : State.DONE, now);
                }
            }
            case RELOADING -> {
                CompletableFuture<Void> r = reload;
                if (r != null && r.isDone() && elapsedMs(now) >= 5_000) {
                    LOG.info("IRISLAB resources reloaded");
                    reload = null;
                    step++;
                    enter(step < trips.size() ? State.ISSUE : State.DONE, now);
                }
            }
            case DONE -> finish(mc);
        }
    }

    private static void record(Minecraft mc) {
        String from = "overworld";
        for (int i = step - 1; i >= 0; i--) {
            if (!trips.get(i).equals("reload")) {
                from = trips.get(i);
                break;
            }
        }
        String line = String.format(Locale.ROOT, "%s,%d,%s,%s,%.1f,%.1f,%.1f",
            ARM, step + 1, from, trips.get(step),
            levelSwitchTotalNanos / 1e6, windowMaxFrameNanos / 1e6, (arrivalNanos - issueNanos) / 1e6);
        LOG.info("IRISLAB result {}", line);
        File out = new File(mc.gameDirectory, "testmod-results.csv");
        boolean header = !out.exists();
        try (PrintWriter w = new PrintWriter(new FileWriter(out, StandardCharsets.UTF_8, true))) {
            if (header) {
                w.println("arm,step,from,to,level_switch_ms,max_frame_ms,arrival_ms");
            }
            w.println(line);
        } catch (IOException e) {
            LOG.error("IRISLAB could not write results", e);
        }
    }

    private static void maybeStartJfr() {
        if (!jfrStep()) {
            return;
        }
        try {
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.start();
        } catch (Exception e) {
            LOG.error("IRISLAB JFR start failed", e);
        }
    }

    private static void maybeStopJfr() {
        if (recording == null) {
            return;
        }
        try {
            recording.stop();
            Path path = Minecraft.getInstance().gameDirectory.toPath()
                .resolve(String.format(Locale.ROOT, "testmod-%s-step%02d.jfr", ARM, step + 1));
            recording.dump(path);
            recording.close();
            LOG.info("IRISLAB JFR written to {}", path);
        } catch (Exception e) {
            LOG.error("IRISLAB JFR dump failed", e);
        }
        recording = null;
    }

    private static boolean jfrStep() {
        for (String s : Splitter.on(',').split(JFR_STEPS)) {
            if (!s.isBlank() && Integer.parseInt(s.trim()) == step + 1) {
                return true;
            }
        }
        return false;
    }

    // -Dtestmod.leaveFirst=true: leave the world for the title screen, wait there 12 s (the disconnect trace finishes),
    // then quit, so a run also measures leaving a world.
    private static @Nullable CompletableFuture<Void> reload;

    private static final boolean LEAVE_FIRST = Boolean.getBoolean("testmod.leaveFirst");
    private static long leftAt;

    private static void finish(Minecraft mc) {
        if (state == State.DONE && stateStart < 0) {
            return;
        }
        if (LEAVE_FIRST && mc.level != null && leftAt == 0) {
            LOG.info("IRISLAB leaving the world");
            leftAt = System.nanoTime();
            mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false, true);
            return;
        }
        if (LEAVE_FIRST && System.nanoTime() - leftAt < 12_000_000_000L) {
            return;
        }
        state = State.DONE;
        stateStart = -1;
        LOG.info("IRISLAB done");
        mc.stop();
    }

    private static String tpCommand(String dim) {
        // "overworld@x;y;z;yaw;pitch": that dimension at a given spot (a scene set up with testmod.setup).
        int at = dim.indexOf('@');
        if (at >= 0) {
            String d = dim.substring(0, at);
            return "execute in " + (d.contains(":") ? d : "minecraft:" + d) + " run tp @a " + dim.substring(at + 1).replace(';', ' ');
        }
        // Fixed spots and facings so both arms frame the same view. Spectator mode, so nothing suffocates or falls.
        return switch (dim) {
            case "the_nether" -> "execute in minecraft:the_nether run tp @a 8.5 100 8.5 -45 10";
            case "the_end" -> "execute in minecraft:the_end run tp @a 40.5 90 0.5 90 15";
            case "overworld" -> "execute in minecraft:overworld run tp @a 0.5 110 0.5 -45 15";
            // A modded dimension by its full id ("the_sift:the_sift"): the same spot, high enough to look around.
            default -> "execute in " + (dim.contains(":") ? dim : "minecraft:" + dim) + " run tp @a 0.5 120 0.5 -45 15";
        };
    }

    private static void server(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private static void enter(State next, long now) {
        state = next;
        stateStart = now;
    }

    private static long elapsedMs(long now) {
        return (now - stateStart) / 1_000_000L;
    }

    static List<String> trips() {
        return new ArrayList<>(trips);
    }
}
