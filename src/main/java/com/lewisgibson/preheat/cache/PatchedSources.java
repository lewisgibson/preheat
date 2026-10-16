package com.lewisgibson.preheat.cache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32C;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

/**
 * Iris's patched shader sources on disk: what {@code TransformPatcher} made of one program's stages, saved so the next
 * launch can skip patching (the largest part of a build once the shaders themselves come from the cache). One file per
 * patch, {@code <key>.src} beside the binaries in the same generation folder, named by a hash of everything Iris's own
 * in-memory patch cache compares and more ({@link PatchKey}).
 *
 * <p>The file holds the stages as name and text pairs (a stage Iris left out is stored as absent), deflated, with the
 * length and a CRC-32C of the uncompressed bytes checked before anything is used. Files are written to a temporary name
 * and renamed into place. No game classes here.
 */
public final class PatchedSources {
    static final byte[] MAGIC = "PHTSRC01".getBytes(StandardCharsets.US_ASCII);
    /** Magic, uncompressed length, CRC-32C of the uncompressed bytes. */
    static final int HEADER = MAGIC.length + 4 + 8;

    public static final String SUFFIX = ".src";

    /** No patched program is anywhere near this; a length above it is damage, not a shader. */
    private static final int MAX_LENGTH = 64 << 20;

    private PatchedSources() {}

    /** The file's bytes for a stage map (stage name to source, or null for a stage Iris left out). */
    public static byte[] encode(Map<String, @Nullable String> stages) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writeInt(raw, stages.size());
        for (Map.Entry<String, @Nullable String> e : stages.entrySet()) {
            writeString(raw, e.getKey());
            String v = e.getValue();
            if (v == null) {
                writeInt(raw, -1);
            } else {
                writeString(raw, v);
            }
        }
        byte[] plain = raw.toByteArray();
        CRC32C crc = new CRC32C();
        crc.update(plain);
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        ByteArrayOutputStream out = new ByteArrayOutputStream(plain.length / 4 + HEADER);
        try {
            d.setInput(plain);
            d.finish();
            byte[] buf = new byte[64 * 1024];
            while (!d.finished()) {
                out.write(buf, 0, d.deflate(buf));
            }
        } finally {
            d.end();
        }
        ByteBuffer head = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
        head.put(MAGIC).putInt(plain.length).putLong(crc.getValue());
        byte[] body = out.toByteArray();
        byte[] file = Arrays.copyOf(head.array(), HEADER + body.length);
        System.arraycopy(body, 0, file, HEADER, body.length);
        return file;
    }

    /** The stage map, or null for anything truncated, damaged, of another format version, or not ours. */
    public static @Nullable Map<String, @Nullable String> decode(byte[] file) {
        if (file.length < HEADER) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        b.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            return null;
        }
        int length = b.getInt();
        long crc = b.getLong();
        if (length < 0 || length > MAX_LENGTH) {
            return null;
        }
        byte[] plain = new byte[length];
        Inflater inf = new Inflater();
        try {
            inf.setInput(file, HEADER, file.length - HEADER);
            int n = 0;
            while (n < length) {
                int got = inf.inflate(plain, n, length - n);
                // Nothing more came out: the stream ended early, was cut short, or wants a dictionary.
                if (got == 0) {
                    return null;
                }
                n += got;
            }
            // The stream must end exactly here, its own checksum read and nothing after it.
            if (!inf.finished() && inf.inflate(new byte[1]) != 0) {
                return null;
            }
            if (!inf.finished() || inf.getRemaining() != 0) {
                return null;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
        CRC32C c = new CRC32C();
        c.update(plain);
        if (c.getValue() != crc) {
            return null;
        }
        try {
            ByteBuffer p = ByteBuffer.wrap(plain).order(ByteOrder.LITTLE_ENDIAN);
            int count = p.getInt();
            if (count < 0 || count > 64) {
                return null;
            }
            Map<String, @Nullable String> out = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String k = readString(p);
                int len = p.getInt();
                out.put(k, len < 0 ? null : readString(p, len));
            }
            return p.hasRemaining() ? null : out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The saved stages for {@code key} in generation folder {@code dir}; a file that fails its checks is deleted. */
    public static @Nullable Map<String, @Nullable String> load(Path dir, String key) {
        Path f = dir.resolve(key + SUFFIX);
        try {
            byte[] bytes = Files.readAllBytes(f);
            Map<String, @Nullable String> m = decode(bytes);
            if (m == null) {
                Files.deleteIfExists(f);
            }
            return m;
        } catch (IOException e) {
            // Missing, or unreadable right now: patched again, and tried again next time.
            return null;
        }
    }

    /** Writes through a temporary file and an atomic rename. */
    public static void store(Path dir, String key, byte[] encoded) throws IOException {
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, key, ".tmp");
        try {
            try (FileChannel ch =
                    FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.wrap(encoded);
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
            }
            Files.move(
                    tmp,
                    dir.resolve(key + SUFFIX),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Whether two stage maps hold the same stages with the same sources once Iris's numbered names are renumbered
     * ({@link StableSource}): Iris numbers them in hash order, so two correct patches of one program can differ there.
     */
    public static boolean sameStages(Map<String, @Nullable String> a, Map<String, @Nullable String> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<String, @Nullable String> e : a.entrySet()) {
            String x = e.getValue();
            String y = b.get(e.getKey());
            boolean same = (x == null || y == null)
                    ? Objects.equals(x, y)
                    : StableSource.of(x).equals(StableSource.of(y));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.writeBytes(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array());
    }

    private static void writeString(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        writeInt(out, b.length);
        out.writeBytes(b);
    }

    private static String readString(ByteBuffer p) {
        return readString(p, p.getInt());
    }

    private static String readString(ByteBuffer p, int len) {
        if (len < 0 || len > p.remaining()) {
            throw new IllegalArgumentException("bad length");
        }
        byte[] b = new byte[len];
        p.get(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
