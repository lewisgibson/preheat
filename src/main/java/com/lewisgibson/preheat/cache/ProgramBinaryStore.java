package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.Preheat;
import com.lewisgibson.preheat.Sha256;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32C;
import org.jspecify.annotations.Nullable;

/**
 * Linked GL program binaries on disk: one directory per generation, one file per program, named by a SHA-256 of
 * everything that decides the binary (the GPU, driver and GLSL version strings, the Iris and Preheat versions, the kind
 * of program, its attribute bindings and the final source of every stage, which already carries the shader pack, its
 * settings and the dimension). Any change to any of them is another name, so a stale binary is never looked up.
 *
 * <p>Each file carries the binary's length and a CRC-32C, checked before the binary is handed to the driver: some
 * drivers crash rather than refuse a damaged binary, and a write torn by a power cut must never reach one. Files are
 * written to a temporary name, flushed to disk and renamed into place, so a reader sees a whole file or none.
 *
 * <p>No GL here; {@link ProgramBinaries} does that.
 */
public final class ProgramBinaryStore {
    static final byte[] MAGIC = "PHTPB002".getBytes(StandardCharsets.US_ASCII);
    public static final int INTERFACE_HASH_BYTES = 32;
    /** Magic, format, length, CRC-32C, interface hash. */
    static final int HEADER = MAGIC.length + 4 + 4 + 8 + INTERFACE_HASH_BYTES;

    static final String SUFFIX = ".bin";

    private final Path dir;

    public ProgramBinaryStore(Path dir) {
        this.dir = dir;
    }

    /** The key: hex SHA-256 over every part, each length-prefixed so no two lists of parts collide. */
    public static String key(List<String> parts) {
        return HexFormat.of().formatHex(Sha256.ofParts(parts));
    }

    /**
     * A binary, the driver's format enum for it, and the hash of the linked program's interface (its active attributes
     * and uniforms) taken when it was compiled, checked again after the driver loads it.
     */
    public static final class Entry {
        private final int format;
        private final byte[] binary;
        private final byte[] interfaceHash;

        public Entry(int format, byte[] binary, byte[] interfaceHash) {
            if (interfaceHash.length != INTERFACE_HASH_BYTES) {
                throw new IllegalArgumentException("interface hash must be " + INTERFACE_HASH_BYTES + " bytes");
            }
            this.format = format;
            this.binary = binary.clone();
            this.interfaceHash = interfaceHash.clone();
        }

        public int format() {
            return format;
        }

        /** A copy of the binary. */
        public byte[] binary() {
            return binary.clone();
        }

        public int length() {
            return binary.length;
        }

        /** A copy of the interface hash. */
        public byte[] interfaceHash() {
            return interfaceHash.clone();
        }

        /** Whether {@code hash} is this entry's interface hash. */
        public boolean interfaceMatches(byte[] hash) {
            return Arrays.equals(interfaceHash, hash);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Entry e
                    && format == e.format
                    && Arrays.equals(binary, e.binary)
                    && Arrays.equals(interfaceHash, e.interfaceHash);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Integer.hashCode(format) + Arrays.hashCode(binary)) + Arrays.hashCode(interfaceHash);
        }

