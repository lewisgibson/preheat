package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchedSourcesTest {
    @TempDir
    Path dir;

    private static Map<String, @Nullable String> stages() {
        Map<String, @Nullable String> m = new LinkedHashMap<>();
        m.put("VERTEX", "#version 330 core\nvoid main() { gl_Position = vec4(0.0); }\n".repeat(50));
        m.put("GEOMETRY", null);
        m.put("FRAGMENT", "#version 330 core\nout vec4 c; void main() { c = vec4(1.0); } // é\n");
        return m;
    }

    @Test
    void encodeDecodeKeepsEveryStageInOrderIncludingAbsentOnes() {
        byte[] file = PatchedSources.encode(stages());
        Map<String, @Nullable String> back = PatchedSources.decode(file);
        assertEquals(stages(), back);
        assertEquals(java.util.List.of("VERTEX", "GEOMETRY", "FRAGMENT"), java.util.List.copyOf(back.keySet()));
        assertTrue(file.length < stages().get("VERTEX").length() / 4, "deflated");
    }

    @Test
    void aFlippedBitNeverGivesADifferentResultAndATruncationIsAlwaysCaught() {
        byte[] file = PatchedSources.encode(stages());
        for (int i = 0; i < file.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                byte[] bad = file.clone();
                bad[i] ^= (byte) (1 << bit);
                Map<String, @Nullable String> back = PatchedSources.decode(bad);
                // A deflate stream's last byte can carry padding bits nothing reads; flipping one changes nothing.
                assertTrue(back == null || back.equals(stages()), "byte " + i + " bit " + bit);
            }
        }
        for (int len = 0; len < file.length; len++) {
            assertNull(PatchedSources.decode(Arrays.copyOf(file, len)), "length " + len);
        }
        byte[] longer = Arrays.copyOf(file, file.length + 3);
        assertNull(PatchedSources.decode(longer), "trailing bytes");
    }

    @Test
    void storeAndLoadThroughTheFolderAndADamagedFileIsDeleted() throws IOException {
        PatchedSources.store(dir, "k", PatchedSources.encode(stages()));
        assertEquals(stages(), PatchedSources.load(dir, "k"));
        assertNull(PatchedSources.load(dir, "missing"));
        Path f = dir.resolve("k" + PatchedSources.SUFFIX);
        byte[] b = Files.readAllBytes(f);
        b[b.length - 2] ^= 1;
        Files.write(f, b);
        assertNull(PatchedSources.load(dir, "k"));
        assertFalse(Files.exists(f));
        try (var s = Files.list(dir)) {
            assertEquals(0, s.count(), "no temporary file left behind");
        }
    }

    @Test
    void notOursIsNull() {
        assertNull(PatchedSources.decode("not a patch file at all".getBytes(StandardCharsets.US_ASCII)));
        assertNull(PatchedSources.decode(new byte[0]));
        assertNotNull(PatchedSources.decode(PatchedSources.encode(new LinkedHashMap<>())));
    }

    @Test
    void sameStagesIgnoresOnlyIrisNumbering() {
        Map<String, @Nullable String> a = new LinkedHashMap<>();
        a.put("VERTEX", "float iris_depth_0_a(); float iris_depth_1_b();");
        a.put("GEOMETRY", null);
        Map<String, @Nullable String> b = new LinkedHashMap<>();
        b.put("VERTEX", "float iris_depth_5_a(); float iris_depth_2_b();");
        b.put("GEOMETRY", null);
        assertTrue(PatchedSources.sameStages(a, b));
        b.put("VERTEX", "float iris_depth_5_a(); float iris_depth_2_c();");
        assertFalse(PatchedSources.sameStages(a, b));
        b.put("VERTEX", a.get("VERTEX"));
        b.put("GEOMETRY", "x");
        assertFalse(PatchedSources.sameStages(a, b), "absent against present");
        b.remove("GEOMETRY");
        assertFalse(PatchedSources.sameStages(a, b), "a missing stage");
    }

    private static byte[] withPlain(byte[] plain, int claimedLength) {
        java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(plain);
        java.util.zip.Deflater d = new java.util.zip.Deflater();
        d.setInput(plain);
        d.finish();
        byte[] buf = new byte[plain.length + 64];
        int n = d.deflate(buf);
        d.end();
        java.nio.ByteBuffer b =
                java.nio.ByteBuffer.allocate(PatchedSources.HEADER + n).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(PatchedSources.MAGIC)
                .putInt(claimedLength)
                .putLong(crc.getValue())
                .put(buf, 0, n);
        return b.array();
    }

    @Test
    void wellFormedFilesWithImpossibleContentsAreRefused() {
        java.nio.ByteBuffer tooMany = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        tooMany.putInt(1000);
        assertNull(PatchedSources.decode(withPlain(tooMany.array(), 4)), "more stages than any program has");
        java.nio.ByteBuffer negative = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        negative.putInt(-1);
        assertNull(PatchedSources.decode(withPlain(negative.array(), 4)));
        java.nio.ByteBuffer badString = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        badString.putInt(1).putInt(99);
        assertNull(PatchedSources.decode(withPlain(badString.array(), 8)), "a name longer than the file");
        assertNull(PatchedSources.decode(withPlain(new byte[4], 1 << 30)), "a length no shader has");
        assertNull(PatchedSources.decode(withPlain(new byte[4], -5)));
        assertNull(PatchedSources.decode(withPlain(new byte[8], 4)), "longer than it says");
        assertNull(PatchedSources.decode(withPlain(new byte[4], 8)), "shorter than it says");
    }

    /** {@code plain} deflated under a valid header, as encode() writes it, so only the contents can be wrong. */
    private static byte[] packed(byte[] plain) {
        java.util.zip.CRC32C crc = new java.util.zip.CRC32C();
        crc.update(plain);
        java.util.zip.Deflater d = new java.util.zip.Deflater();
        d.setInput(plain);
        d.finish();
        byte[] buf = new byte[plain.length + 64];
        int n = d.deflate(buf);
        d.end();
        java.nio.ByteBuffer head =
                java.nio.ByteBuffer.allocate(PatchedSources.HEADER + n).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        head.put(PatchedSources.MAGIC)
                .putInt(plain.length)
                .putLong(crc.getValue())
                .put(buf, 0, n);
        return head.array();
    }

    private static byte[] plain(int... ints) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(4 * ints.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i : ints) {
            b.putInt(i);
        }
        return b.array();
    }

    @Test
    void contentsThatDoNotAddUpAreRefused() {
        assertEquals(Map.of(), PatchedSources.decode(packed(plain(0))), "no stages is well formed");
        assertNull(PatchedSources.decode(packed(plain(0, 7))), "bytes left over after the last stage");
        assertNull(PatchedSources.decode(packed(plain(1, -1))), "a stage name of negative length");
        assertNull(PatchedSources.decode(packed(plain(1, 50))), "a stage name longer than what is left");
    }

    @Test
    void anAbsentStageMatchesOnlyAnAbsentStage() {
        Map<String, @Nullable String> absent = new java.util.HashMap<>();
        absent.put("GEOMETRY", null);
        Map<String, @Nullable String> present = Map.of("GEOMETRY", "void main() {}");
        assertFalse(PatchedSources.sameStages(absent, present));
        assertFalse(PatchedSources.sameStages(present, absent));
        assertTrue(PatchedSources.sameStages(absent, absent));
    }
}
