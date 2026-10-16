package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lewisgibson.preheat.dh.DhOverrides;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Putting a pipeline aside and bringing it back, as calls on a fake Iris. */
class ReactivationTest {
    /** A storage buffer: its binding index, GL name, and how it is sized and filled. */
    record Buffer(int index, int id, boolean relative, boolean content) {}

    /** Iris with one pipeline's state as the test sets it, recording every call. */
    static final class FakeIris implements Reactivation.Iris<String, Buffer, String> {
        final List<String> calls = new ArrayList<>();
        boolean iris = true;

        @Nullable
        List<@Nullable Buffer> buffers = Arrays.asList(new Buffer(0, 10, true, false), null);

        boolean reloadRequired;
        boolean level = true;
        boolean shadow = true;
        List<@Nullable String> setup = Arrays.asList(null, "setup0", "setup1");
        int maxUnits = 8;

        @Override
        public boolean isIris(String pipeline) {
            return iris;
        }

        @Override
        public void activeTexture(int unit) {
            calls.add("activeTexture " + (unit - Reactivation.GL_TEXTURE0));
        }

        @Override
        public void unbindAllSamplers() {
            calls.add("unbindAllSamplers");
        }

        @Override
        public void bindTexture(int texture) {
            calls.add("bindTexture " + texture);
        }

        @Override
        public void bindFramebuffer(int target, int framebuffer) {
            calls.add("bindFramebuffer " + Integer.toHexString(target) + " " + framebuffer);
        }

        @Override
        public void bindBuffer(int target, int buffer) {
            calls.add("bindBuffer " + buffer);
        }

        @Override
        public void bindBufferBase(int target, int index, int buffer) {
            calls.add("bindBufferBase " + index + " " + buffer);
        }

        @Override
        public boolean hasStorageBuffers(String pipeline) {
            return buffers != null;
        }

        @Override
        public List<@Nullable Buffer> storageBuffers(String pipeline) {
            return buffers;
        }

        @Override
        public int storageWidth(String pipeline) {
            return 1920;
        }

        @Override
        public int storageHeight(String pipeline) {
            return 1080;
        }

        @Override
        public int index(Buffer buffer) {
            return buffer.index();
        }

        @Override
        public int id(Buffer buffer) {
            return buffer.id();
        }

        @Override
        public boolean relative(Buffer buffer) {
            return buffer.relative();
        }

        @Override
        public void resize(Buffer buffer, int width, int height) {
            calls.add("resize " + buffer.id() + " " + width + "x" + height);
        }

        @Override
        public boolean hasContent(Buffer buffer) {
            return buffer.content();
        }

        @Override
        public void uploadContent(Buffer buffer) {
            calls.add("upload " + buffer.id());
        }

        @Override
        public void zero(Buffer buffer) {
            calls.add("zero " + buffer.id());
        }

        @Override
        public void setupBuffers(String pipeline) {
            calls.add("setupBuffers");
        }

        @Override
        public int maxStorageUnits() {
            return maxUnits;
        }

        @Override
        public void resetSystemTime() {
            calls.add("resetSystemTime");
        }

        @Override
        public boolean reloadRequired() {
            return reloadRequired;
        }

        @Override
        public boolean levelLoaded() {
            return level;
        }

        @Override
        public void remeshLevel() {
            calls.add("remeshLevel");
        }

        @Override
        public void clearReloadRequired() {
            calls.add("clearReloadRequired");
        }

        @Override
        public void applyPackSettings(String pipeline) {
            calls.add("applyPackSettings " + pipeline);
        }

        @Override
        public void clearCustomImages(String pipeline) {
            calls.add("clearCustomImages");
        }

        @Override
        public void requireFullClear(String pipeline) {
            calls.add("requireFullClear");
        }

        @Override
        public boolean hasShadowTargets(String pipeline) {
            return shadow;
        }

        @Override
        public void requireShadowFullClear(String pipeline) {
            calls.add("requireShadowFullClear");
        }

