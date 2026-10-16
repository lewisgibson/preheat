package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The checks a cached binary passes before it is used, and a compiled program before it is saved, on a fake driver. */
class BinaryChecksTest {
    private static final byte[] IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];
    private static final byte[] OTHER_IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];

    static {
        OTHER_IFACE[0] = 1;
    }

    /** A driver whose answers the test sets. */
    static final class FakeGl implements ProgramGl {
        int error;
        boolean linked = true;
        byte[] iface = IFACE;
        int length = 4;
        byte[] binary = {9, 8, 7, 6};
        boolean throwOnBinary;
        boolean throwOnDelete;
        final List<String> calls = new ArrayList<>();

        @Override
        public void clearErrors() {
            calls.add("clear");
        }

        @Override
        public int error() {
            return error;
        }

        @Override
        public int createProgram() {
            calls.add("create");
            return 42;
        }

        @Override
        public void programBinary(int program, int format, byte[] b) {
            if (throwOnBinary) {
                throw new IllegalStateException("driver threw");
            }
            calls.add("binary " + format + " " + b.length);
        }

        @Override
        public boolean linked(int program) {
            return linked;
        }

        @Override
        public byte[] interfaceHash(int program) {
            return iface;
        }

        @Override
        public int programInt(int program, int pname) {
            throw new UnsupportedOperationException("the interface hash is answered whole");
        }

        @Override
        public Active activeAttribute(int program, int index) {
            throw new UnsupportedOperationException("the interface hash is answered whole");
        }

        @Override
        public int attributeLocation(int program, String name) {
            throw new UnsupportedOperationException("the interface hash is answered whole");
        }

        @Override
        public Active activeUniform(int program, int index) {
            throw new UnsupportedOperationException("the interface hash is answered whole");
        }

        @Override
        public int binaryLength(int program) {
            return length;
        }

        @Override
        public Binary binary(int program, int len) {
            return new Binary(0x8E21, binary);
        }

        @Override
        public void deleteProgram(int program) {
            calls.add("delete " + program);
            if (throwOnDelete) {
                throw new IllegalStateException("delete threw");
            }
        }
    }

    private static ProgramBinaryStore.Entry entry() {
        return new ProgramBinaryStore.Entry(0x8E21, new byte[] {1, 2, 3}, IFACE);
    }

    @Test
    void aBinaryTheDriverTakesCleanlyIsUsed() {
        FakeGl gl = new FakeGl();
        BinaryChecks.Loaded l = BinaryChecks.load(gl, entry());
        assertEquals(42, l.program());
        assertNull(l.refusal());
        assertEquals(List.of("clear", "create", "binary 36385 3"), gl.calls);
    }

    @Test
    void aGlErrorAnUnlinkedProgramOrAChangedInterfaceIsRefusedAndTheProgramDeleted() {
        FakeGl err = new FakeGl();
        err.error = 0x500;
        BinaryChecks.Loaded l = BinaryChecks.load(err, entry());
        assertEquals(0, l.program());
        assertEquals("GL error 0x500", l.refusal());
        assertTrue(err.calls.contains("delete 42"));

        FakeGl unlinked = new FakeGl();
        unlinked.linked = false;
        assertEquals("not linked", BinaryChecks.load(unlinked, entry()).refusal());
        assertTrue(unlinked.calls.contains("delete 42"));

        FakeGl changed = new FakeGl();
        changed.iface = OTHER_IFACE;
        String why = BinaryChecks.load(changed, entry()).refusal();
        assertNotNull(why);
        assertTrue(why.contains("attributes or uniforms"));
        assertTrue(changed.calls.contains("delete 42"));
    }

    @Test
    void aDriverThatThrowsHasItsProgramDeletedAndTheErrorPassedOn() {
        FakeGl gl = new FakeGl();
        gl.throwOnBinary = true;
        assertThrows(IllegalStateException.class, () -> BinaryChecks.load(gl, entry()));
        assertTrue(gl.calls.contains("delete 42"));
        gl.throwOnDelete = true;
        assertThrows(IllegalStateException.class, () -> BinaryChecks.load(gl, entry()), "a failed delete is swallowed");
    }

    @Test
    void aLinkedProgramWithABinaryIsCapturedWithItsInterface() {
        FakeGl gl = new FakeGl();
        BinaryChecks.Captured c = BinaryChecks.capture(gl, 7);
        assertEquals(BinaryChecks.Outcome.SAVED, c.outcome());
        assertNotNull(c.entry());
        assertArrayEquals(new byte[] {9, 8, 7, 6}, c.entry().binary());
        assertEquals(0x8E21, c.entry().format());
        assertTrue(c.entry().interfaceMatches(IFACE));
    }

    @Test
    void nothingIsCapturedWithoutALinkOrABinary() {
        FakeGl unlinked = new FakeGl();
        unlinked.linked = false;
        assertEquals(
                BinaryChecks.Outcome.NOT_LINKED,
                BinaryChecks.capture(unlinked, 7).outcome());
        FakeGl noLength = new FakeGl();
        noLength.length = 0;
        assertEquals(
                BinaryChecks.Outcome.NO_BINARY,
                BinaryChecks.capture(noLength, 7).outcome());
        FakeGl empty = new FakeGl();
        empty.binary = new byte[0];
        BinaryChecks.Captured c = BinaryChecks.capture(empty, 7);
        assertEquals(BinaryChecks.Outcome.NO_BINARY, c.outcome());
        assertNull(c.entry());
    }

    @Test
    void discardNeverThrows() {
        FakeGl gl = new FakeGl();
        gl.throwOnDelete = true;
        BinaryChecks.discard(gl, 5);
        assertEquals(List.of("delete 5"), gl.calls);
    }
}
