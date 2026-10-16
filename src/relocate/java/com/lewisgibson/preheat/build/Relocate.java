package com.lewisgibson.preheat.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Builds the Minecraft 26.2 classes from the 26.3 ones. Minecraft 26.3 moved its OpenGL backend from
 * {@code com.mojang.blaze3d} to {@code com.mojang.renderpearl} without changing it; every reference is mapped back,
 * including the ones inside mixin annotations (injection point targets are strings). The compatibility matrix test
 * then checks the result against the real 26.2 jars like any other build.
 *
 * <p>Usage: Relocate &lt;classes dir&gt; &lt;output dir&gt;
 */
public final class Relocate {
    /** One renamed package, in internal form ("com/mojang/..."): 26.3's name and the name before it. */
    record Rename(String from, String to) {
        String apply(String s) {
            return s.replace(from, to).replace(from.replace('/', '.'), to.replace('/', '.'));
        }
    }

    static final List<Rename> PACKAGES = List.of(
            new Rename("com/mojang/renderpearl/backend/opengl/", "com/mojang/blaze3d/opengl/"),
            new Rename("com/mojang/renderpearl/api/vertex/", "com/mojang/blaze3d/vertex/"),
            // GpuDevice and DeviceInfo (the depth range the patch cache keys on) were in blaze3d.systems.
            new Rename("com/mojang/renderpearl/api/device/", "com/mojang/blaze3d/systems/"));

    private Relocate() {}

    /** {@code s} with every renamed package mapped back, in internal, dotted and descriptor form alike. */
    static String map(String s) {
        String out = s;
        for (Rename r : PACKAGES) {
            out = r.apply(out);
        }
        return out;
    }

    /** The remapper: class names, and string constants (mixin annotations name their targets in strings). */
    static Remapper remapper() {
        return new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String internalName) {
                return Relocate.map(internalName);
            }

            @Override
            public Object mapValue(Object value) {
                return value instanceof String s ? Relocate.map(s) : super.mapValue(value);
            }
        };
    }

    /** One class file mapped back. */
    static byte[] relocate(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, remapper()), 0);
        return writer.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: Relocate <classes dir> <output dir>");
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        try (Stream<Path> files = Files.walk(in)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path target = out.resolve(in.relativize(f).toString());
                Files.createDirectories(target.getParent());
                if (f.toString().endsWith(".class")) {
                    Files.write(target, relocate(Files.readAllBytes(f)));
                } else {
                    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
