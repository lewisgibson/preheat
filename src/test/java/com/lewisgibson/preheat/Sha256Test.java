package com.lewisgibson.preheat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class Sha256Test {
    @Test
    void hashesBytesAsSha256() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                HexFormat.of().formatHex(Sha256.of("abc".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void partsAreLengthPrefixedSoTheyCannotRunTogether() {
        assertNotEquals(
                HexFormat.of().formatHex(Sha256.ofParts(List.of("ab", "c"))),
                HexFormat.of().formatHex(Sha256.ofParts(List.of("a", "bc"))));
        assertEquals(
                HexFormat.of().formatHex(Sha256.ofParts(List.of("a", "bc"))),
                HexFormat.of().formatHex(Sha256.ofParts(List.of("a", "bc"))));
    }

    @Test
    void aMissingAlgorithmIsAnError() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Sha256.digest("NO-SUCH-HASH"));
        assertInstanceOf(NoSuchAlgorithmException.class, e.getCause());
    }
}
