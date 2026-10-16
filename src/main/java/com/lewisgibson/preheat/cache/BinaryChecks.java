package com.lewisgibson.preheat.cache;

import org.jspecify.annotations.Nullable;

/**
 * What decides whether a cached binary is used and whether a compiled program is saved, over {@link ProgramGl} so it
 * is tested without a driver. A loaded binary is used only if the driver took it without a GL error, reports the
 * program linked, and the program has the interface the compiled one had; anything else is refused and the program
 * deleted. A compiled program is saved only if it linked and the driver gives a binary for it.
 */
public final class BinaryChecks {
    private BinaryChecks() {}

    /** A loaded program, or 0 with why the driver's program was refused. */
    public record Loaded(int program, @Nullable String refusal) {}

    /** Creates a program from {@code e}; the program is deleted again if anything is wrong with it, or anything throws. */
    public static Loaded load(ProgramGl gl, ProgramBinaryStore.Entry e) {
        gl.clearErrors();
        int program = gl.createProgram();
        String why;
        try {
            gl.programBinary(program, e.format(), e.binary());
            why = refusal(gl, program, e);
        } catch (RuntimeException | LinkageError t) {
            discard(gl, program);
            throw t;
        }
        if (why != null) {
            discard(gl, program);
            return new Loaded(0, why);
        }
        return new Loaded(program, null);
    }

    private static @Nullable String refusal(ProgramGl gl, int program, ProgramBinaryStore.Entry e) {
        int error = gl.error();
        if (error != 0) {
            return "GL error 0x" + Integer.toHexString(error);
        }
        if (!gl.linked(program)) {
            return "not linked";
        }
        if (!e.interfaceMatches(gl.interfaceHash(program))) {
            return "its attributes or uniforms differ from the compiled program";
        }
        return null;
    }

    /** What reading a compiled program's binary gave. */
    public enum Outcome {
        SAVED,
        /** It did not link; Iris falls back for it, and there is nothing to keep. */
        NOT_LINKED,
        /** The driver gave no binary. */
        NO_BINARY
    }

    /** A compiled program's entry to save, or null with why not. */
    public record Captured(ProgramBinaryStore.@Nullable Entry entry, Outcome outcome) {}

    public static Captured capture(ProgramGl gl, int program) {
        if (!gl.linked(program)) {
            return new Captured(null, Outcome.NOT_LINKED);
        }
        int length = gl.binaryLength(program);
        if (length <= 0) {
            return new Captured(null, Outcome.NO_BINARY);
        }
        ProgramGl.Binary b = gl.binary(program, length);
        if (b.bytes().length == 0) {
            return new Captured(null, Outcome.NO_BINARY);
        }
        return new Captured(
                new ProgramBinaryStore.Entry(b.format(), b.bytes(), gl.interfaceHash(program)), Outcome.SAVED);
    }

    /** Deletes a program that will not be used; a failure here leaves it for the driver to free with the context. */
    public static void discard(ProgramGl gl, int program) {
        try {
            gl.deleteProgram(program);
        } catch (RuntimeException | LinkageError ignored) {
            // Nothing more can be done.
        }
    }
}
