package com.lewisgibson.preheat.cache;

import org.jspecify.annotations.Nullable;

/**
 * What the cache asks the driver once per launch and around each build, as an interface so {@link BinaryCache} is
 * tested without one. {@link LwjglProgramGl} is the real one.
 */
public interface DriverGl {
    /** Whether the context has program binaries at all (OpenGL 4.1, or ARB_get_program_binary). */
    boolean programBinarySupported();

    int integer(int pname);

    /** Fills {@code into} with the values of an array parameter. */
    void integers(int pname, int[] into);

    /** A driver string ({@code glGetString}), which a broken driver may leave out. */
    @Nullable
    String string(int name);

    /** Whether {@code program} names a program now ({@code glIsProgram}). */
    boolean isProgram(int program);

    void programParameter(int program, int pname, int value);
}
