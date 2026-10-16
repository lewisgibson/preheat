package com.lewisgibson.preheat.keep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Free video memory as each kind of driver reports it, on a fake driver. */
class VideoMemoryTest {
    /** A driver with these strings, extensions and readings; each read of the error pops the next one. */
    static final class FakeGl implements VideoMemory.Gl {
        @Nullable
        String renderer = "NVIDIA GeForce RTX 5090/PCIe/SSE2";

        boolean nvx;
        boolean ati;
        final Deque<Integer> errors = new ArrayDeque<>();
        final Map<Integer, Integer> values = new HashMap<>();
        int rendererReads;
        RuntimeException fails;

        @Override
        public @Nullable String renderer() {
            rendererReads++;
            return renderer;
        }

        @Override
        public boolean nvxMemoryInfo() {
            if (fails != null) {
                throw fails;
            }
            return nvx;
        }

        @Override
        public boolean atiMemInfo() {
            return ati;
        }

        @Override
        public int error() {
            Integer e = errors.poll();
            return e == null ? 0 : e;
        }

        @Override
        public int integer(int pname) {
            return values.getOrDefault(pname, 0);
        }

        @Override
        public void integers(int pname, int[] into) {
            into[0] = values.getOrDefault(pname, 0);
        }
    }

    @Test
    void nvidiaReportsFreeAndTotalAndAChangingTotalIsNotBelieved() {
        FakeGl gl = new FakeGl();
        gl.nvx = true;
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, 32 << 20);
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, 20 << 20);
        VideoMemory m = new VideoMemory(gl);
        assertEquals(new KeptBudget.Memory(20 << 10, 32 << 10), m.read());
        assertEquals(new KeptBudget.Memory(20 << 10, 32 << 10), m.read());
        assertEquals(1, gl.rendererReads, "the renderer is looked at once");
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, 64 << 20);
        assertNull(m.read(), "the total changed");
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, 32 << 20);
        gl.errors.add(0);
        gl.errors.add(0x500);
        assertNull(m.read(), "the driver said the reading was an error");
    }

    @Test
    void aFirstReadingThatDoesNotHoldTogetherIsNotRemembered() {
        FakeGl gl = new FakeGl();
        gl.nvx = true;
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, 1 << 20);
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, 2 << 20);
        VideoMemory m = new VideoMemory(gl);
        assertNull(m.read(), "more free than there is");
        gl.values.put(VideoMemory.GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, 4 << 20);
        assertEquals(new KeptBudget.Memory(2 << 10, 4 << 10), m.read(), "so the first good total is the session's");
    }

    @Test
    void amdReportsFreeOnly() {
        FakeGl gl = new FakeGl();
        gl.ati = true;
        gl.values.put(VideoMemory.TEXTURE_FREE_MEMORY_ATI, 8 << 20);
        assertEquals(new KeptBudget.Memory(8 << 10, 0), new VideoMemory(gl).read());
        gl.errors.add(0);
        gl.errors.add(0x500);
        assertNull(new VideoMemory(gl).read());
    }

    @Test
    void softwareRenderersOtherDriversAndErrorsHaveNoReading() {
        for (String software : new String[] {
            "llvmpipe (LLVM 19.1.7, 256 bits)",
            "softpipe",
            "Mesa swrast",
            "lavapipe",
            "Google SwiftShader",
            "Microsoft Basic Render Driver"
        }) {
            FakeGl gl = new FakeGl();
            gl.renderer = software;
            gl.nvx = true;
            gl.ati = true;
            assertNull(new VideoMemory(gl).read(), software);
        }
        FakeGl none = new FakeGl();
        none.renderer = null;
        assertNull(new VideoMemory(none).read(), "neither extension");
        FakeGl broken = new FakeGl();
        broken.fails = new IllegalStateException("no context");
        assertNull(new VideoMemory(broken).read());
    }
}