        @Override
        public String toString() {
            return "Entry[format=0x" + Integer.toHexString(format) + ", " + binary.length + " bytes]";
        }
    }

    /** The file's bytes: magic, format, length, CRC-32C of the binary, interface hash, the binary. */
    public static byte[] encode(Entry e) {
        CRC32C crc = new CRC32C();
        crc.update(e.binary);
        ByteBuffer b = ByteBuffer.allocate(HEADER + e.binary.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(MAGIC)
                .putInt(e.format)
                .putInt(e.binary.length)
                .putLong(crc.getValue())
                .put(e.interfaceHash)
                .put(e.binary);
        return b.array();
    }

    /** The entry, or null for anything truncated, damaged, of another format version, or not ours. */
    public static @Nullable Entry decode(byte[] file) {
        if (file.length < HEADER) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        b.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            return null;
        }
        int format = b.getInt();
        int length = b.getInt();
        long crc = b.getLong();
        if (length <= 0 || length != file.length - HEADER) {
            return null;
        }
        byte[] iface = new byte[INTERFACE_HASH_BYTES];
        b.get(iface);
        CRC32C c = new CRC32C();
        c.update(file, HEADER, length);
        if (c.getValue() != crc) {
            return null;
        }
        return new Entry(format, Arrays.copyOfRange(file, HEADER, file.length), iface);
    }

    /** The generation folder this store reads and writes. */
    public Path dir() {
        return dir;
    }

    Path file(String key) {
        return dir.resolve(key + SUFFIX);
    }

    /** What a lookup found: the entry (or null), whether a file was there but failed its checks, and the bytes read. */
    public record Lookup(@Nullable Entry entry, boolean damaged, long bytes) {}

    /** The stored entry; a file that fails its checks is deleted. */
    public Lookup load(String key) {
        Path f = file(key);
        if (!Files.isRegularFile(f)) {
            return new Lookup(null, false, 0);
        }
        try {
            byte[] bytes = Files.readAllBytes(f);
            Entry e = decode(bytes);
            if (e == null) {
                Files.deleteIfExists(f);
                return new Lookup(null, true, bytes.length);
            }
            return new Lookup(e, false, bytes.length);
        } catch (IOException ex) {
            // Unreadable right now (locked by a virus scanner, a drive gone): compiled instead, and tried again next
            // time.
            Preheat.LOG.debug("PREHEAT Could not read {}", f, ex);
            return new Lookup(null, false, 0);
        }
    }

    /**
     * Writes through a temporary file and an atomic rename. Nothing is forced to disk: a file torn by a power cut fails
     * its checksum and is compiled again, so an fsync per program would only slow saving down.
     */
    public void store(String key, Entry e) throws IOException {
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, key, ".tmp");
        try {
            try (FileChannel ch =
                    FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buf = ByteBuffer.wrap(encode(e));
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
            }
            Files.move(tmp, file(key), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Marks a binary as just used, so the size limit removes it last. */
    static void touch(Path file) {
        try {
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException ignored) {
            // Then it is only as recent as when it was written.
        }
    }

    /** Forgets one entry (a binary the driver refused). */
    public void delete(String key) {
        try {
            Files.deleteIfExists(file(key));
        } catch (IOException ignored) {
            // A file that cannot be deleted is refused again next time and deleted then.
        }
    }

    /** The most the cache may ever hold. Not a setting: a real pack's whole cache is tens of MB, so this is only a guard. */
    public static final long HARD_CAP_BYTES = 4L << 30;

    /**
     * The fail-safe: if the binaries under {@code root} ever add up to more than {@code maxBytes}, those used longest ago are
     * deleted until they do not. Returns the number deleted.
     */
    public static int enforceCap(Path root, long maxBytes) throws IOException {
        record F(Path path, long written, long size) {}
        List<F> files = new ArrayList<>();
        for (Path p : binaries(root)) {
            files.add(new F(
                    p, Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toMillis(), Files.size(p)));
        }
        long total = files.stream().mapToLong(F::size).sum();
        if (total <= maxBytes) {
            return 0;
        }
        files.sort(Comparator.comparingLong(F::written));
        int deleted = 0;
        for (F f : files) {
            if (total <= maxBytes) {
                break;
            }
            Files.deleteIfExists(f.path());
            total -= f.size();
            deleted++;
        }
        return deleted;
    }

    /** Files and bytes of binaries under {@code root}. */
    public record Usage(long files, long bytes) {}

    public static Usage usage(Path root) {
        return usage(root, Files::size);
    }

    /** How a file's size is read: {@link Files#size}, which throws for a file gone since it was listed. */
    interface Size {
        long of(Path file) throws IOException;
    }

    static Usage usage(Path root, Size size) {
        long files = 0;
        long bytes = 0;
        try {
            for (Path p : binaries(root)) {
                try {
                    bytes += size.of(p);
                    files++;
                } catch (IOException ignored) {
                    // Gone between listing and reading: not counted.
                }
            }
        } catch (IOException e) {
            return new Usage(files, bytes);
        }
        return new Usage(files, bytes);
    }

    /** Whether {@code p} is one of the cache's own files: a real file (not a link) named as a binary or a patch. */
    static boolean isCacheFile(Path p) {
        String name = p.getFileName().toString();
        return (name.endsWith(SUFFIX) || name.endsWith(PatchedSources.SUFFIX))
                && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * The cache files (binaries and patches) in an owned root's generation folders (real files only, links never
     * followed).
     */
    static List<Path> binaries(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path g : CacheDir.generations(root)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(g)) {
                for (Path p : ds) {
                    if (isCacheFile(p)) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }
}
