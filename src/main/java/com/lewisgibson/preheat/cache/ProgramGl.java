package com.lewisgibson.preheat.cache;

/**
 * The OpenGL calls the cache makes on a program, as an interface so the checks around them ({@link BinaryChecks}) can
 * be tested without a driver. {@link LwjglProgramGl} is the real one.
 */
public interface ProgramGl {
    /** A binary read back from the driver: its format enum and bytes (handed straight on, never compared). */
    @SuppressWarnings("ArrayRecordComponent")
    record Binary(int format, byte[] bytes) {}

    void clearErrors();

    int error();

    int createProgram();

    void programBinary(int program, int format, byte[] binary);

    boolean linked(int program);

    /** The program's interface hash ({@link ProgramInterface#hash}), from the queries below. */
    default byte[] interfaceHash(int program) {
        return ProgramInterface.hash(this, program);
    }

    /** An active attribute or uniform as the driver lists it: its name, type enum and array size. */
    record Active(String name, int type, int size) {}

    /** A program parameter ({@code glGetProgrami}), read straight from the driver. */
    int programInt(int program, int pname);

    Active activeAttribute(int program, int index);

    int attributeLocation(int program, String name);

    Active activeUniform(int program, int index);

    /** The length the driver reports for the program's binary, 0 or less when it has none. */
    int binaryLength(int program);

    /** The binary, read into a buffer of {@code length}; its length is 0 when the driver gave none. */
    Binary binary(int program, int length);

    void deleteProgram(int program);
}
