package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

/**
 * The regression on NeoForge 26.3 (2026-10-09): Mixin's bytecode service there found neither Preheat's classes nor the
 * game's at startup, so every feature read as missing. Preheat's classes now come from its jar, and the game's from the
 * class loader when the service has nothing.
 */
class OwnClassesTest {
    @Test
    void preheatsOwnClassesComeFromItsJarNeverFromTheService() {
        ShapeCheck.ClassSource service = name -> null; // what NeoForge's service gave for Preheat's classes
        ShapeCheck.ClassSource source = OwnClasses.over(service, getClass().getClassLoader());
        ClassNode mixin = source.get(FeatureCheck.MIXINS + "keep/PipelineManagerMixin");
        assertNotNull(mixin, "a mixin class");
        assertEquals("com/lewisgibson/preheat/mixin/keep/PipelineManagerMixin", mixin.name);
        assertNotNull(source.get(FeatureCheck.ROOT + "keep/Prebuild"), "a helper class");
        assertNull(source.get(FeatureCheck.ROOT + "no/SuchClass"));
    }

    @Test
    void everythingElseStillComesFromTheService() {
        ClassNode iris = new ClassNode();
        iris.name = "net/irisshaders/iris/Iris";
        ShapeCheck.ClassSource service = Map.of("net/irisshaders/iris/Iris", iris)::get;
        ShapeCheck.ClassSource source = OwnClasses.over(service, getClass().getClassLoader());
        assertEquals(iris, source.get("net/irisshaders/iris/Iris"), "the service first");
        assertNotNull(
                source.get("net/minecraft/client/Minecraft"), "then the class loader, when the service has nothing");
        assertNull(source.get("net/minecraft/NoSuchClass"));
    }

    @Test
    void everyFeatureFitsWhenTheServiceFindsNothingAsOnNeoForge() {
        // The test classpath has the game's jars, standing in for the class loader on NeoForge.
        ShapeCheck.ClassSource source = OwnClasses.over(name -> null, getClass().getClassLoader());
        for (var e : FeatureCheck.evaluate(source, true).entrySet()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    e.getValue().on(), e.getKey() + ": " + e.getValue().problems());
        }
    }

    @Test
    void anUnreadableClassFileIsMissing() {
        ClassLoader garbage = new ClassLoader(null) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                return new java.io.ByteArrayInputStream(new byte[] {1, 2, 3});
            }
        };
        assertNull(OwnClasses.read(garbage, "net/minecraft/client/Minecraft"));
    }
}
