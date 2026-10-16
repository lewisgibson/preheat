package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Other client mods players run beside Preheat (deps.json's {@code neighbours}): every method one of their mixins
 * injects into, wraps, redirects or overwrites is compared with the methods Preheat's mixins inject into. Two mods
 * injecting into one method usually coexist, but it is where a conflict would be, so every overlap is listed in
 * {@code neighbour-overlaps.txt} after being looked at, and a new one fails this test until it has been.
 */
class NeighbourMixinsTest {
    /** Annotations whose {@code method} attribute names the methods they change. */
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    @Test
    void noNeighbourInjectsIntoAMethodPreheatDoesUnlessReviewed() throws IOException {
        Path neighbours =
                Path.of(System.getProperty("preheat.matrix", "libs/matrix")).resolveSibling("neighbours");
        if (System.getenv("CI") != null) {
            assertTrue(
                    Files.isDirectory(neighbours), "the neighbour jars are missing; CI must run scripts/fetch-deps.py");
        }
        assumeTrue(Files.isDirectory(neighbours), "run scripts/fetch-deps.py first");
        Map<String, Path> preheatClasses = Map.of(
                "26.3",
                Path.of(Splitter.on(java.io.File.pathSeparator)
                        .split(System.getProperty("preheat.classes", "build/classes/java/main"))
                        .iterator()
                        .next()),
                "26.2",
                Path.of(System.getProperty("preheat.classes.blaze3d", "build/classes/blaze3d")));
        Set<String> overlaps = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> lines = Files.list(neighbours)) {
            for (Path line : lines.sorted().toList()) {
                String mc = line.getFileName().toString();
                Set<String> ours = targetsOfDirectory(preheatClasses.get(mc).resolve("com/lewisgibson/preheat/mixin"));
                assertFalse(ours.isEmpty(), "Preheat's own mixins were not found for " + mc);
                try (Stream<Path> jars = Files.list(line)) {
                    for (Path jar : jars.sorted().toList()) {
                        Set<String> theirs = targetsOfJar(Files.readAllBytes(jar));
                        scanned++;
                        for (String t : theirs) {
                            if (ours.contains(t)) {
                                overlaps.add(mc + " " + jar.getFileName() + " " + t);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(scanned > 0);
        Set<String> expected = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream("/neighbour-overlaps.txt")) {
            assertFalse(in == null, "src/test/resources/neighbour-overlaps.txt is missing");
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                if (!l.isBlank() && !l.startsWith("#")) {
                    expected.add(l.trim());
                }
            }
        }
        assertEquals(
                expected,
                overlaps,
                "another mod now injects into a method Preheat injects into; check the two coexist, then update "
                        + "src/test/resources/neighbour-overlaps.txt");
    }

    /** "owner#method" for every method the mixins under {@code dir} change. */
    private static Set<String> targetsOfDirectory(Path dir) throws IOException {
        Set<String> out = new TreeSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                out.addAll(targets(read(Files.readAllBytes(f))));
            }
        }
        return out;
    }

    /** The same for every mixin a mod jar's configs list, Jar-in-Jar included. */
    static Set<String> targetsOfJar(byte[] jar) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        List<byte[]> nested = new ArrayList<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                byte[] b = z.readAllBytes();
                entries.put(e.getName(), b);
                if (e.getName().startsWith("META-INF/jars/") && e.getName().endsWith(".jar")) {
                    nested.add(b);
                }
            }
        }
        Set<String> out = new TreeSet<>();
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String name = e.getKey();
            if (!name.contains("/") && name.endsWith(".json") && name.contains("mixins")) {
                JsonObject config = JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!config.has("package")) {
                    continue;
                }
                String pkg = config.get("package").getAsString().replace('.', '/');
                for (String section : List.of("mixins", "client", "server")) {
                    JsonArray list = config.has(section) ? config.getAsJsonArray(section) : new JsonArray();
                    for (JsonElement m : list) {
                        byte[] cls = entries.get(pkg + "/" + m.getAsString().replace('.', '/') + ".class");
                        if (cls != null) {
                            out.addAll(targets(read(cls)));
                        }
                    }
                }
            }
        }
        for (byte[] b : nested) {
            out.addAll(targetsOfJar(b));
        }
        return out;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode n = new ClassNode();
        new ClassReader(bytes).accept(n, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
        return n;
    }

    /** "owner#method" for each method a mixin class changes; empty for a class that is not a mixin. */
    static Set<String> targets(ClassNode mixin) {
        List<String> owners = new ArrayList<>();
        for (AnnotationNode a : annotations(mixin.invisibleAnnotations, mixin.visibleAnnotations)) {
            if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") && a.values != null) {
                for (int i = 0; i < a.values.size(); i += 2) {
                    Object v = a.values.get(i + 1);
                    if (v instanceof List<?> list) {
                        for (Object o : list) {
                            owners.add(
                                    o instanceof Type t
                                            ? t.getInternalName()
                                            : String.valueOf(o).replace('.', '/'));
                        }
                    }
                }
            }
        }
        Set<String> out = new TreeSet<>();
        if (owners.isEmpty()) {
            return out;
        }
        for (MethodNode m : mixin.methods) {
            for (AnnotationNode a : annotations(m.invisibleAnnotations, m.visibleAnnotations)) {
                if (a.desc.equals(OVERWRITE)) {
                    owners.forEach(o -> out.add(o + "#" + m.name));
                }
                if (!INJECTORS.contains(a.desc) || a.values == null) {
                    continue;
                }
                for (int i = 0; i < a.values.size(); i += 2) {
                    if (a.values.get(i).equals("method") && a.values.get(i + 1) instanceof List<?> methods) {
                        for (Object target : methods) {
                            String name = methodName(String.valueOf(target));
                            owners.forEach(o -> out.add(o + "#" + name));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** "name", "name(desc)", "Lowner;name(desc)" or "owner.name" to the bare name. */
    static String methodName(String target) {
        String t = target;
        int paren = t.indexOf('(');
        if (paren >= 0) {
            t = t.substring(0, paren);
        }
        int semi = t.lastIndexOf(';');
        if (semi >= 0) {
            t = t.substring(semi + 1);
        }
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t;
    }

    @SafeVarargs
    private static List<AnnotationNode> annotations(List<AnnotationNode>... lists) {
        List<AnnotationNode> out = new ArrayList<>();
        for (List<AnnotationNode> l : lists) {
            if (l != null) {
                out.addAll(l);
            }
        }
        return out;
    }

    @Test
    void methodNamesAreReadFromEveryFormMixinAccepts() {
        assertEquals("render", methodName("render"));
        assertEquals("render", methodName("render(F)V"));
        assertEquals("render", methodName("Lnet/minecraft/A;render(F)V"));
        assertEquals("<init>", methodName("<init>"));
    }
}
