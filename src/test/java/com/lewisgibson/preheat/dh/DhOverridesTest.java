package com.lewisgibson.preheat.dh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DhOverridesTest {
    /** A DHCompatInternal with these overrides set, recording what is done to it. */
    static final class Fake implements DhOverrides.Internal {
        final @Nullable Object solid;
        final @Nullable Object shadow;
        final @Nullable Object generic;
        final List<String> calls = new ArrayList<>();

        Fake(@Nullable Object solid, @Nullable Object shadow, @Nullable Object generic) {
            this.solid = solid;
            this.shadow = shadow;
            this.generic = generic;
        }

        @Override
        public @Nullable Object solidFramebuffer() {
            return solid;
        }

        @Override
        public @Nullable Object shadowFramebuffer() {
            return shadow;
        }

        @Override
        public @Nullable Object genericShader() {
            return generic;
        }

        @Override
        public void unbindFramebuffer(Object framebuffer) {
            calls.add("framebuffer " + framebuffer);
        }

        @Override
        public void unbindShader(Object shader) {
            calls.add("shader " + shader);
        }

        @Override
        public void resetForReuse() {
            calls.add("reset");
        }
    }

    @Test
    void onlyTheOverridesThePipelineSetAreUnbound() {
        Fake all = new Fake("solid", "shadow", "generic");
        DhOverrides.unbind(all);
        assertEquals(List.of("framebuffer solid", "framebuffer shadow", "shader generic"), all.calls);
        Fake none = new Fake(null, null, null);
        DhOverrides.unbind(none);
        assertEquals(List.of(), none.calls);
        DhOverrides.unbind(null);
    }

    @Test
    void aPipelineWithDistantHorizonsStateIsResetForReuse() {
        Fake dh = new Fake(null, null, null);
        DhOverrides.reset(dh);
        DhOverrides.reset(null);
        assertEquals(List.of("reset"), dh.calls);
    }
}
