package com.lewisgibson.preheat.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.config.ConfigFiles;
import com.lewisgibson.preheat.feature.Feature;
import com.lewisgibson.preheat.feature.FeatureCheck;
import com.lewisgibson.preheat.feature.Features;
import com.lewisgibson.preheat.platform.FakePlatform;
import com.lewisgibson.preheat.platform.Platforms;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AsyncDisconnectTest {
    @Test
    void defersOnlyAMultiplayerCloseOnTheRenderThread() {
        assertTrue(AsyncDisconnect.shouldDefer(true, false, true, false));
        assertFalse(AsyncDisconnect.shouldDefer(false, false, true, false), "turned off");
        assertFalse(AsyncDisconnect.shouldDefer(true, true, true, false), "the background run itself");
        assertFalse(AsyncDisconnect.shouldDefer(true, false, false, false), "another thread");
        assertFalse(AsyncDisconnect.shouldDefer(true, false, true, true), "singleplayer");
    }

    @Test
    void theBarrierWaitsForTheCloseToFinish() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(1);
        AsyncDisconnect.start(() -> {
            assertTrue(AsyncDisconnect.insideWorker());
            started.countDown();
            sleep(300);
            order.add("closed");
        });
        started.await();
        assertFalse(AsyncDisconnect.insideWorker());
        AsyncDisconnect.await("the test");
        order.add("joined");
        assertEquals(List.of("closed", "joined"), order);
    }

    @Test
    void aSecondCloseWaitsForTheFirst() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AsyncDisconnect.start(() -> {
            sleep(200);
            order.add("first");
        });
        AsyncDisconnect.start(() -> order.add("second"));
        AsyncDisconnect.await("the test");
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void aFailingCloseStillReleasesTheBarrier() {
        AsyncDisconnect.start(() -> {
            throw new IllegalStateException("boom");
        });
        AsyncDisconnect.await("the test");
    }

    @Test
    void aCloseThatNeverEndsCannotHangTheGame() {
        CountDownLatch release = new CountDownLatch(1);
        AsyncDisconnect.start(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        long t0 = System.nanoTime();
        AsyncDisconnect.await("the test", 200);
        long waited = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(waited >= 150 && waited < 5_000, "waited " + waited + " ms");
        release.countDown();
        AsyncDisconnect.await("the test");
    }

    @Test
    void onlyTheCheckedDistantHorizonsVersionsCloseInTheBackground() {
        assertTrue(AsyncDisconnect.checked("3.3.4-26.3"));
        assertFalse(AsyncDisconnect.checked("3.4.0-26.3"));
        assertFalse(AsyncDisconnect.checked(""));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void theCloseItselfNeverWaitsForItself() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AsyncDisconnect.start(() -> {
            AsyncDisconnect.await("a world the close opened");
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "returned at once rather than waiting on its own thread");
        AsyncDisconnect.await("the test");
    }

    @Test
    void anInterruptWhileWaitingIsKeptAndTheWaitGoesOn() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AsyncDisconnect.start(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.ofPlatform().daemon().start(() -> {
            sleep(200);
            release.countDown();
        });
        Thread.currentThread().interrupt();
        AsyncDisconnect.await("the test");
        assertTrue(Thread.interrupted(), "the interrupt is handed back");
        assertEquals(0, release.getCount(), "it waited for the close all the same");
    }

    @Test
    void itIsOnWithPreheatOnAndACheckedDistantHorizons() {
        Path dir = Path.of("game");
        ConfigFiles.use(dir.resolve("unused-preheat.json"));
        Map<Feature, FeatureCheck.Result> on = new EnumMap<>(Feature.class);
        on.put(Feature.BACKGROUND_DH_CLOSE, new FeatureCheck.Result(FeatureCheck.State.ON, List.of()));
        Features.set(on);
        try {
            Platforms.use(FakePlatform.client(dir, "distanthorizons", "3.3.4-26.3"));
            assertTrue(AsyncDisconnect.enabled());
            Platforms.use(FakePlatform.client(dir, "distanthorizons", "3.4.0-26.3"));
            assertFalse(AsyncDisconnect.enabled(), "a Distant Horizons not checked");
            Features.set(Map.of());
            assertFalse(AsyncDisconnect.enabled(), "the feature did not apply");
        } finally {
            Platforms.use(null);
            Features.set(Map.of());
            ConfigFiles.use(null);
        }
    }
}
