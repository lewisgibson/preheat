package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.gl.blending.AlphaTests;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.helpers.Tri;
import net.irisshaders.iris.pipeline.transform.Patch;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.preheattest.FakeSodiumParameters;
import net.irisshaders.preheattest.FakeStageParameters;
import net.irisshaders.preheattest.OddValues;
import org.junit.jupiter.api.Test;

/** Against classes shaped like Iris's parameters, holding Iris's real records and enums. */
class PatchKeyTest {
    private static Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textures(String... names) {
        Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> m = new Object2ObjectOpenHashMap<>();
        for (String n : names) {
            m.put(new Tri<>(n, TextureType.TEXTURE_2D, TextureStage.GBUFFERS_AND_SHADOW), n + "_bound");
        }
        return m;
    }

    private static FakeSodiumParameters sodium(
            Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> tex,
            Set<String> overrides,
            boolean shadow) {
        return new FakeSodiumParameters(Patch.SODIUM, tex, overrides, AlphaTests.ONE_TENTH_ALPHA, shadow);
    }

    @Test
    void equalParametersDescribeTheSameWhateverTheMapAndSetOrder() {
        Set<String> a = new LinkedHashSet<>(List.of("x", "y", "z"));
        Set<String> b = new LinkedHashSet<>(List.of("z", "y", "x"));
        String one = PatchKey.describe(sodium(textures("colortex0", "colortex1", "noisetex"), a, false));
        String two = PatchKey.describe(sodium(textures("noisetex", "colortex1", "colortex0"), b, false));
        assertNotNull(one);
        assertEquals(one, two);
    }

    @Test
    void everyFieldIsInTheDescription() {
        String base = PatchKey.describe(sodium(textures("colortex0"), Set.of(), false));
        assertNotEquals(base, PatchKey.describe(sodium(textures("colortex0"), Set.of(), true)), "shadow flag");
        assertNotEquals(base, PatchKey.describe(sodium(textures("colortex1"), Set.of(), false)), "texture map");
        assertNotEquals(base, PatchKey.describe(sodium(textures("colortex0"), Set.of("a"), false)), "overrides");
        assertNotEquals(
                base,
                PatchKey.describe(new FakeSodiumParameters(
                        Patch.SODIUM, textures("colortex0"), Set.of(), AlphaTests.HALF_ALPHA, false)),
                "alpha test, a record nested inside");
        assertNotEquals(
                base,
                PatchKey.describe(new FakeStageParameters(
                        Patch.COMPOSITE, TextureStage.GBUFFERS_AND_SHADOW, textures("colortex0"), Set.of())),
                "another parameters class");
        assertTrue(base.contains("FakeSodiumParameters.shadow="), base);
        assertTrue(base.contains("AlphaTest.reference="), base);
    }

    @Test
    void theFieldsPatchingAssignsFirstAreLeftOut() {
        FakeSodiumParameters p = sodium(textures("colortex0"), Set.of(), false);
        String before = PatchKey.describe(p);
        p.type = PatchShaderType.FRAGMENT;
        p.name = "gbuffers_terrain";
        assertEquals(before, PatchKey.describe(p), "transformInternal assigns name and type before reading them");
        assertFalse(before.contains("FakeParameters.name="));
    }

    /** A class outside Iris that the key cannot write down stably. */
    static final class Foreign {
        @Override
        public String toString() {
            return "foreign@" + System.identityHashCode(this);
        }
    }

    @Test
    void aValueItCannotWriteDownStablyMakesTheProgramUncacheable() {
        Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> bad = textures("colortex0");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object2ObjectMap<Object, Object> raw = (Object2ObjectMap) bad;
        raw.put(new Foreign(), "x");
        assertNull(PatchKey.describe(sodium(bad, Set.of(), false)));
        assertNull(PatchKey.describe(new Foreign()), "not Iris's class");
        assertNull(PatchKey.describe(new java.util.concurrent.atomic.AtomicInteger(1)), "a mutable number");
    }

    @Test
    void plainValuesListsAndArraysAreWrittenInOrder() {
        assertEquals("list[\"1:a\", \"1:b\"]", PatchKey.describe(List.of("a", "b")));
        assertNotEquals(PatchKey.describe(List.of("a", "b")), PatchKey.describe(List.of("b", "a")));
        assertEquals("array[Integer(1),null,]", PatchKey.describe(new Integer[] {1, null}));
    }

    /** Iris-named cycle: a parameters object never refers back to itself, so a cycle is refused. */
    @Test
    void aCycleIsRefused() {
        List<Object> self = new java.util.ArrayList<>();
        self.add(self);
        assertNull(PatchKey.describe(self));
    }

    @Test
    void everyKindOfValueThatCannotBeWrittenRefusesTheWholeKey() {
        Foreign f = new Foreign();
        assertNull(PatchKey.describe(java.util.Map.of(f, "x")), "a map key");
        assertNull(PatchKey.describe(java.util.Map.of("x", f)), "a map value");
        assertNull(PatchKey.describe(List.of(f)), "a list element");
        assertNull(PatchKey.describe(new Object[] {f}), "an array element");
        assertNull(PatchKey.describe((Runnable) () -> {}), "a lambda");
        List<Object> deep = new java.util.ArrayList<>();
        List<Object> cur = deep;
        for (int i = 0; i < 12; i++) {
            List<Object> next = new java.util.ArrayList<>();
            cur.add(next);
            cur = next;
        }
        assertNull(PatchKey.describe(deep), "nested deeper than any parameters object");
        assertEquals("Character(c)", PatchKey.describe('c'));
        assertEquals("Boolean(true)", PatchKey.describe(true));
        assertEquals("set[\"1:a\"]", PatchKey.describe(Set.of("a")));
        assertEquals("map[\"1:a\"=null]", PatchKey.describe(java.util.Collections.singletonMap("a", null)));
    }

    @Test
    void irisNamedClassesThatSayNothingStableAboutTheirValueAreRefused() throws Exception {
        assertNull(PatchKey.describe(OddValues.anonymous()), "an anonymous class");
        assertNull(PatchKey.describe(OddValues.lambda()), "a lambda");
        assertNull(PatchKey.describe(OddValues.hidden()), "a hidden class");
        assertNull(PatchKey.describe(OddValues.synthetic()), "a synthetic class");
    }

    @Test
    void aNestedParametersObjectKeepsTheFieldsPatchingAssigns() {
        FakeStageParameters inner =
                new FakeStageParameters(Patch.VANILLA, TextureStage.GBUFFERS_AND_SHADOW, textures(), Set.of());
        inner.name = "gbuffers_terrain";
        String d = PatchKey.describe(new OddValues.Holder(inner));
        assertNotNull(d);
        assertTrue(d.contains("FakeParameters.name=\"16:gbuffers_terrain\""), d);
        assertFalse(d.contains("count"), "a static field is not part of the value");
    }

    @Test
    void aValueThatThrowsWhileItIsReadIsRefused() {
        java.util.Map<String, String> broken = new java.util.AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                throw new UnsupportedOperationException("not today");
            }
        };
        assertNull(PatchKey.describe(broken));
    }
}
