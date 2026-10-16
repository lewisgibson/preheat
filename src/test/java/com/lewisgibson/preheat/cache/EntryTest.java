package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntryTest {
    private static final byte[] IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];

    @Test
    void equalByContentAndDefensivelyCopied() {
        byte[] binary = {1, 2, 3};
        ProgramBinaryStore.Entry a = new ProgramBinaryStore.Entry(0x8E21, binary, IFACE);
        binary[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, a.binary(), "the caller's array is copied in");
        a.binary()[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, a.binary(), "and copied out");
        assertEquals(3, a.length());
        ProgramBinaryStore.Entry b = new ProgramBinaryStore.Entry(0x8E21, new byte[] {1, 2, 3}, IFACE.clone());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ProgramBinaryStore.Entry(0x8E22, new byte[] {1, 2, 3}, IFACE));
        assertNotEquals(a, new ProgramBinaryStore.Entry(0x8E21, new byte[] {1, 2, 4}, IFACE));
        byte[] other = IFACE.clone();
        other[0] = 1;
        assertNotEquals(a, new ProgramBinaryStore.Entry(0x8E21, new byte[] {1, 2, 3}, other));
        assertNotEquals(a, (Object) "not an entry");
        assertTrue(a.interfaceMatches(IFACE.clone()));
        assertFalse(a.interfaceMatches(other));
        assertArrayEquals(IFACE, a.interfaceHash());
        assertEquals("Entry[format=0x8e21, 3 bytes]", a.toString());
    }
}
