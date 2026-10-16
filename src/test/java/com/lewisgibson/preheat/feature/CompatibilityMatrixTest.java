package com.lewisgibson.preheat.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.base.Splitter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Runs the same feature check the game runs at startup, against the real jars of every row in deps.json (Minecraft,
 * its LWJGL, Iris, Sodium, Distant Horizons where it exists), with Preheat's compiled classes. The result is written
 * to build/reports/compat/matrix.md and compared with src/test/resources/compat-expected.txt, so a change in what
 * applies where is always a reviewed change to that file (and to the README's matrix).
 */
class CompatibilityMatrixTest {
    @Test
    void everyRowMatchesTheExpectedMatrix() throws IOException {
        Path matrix = Path.of(System.getProperty("preheat.matrix", "libs/matrix"));
        if (System.getenv("CI") != null) {
            assertTrue(Files.isDirectory(matrix), "the matrix jars are missing; CI must run scripts/fetch-deps.py");
        }
        assumeTrue(Files.isDirectory(matrix), "run scripts/fetch-deps.py first");
        List<Path> classDirs = new ArrayList<>();
        for (String p : Splitter.on(File.pathSeparator)
                .split(System.getProperty("preheat.classes", "build/classes/java/main"))) {
            classDirs.add(Path.of(p));
        }
        // The 26.1 and 26.2 jars carry the relocated classes (build.gradle.kts, Relocate).
        List<Path> blaze3dDirs =
                List.of(Path.of(System.getProperty("preheat.classes.blaze3d", "build/classes/blaze3d")));
        Map<String, String> actual = new TreeMap<>();
        Map<String, String> fingerprints = new TreeMap<>();
        StringBuilder report =
                new StringBuilder("| Row | Distant Horizons | Feature | State | Why |\n|---|---|---|---|---|\n");
        try (Stream<Path> rows = Files.list(matrix)) {
            for (Path row : rows.sorted().toList()) {
                List<Path> jars = new ArrayList<>();
                try (Stream<Path> files = Files.walk(row)) {
                    files.filter(f -> f.toString().endsWith(".jar")).forEach(jars::add);
                }
                boolean dhJar =
                        jars.stream().anyMatch(j -> j.getFileName().toString().startsWith("DistantHorizons"));
                for (boolean dh : dhJar ? new boolean[] {false, true} : new boolean[] {false}) {
                    boolean relocated = !row.getFileName().toString().startsWith("26.3");
                    try (JarSource source = new JarSource(relocated ? blaze3dDirs : classDirs, jars)) {
                        fingerprints.put(
                                row.getFileName().toString(),
                                "pipeline " + PipelineFingerprint.of(source) + ", patch "
                                        + PatchFingerprint.of(source));
                        Map<Feature, FeatureCheck.Result> results = FeatureCheck.evaluate(source, dh);
                        String name = row.getFileName() + (dh ? "+dh" : "");
                        List<String> on = new ArrayList<>();
                        for (Feature f : Feature.values()) {
                            FeatureCheck.Result r = results.get(f);
                            if (r.state() == FeatureCheck.State.ON) {
                                on.add(f.name());
                            }
                            report.append("| ")
                                    .append(row.getFileName())
                                    .append(" | ")
                                    .append(dh ? "yes" : "no")
                                    .append(" | ")
                                    .append(f.name())
                                    .append(" | ")
                                    .append(r.state())
                                    .append(" | ")
                                    .append(String.join("; ", r.problems()).replace("|", "\\|"))
                                    .append(" |\n");
                        }
                        actual.put(name, String.join(",", on));
                    }
                }
            }
        }
        Path reports = Path.of(System.getProperty("preheat.reports", "build/reports/compat"));
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("matrix.md"), report.toString(), StandardCharsets.UTF_8);
        StringBuilder flat = new StringBuilder();
        actual.forEach((k, v) -> flat.append(k).append('=').append(v).append('\n'));
        Files.writeString(reports.resolve("compat-actual.txt"), flat.toString(), StandardCharsets.UTF_8);
        StringBuilder fps = new StringBuilder();
        fingerprints.forEach((k, v) -> fps.append(k).append('=').append(v).append('\n'));
        Files.writeString(reports.resolve("fingerprints.txt"), fps.toString(), StandardCharsets.UTF_8);

        Map<String, String> expected = new TreeMap<>();
        try (InputStream in = getClass().getResourceAsStream("/compat-expected.txt")) {
            assertFalse(in == null, "src/test/resources/compat-expected.txt is missing");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    int eq = line.indexOf('=');
                    expected.put(
                            line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
        assertEquals(
                expected,
                actual,
                "the compatibility matrix changed; see build/reports/compat/matrix.md and update compat-expected.txt and the README");
    }

    /** Class files from directories and jars, first match wins; jars nested in a jar (Jar-in-Jar) are read too. */
    static final class JarSource implements ShapeCheck.ClassSource, AutoCloseable {
        private final List<Path> dirs;
        private final List<ZipFile> zips = new ArrayList<>();
        private final Map<String, ClassNode> cache = new HashMap<>();
        private final List<Path> extracted = new ArrayList<>();

        JarSource(List<Path> dirs, List<Path> jars) throws IOException {
            this.dirs = dirs;
            for (Path j : jars) {
                ZipFile z = new ZipFile(j.toFile());
                zips.add(z);
                // NeoForge's Sodium ships its classes in a nested jar.
                for (ZipEntry e : z.stream().toList()) {
                    if (e.getName().startsWith("META-INF/jarjar/")
                            && e.getName().endsWith(".jar")) {
                        Path tmp = Files.createTempFile("preheat-jij", ".jar");
                        try (InputStream in = z.getInputStream(e)) {
                            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        extracted.add(tmp);
                        zips.add(new ZipFile(tmp.toFile()));
                    }
                }
            }
        }

        @Override
        public ClassNode get(String internalName) {
            if (cache.containsKey(internalName)) {
                return cache.get(internalName);
            }
            ClassNode node = null;
            try {
                for (Path d : dirs) {
                    Path f = d.resolve(internalName + ".class");
                    if (Files.isRegularFile(f)) {
                        node = read(Files.readAllBytes(f));
                        break;
                    }
                }
                if (node == null) {
                    for (ZipFile z : zips) {
                        ZipEntry e = z.getEntry(internalName + ".class");
                        if (e != null) {
                            try (InputStream in = z.getInputStream(e)) {
                                node = read(in.readAllBytes());
                            }
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            cache.put(internalName, node);
            return node;
        }

        private static ClassNode read(byte[] bytes) {
            ClassNode n = new ClassNode();
            new ClassReader(bytes).accept(n, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return n;
        }

        @Override
        public void close() throws IOException {
            for (ZipFile z : zips) {
                z.close();
            }
            for (Path p : extracted) {
                Files.deleteIfExists(p);
            }
        }
    }
}
