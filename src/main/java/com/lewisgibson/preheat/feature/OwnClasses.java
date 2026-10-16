package com.lewisgibson.preheat.feature;

import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Class files for the startup check, as bytes, before Mixin has applied anything. Preheat's own come straight from its
 * jar; anything else comes from Mixin's bytecode service, and when the service has nothing, from the class loader's
 * resources (the untransformed class files). On NeoForge 26.3 the service finds neither Preheat's classes nor the game's
 * at that point, so every feature read as missing and Preheat did nothing there (found in the lab, 2026-10-09).
 */
public final class OwnClasses {
    private OwnClasses() {}

    /**
     * A source that reads Preheat's classes from {@code loader}'s resources, and anything else from {@code service} or,
     * when that has nothing, from {@code loader}'s resources.
     */
    public static ShapeCheck.ClassSource over(ShapeCheck.ClassSource service, ClassLoader loader) {
        return name -> {
            if (name.startsWith(FeatureCheck.ROOT)) {
                return read(loader, name);
            }
            ClassNode n = service.get(name);
            return n != null ? n : read(loader, name);
        };
    }

    /** The class file {@code internalName} from {@code loader}'s resources, or null when it is not there. */
    static @Nullable ClassNode read(ClassLoader loader, String internalName) {
        InputStream in = loader.getResourceAsStream(internalName + ".class");
        if (in == null) {
            return null;
        }
        try (in) {
            ClassNode n = new ClassNode();
            new ClassReader(in.readAllBytes()).accept(n, ClassReader.SKIP_FRAMES);
            return n;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