        @Override
        public List<@Nullable String> setupPrograms(String pipeline) {
            return setup;
        }

        @Override
        public void fullClear(String pipeline) {
            calls.add("fullClear");
        }

        @Override
        public void dispatch(String program) {
            calls.add("dispatch " + program);
        }

        @Override
        public void unbindCompute() {
            calls.add("unbindCompute");
        }

        @Override
        public DhOverrides.@Nullable Internal distantHorizons(String pipeline) {
            calls.add("distantHorizons");
            return null;
        }
    }

    @Test
    void puttingAPipelineAsideUnbindsEveryUnitFramebufferAndStorageBuffer() {
        FakeIris iris = new FakeIris();
        new Reactivation<>(iris, true).deactivate("nether");
        assertEquals(List.of("activeTexture 0", "unbindAllSamplers", "bindTexture 0"), iris.calls.subList(0, 3));
        assertEquals("activeTexture 15", iris.calls.get(45));
        assertEquals(
                List.of(
                        "activeTexture 0",
                        "bindFramebuffer 8ca8 0",
                        "bindFramebuffer 8ca9 0",
                        "bindFramebuffer 8d40 0",
                        "bindBufferBase 0 0",
                        "distantHorizons"),
                iris.calls.subList(48, iris.calls.size()));
    }

    @Test
    void aVanillaPipelineOrOneWithoutBuffersOrDistantHorizonsHasLessToPutAside() {
        FakeIris vanilla = new FakeIris();
        vanilla.iris = false;
        new Reactivation<>(vanilla, true).deactivate("vanilla");
        assertTrue(vanilla.calls.isEmpty());
        FakeIris bare = new FakeIris();
        bare.buffers = null;
        new Reactivation<>(bare, false).deactivate("nether");
        assertEquals("bindFramebuffer 8d40 0", bare.calls.get(bare.calls.size() - 1));
    }

    @Test
    void bringingItBackStartsItAsANewPipelineWould() {
        FakeIris iris = new FakeIris();
        iris.buffers = Arrays.asList(
                new Buffer(0, 10, true, false), null, new Buffer(2, 12, false, true), new Buffer(3, 13, false, false));
        iris.reloadRequired = true;
        new Reactivation<>(iris, true).reactivate("nether");
        assertEquals(
                List.of(
                        "resetSystemTime",
                        "applyPackSettings nether",
                        "resize 10 1920x1080",
                        "bindBuffer 12",
                        "upload 12",
                        "bindBuffer 13",
                        "zero 13",
                        "bindBuffer 0",
                        "setupBuffers",
                        "clearCustomImages",
                        "requireFullClear",
                        "requireShadowFullClear",
                        "fullClear",
                        "dispatch setup0",
                        "dispatch setup1",
                        "unbindCompute",
                        "distantHorizons",
                        "remeshLevel",
                        "clearReloadRequired"),
                iris.calls);
    }

    @Test
    void withoutBuffersEveryUnitIsUnboundAndWithoutSetupProgramsNothingIsCleared() {
        FakeIris iris = new FakeIris();
        iris.buffers = null;
        iris.maxUnits = 20;
        iris.shadow = false;
        iris.setup = Arrays.asList(null, null);
        iris.reloadRequired = true;
        iris.level = false;
        new Reactivation<>(iris, false).reactivate("end");
        List<String> expected = new ArrayList<>(List.of("resetSystemTime", "applyPackSettings end"));
        for (int i = 0; i < 16; i++) {
            expected.add("bindBufferBase " + i + " 0");
        }
        expected.addAll(List.of("clearCustomImages", "requireFullClear", "clearReloadRequired"));
        assertEquals(expected, iris.calls);
    }

    @Test
    void aVanillaPipelineOnlyHasItsTimersReset() {
        FakeIris vanilla = new FakeIris();
        vanilla.iris = false;
        new Reactivation<>(vanilla, true).reactivate("vanilla");
        assertEquals(List.of("resetSystemTime"), vanilla.calls);
    }
}
