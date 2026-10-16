package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramBinaryStoreTest {
    private static final byte[] IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];

    @TempDir
    Path tmp;

    private static ProgramBinaryStore.Entry entry(int format, byte[] binary) {
        return new ProgramBinaryStore.Entry(format, binary, IFACE);
    }

    @Test
    void keysAreStableAndSeeEveryPart() {
        String a = ProgramBinaryStore.key(List.of("NVIDIA 616.92", "gbuffer", "void main(){}"));
        assertEquals(a, ProgramBinaryStore.key(List.of("NVIDIA 616.92", "gbuffer", "void main(){}")));
        assertEquals(64, a.length());
        assertNotEquals(a, ProgramBinaryStore.key(List.of("NVIDIA 616.93", "gbuffer", "void main(){}")), "driver");
        assertNotEquals(a, ProgramBinaryStore.key(List.of("NVIDIA 616.92", "gbuffer", "void main(){ }")), "source");
        // Length prefixes: moving a boundary between parts is a different key.
        assertNotEquals(ProgramBinaryStore.key(List.of("ab", "c")), ProgramBinaryStore.key(List.of("a", "bc")));
    }

    @Test
    void encodeDecodeKeepsTheBinaryAndTheInterfaceHash() {
        byte[] bin = {1, 2, 3, 4, 5, 6, 7};
        byte[] iface = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];
        iface[0] = 42;
        iface[31] = -1;
        ProgramBinaryStore.Entry back =
                ProgramBinaryStore.decode(ProgramBinaryStore.encode(new ProgramBinaryStore.Entry(0x8E21, bin, iface)));
        assertEquals(0x8E21, back.format());
        assertArrayEquals(bin, back.binary());
        assertArrayEquals(iface, back.interfaceHash());
        assertThrows(IllegalArgumentException.class, () -> new ProgramBinaryStore.Entry(1, bin, new byte[3]));
    }

    @Test
    void everyFlippedBitAndEveryTruncationIsCaughtBeforeTheDriverSeesIt() {
        byte[] bin = new byte[64];
        new Random(1).nextBytes(bin);
        byte[] file = ProgramBinaryStore.encode(entry(0x8E21, bin));
        for (int i = 0; i < file.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                byte[] damaged = file.clone();
                damaged[i] ^= (byte) (1 << bit);
                ProgramBinaryStore.Entry e = ProgramBinaryStore.decode(damaged);
                // A flip in the format or the interface hash still decodes; the driver's format list and the
                // interface check after loading catch those. Every flip in the binary, its length or its CRC is caught
                // here.
                boolean inFormat = i >= ProgramBinaryStore.MAGIC.length && i < ProgramBinaryStore.MAGIC.length + 4;
                boolean inInterfaceHash = i >= ProgramBinaryStore.HEADER - ProgramBinaryStore.INTERFACE_HASH_BYTES
                        && i < ProgramBinaryStore.HEADER;
                boolean header = inFormat || inInterfaceHash;
                if (!header) {
                    assertNull(e, "flip at byte " + i + " bit " + bit);
                }
            }
        }
        for (int n = 0; n < file.length; n++) {
            assertNull(ProgramBinaryStore.decode(Arrays.copyOf(file, n)), "truncated to " + n);
        }
        byte[] old = file.clone();
        old[7] = '1';
        assertNull(ProgramBinaryStore.decode(old), "an older format version");
    }

    @Test
    void storeLoadAndDamagedFilesAreDeleted() throws Exception {
        ProgramBinaryStore s = new ProgramBinaryStore(tmp.resolve("gen"));
        assertNull(s.load("k").entry());
        s.store("k", entry(7, new byte[] {9, 9}));
        ProgramBinaryStore.Lookup hit = s.load("k");
        assertArrayEquals(new byte[] {9, 9}, hit.entry().binary());
        assertEquals(ProgramBinaryStore.HEADER + 2, hit.bytes());
        assertFalse(hit.damaged());
        Files.write(s.file("bad"), "not a binary".getBytes(StandardCharsets.UTF_8));
        ProgramBinaryStore.Lookup bad = s.load("bad");
        assertNull(bad.entry());
        assertTrue(bad.damaged());
        assertFalse(Files.exists(s.file("bad")), "a file that does not decode is deleted");
        s.delete("k");
        assertNull(s.load("k").entry());
        try (var files = Files.list(tmp.resolve("gen"))) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".tmp")), "no temporary file is left behind");
        }
    }

    @Test
    void theHardCapDeletesTheOldestWrittenOnlyWhenExceeded() throws Exception {
        CacheDir.ensureOwned(tmp);
        ProgramBinaryStore a = new ProgramBinaryStore(tmp.resolve("00000000000000aa"));
        for (String k : List.of("old", "mid", "new")) {
            a.store(k, entry(1, new byte[100]));
        }
        long one = Files.size(a.file("old"));
        Files.setLastModifiedTime(a.file("old"), java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(a.file("mid"), java.nio.file.attribute.FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(a.file("new"), java.nio.file.attribute.FileTime.fromMillis(3_000));
        assertEquals(0, ProgramBinaryStore.enforceCap(tmp, 3 * one));
        assertEquals(2, ProgramBinaryStore.enforceCap(tmp, one));
        assertTrue(Files.exists(a.file("new")));
        assertFalse(Files.exists(a.file("old")));
        assertEquals(0, ProgramBinaryStore.enforceCap(tmp.resolve("missing"), 1));
        assertEquals(new ProgramBinaryStore.Usage(1, one), ProgramBinaryStore.usage(tmp));
        assertEquals(new ProgramBinaryStore.Usage(0, 0), ProgramBinaryStore.usage(tmp.resolve("none")));
        assertEquals(4L << 30, ProgramBinaryStore.HARD_CAP_BYTES);
    }

    @Test
    void verifyingSixteenMegabytesTakesMillisecondsNotSeconds() throws Exception {
        // The size of one dimension's cache on Mesa in the lab. Measured here for the README; asserted loosely.
        ProgramBinaryStore s = new ProgramBinaryStore(tmp.resolve("big"));
        byte[] bin = new byte[160 * 1024];
        new Random(2).nextBytes(bin);
        for (int i = 0; i < 100; i++) {
            s.store("k" + i, entry(1, bin));
        }
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                assertTrue(s.load("k" + i).entry() != null);
            }
            best = Math.min(best, System.nanoTime() - t0);
        }
        System.out.printf(
                "read and verified 100 binaries (%.1f MB) in %.1f ms%n", 100 * bin.length / 1048576.0, best / 1e6);
        assertTrue(best < 2_000_000_000L, "verification took " + best / 1e6 + " ms");
    }

    @Test
    void anEntryThatCannotBeDeletedIsLeftForNextTime() throws Exception {
        ProgramBinaryStore store = new ProgramBinaryStore(tmp.resolve("gen"));
        assertEquals(tmp.resolve("gen"), store.dir());
        Path blocked = Files.createDirectories(store.file("k"));
        Files.writeString(blocked.resolve("inside"), "x");
        store.delete("k");
        assertTrue(Files.exists(blocked));
    }

    @Test
    void aCapOfNothingDeletesEveryFile() throws Exception {
        Path root = tmp.resolve("root");
        CacheDir.ensureOwned(root);
        Path gen = Files.createDirectories(root.resolve("0123456789abcdef"));
        Files.writeString(gen.resolve("a" + ProgramBinaryStore.SUFFIX), "aa");
        Files.writeString(gen.resolve("b" + PatchedSources.SUFFIX), "bb");
        Files.writeString(gen.resolve("c.txt"), "the player's");
        Files.createDirectories(gen.resolve("d" + ProgramBinaryStore.SUFFIX));
        assertEquals(new ProgramBinaryStore.Usage(2, 4), ProgramBinaryStore.usage(root), "binaries and patches only");
        assertEquals(2, ProgramBinaryStore.enforceCap(root, 0));
        assertEquals(new ProgramBinaryStore.Usage(0, 0), ProgramBinaryStore.usage(root));
        assertTrue(Files.exists(gen.resolve("c.txt")));
    }

    @Test
    void aFileGoneBeforeItIsMeasuredIsNotCounted() throws Exception {
        Path root = tmp.resolve("root");
        CacheDir.ensureOwned(root);
        Path gen = Files.createDirectories(root.resolve("0123456789abcdef"));
        Files.writeString(gen.resolve("a" + ProgramBinaryStore.SUFFIX), "aa");
        Files.writeString(gen.resolve("b" + ProgramBinaryStore.SUFFIX), "bbb");
        ProgramBinaryStore.Usage u = ProgramBinaryStore.usage(root, p -> {
            if (p.getFileName().toString().startsWith("a")) {
                throw new java.nio.file.NoSuchFileException(p.toString());
            }
            return Files.size(p);
        });
        assertEquals(new ProgramBinaryStore.Usage(1, 3), u);
    }

    @Test
    void aGenerationThatCannotBeListedStopsTheCountWhereItIs() throws Exception {
        Path root = tmp.resolve("root");
        CacheDir.ensureOwned(root);
        Path gen = Files.createDirectories(root.resolve("0123456789abcdef"));
        Files.writeString(gen.resolve("a" + ProgramBinaryStore.SUFFIX), "aa");
        Unreadable.during(gen, () -> assertEquals(new ProgramBinaryStore.Usage(0, 0), ProgramBinaryStore.usage(root)));
    }
}
