package com.lewisgibson.preheat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/** SHA-256, which every Java runtime has: the cache keys, the program interface hash and the Iris fingerprints. */
public final class Sha256 {
    private Sha256() {}

    /** A new SHA-256 digest. */
    public static MessageDigest digest() {
        return digest("SHA-256");
    }

    /** The named digest; a runtime without it is broken, so that is an error rather than a checked exception. */
    static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The hash of {@code bytes}. */
    public static byte[] of(byte[] bytes) {
        return digest().digest(bytes);
    }

    /** The hash of every part in order, each length-prefixed so no two lists of parts collide. */
    public static byte[] ofParts(List<String> parts) {
        MessageDigest md = digest();
        for (String part : parts) {
            byte[] b = part.getBytes(StandardCharsets.UTF_8);
            md.update(ByteBuffer.allocate(4).putInt(b.length).array());
            md.update(b);
        }
        return md.digest();
    }
}
