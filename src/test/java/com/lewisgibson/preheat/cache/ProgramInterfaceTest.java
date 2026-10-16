package com.lewisgibson.preheat.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;

/** The interface hash is built from what the driver lists for a program, in whatever order it lists it. */
class ProgramInterfaceTest {
    /** A driver listing a program's attributes, uniforms and uniform blocks. */
    record Listing(List<ProgramGl.Active> attributes, List<ProgramGl.Active> uniforms, int blocks)
            implements ProgramGl {
        @Override
        public int programInt(int program, int pname) {
            return Map.of(
                            GL20C.GL_ACTIVE_ATTRIBUTES, attributes.size(),
                            GL20C.GL_ACTIVE_UNIFORMS, uniforms.size(),
                            GL31C.GL_ACTIVE_UNIFORM_BLOCKS, blocks)
                    .get(pname);
        }

        @Override
        public Active activeAttribute(int program, int index) {
            return attributes.get(index);
        }

        @Override
        public int attributeLocation(int program, String name) {
            return name.equals("Position") ? 0 : 1;
        }

        @Override
        public Active activeUniform(int program, int index) {
            return uniforms.get(index);
        }

        @Override
        public void clearErrors() {}

        @Override
        public int error() {
            return 0;
        }

        @Override
        public int createProgram() {
            return 0;
        }

        @Override
        public void programBinary(int program, int format, byte[] binary) {}

        @Override
        public boolean linked(int program) {
            return true;
        }

        @Override
        public int binaryLength(int program) {
            return 0;
        }

        @Override
        public Binary binary(int program, int length) {
            return new Binary(0, new byte[0]);
        }

        @Override
        public void deleteProgram(int program) {}
    }

    private static final ProgramGl.Active POSITION = new ProgramGl.Active("Position", 35665, 1);
    private static final ProgramGl.Active UV = new ProgramGl.Active("UV0", 35664, 1);
    private static final ProgramGl.Active SAMPLER = new ProgramGl.Active("gtexture", 35678, 1);

    @Test
    void theHashIsOfEveryAttributeUniformAndBlockCount() {
        Listing gl = new Listing(List.of(POSITION, UV), List.of(SAMPLER), 1);
        assertArrayEquals(
                ProgramInterface.of(List.of("a Position 35665 1 0", "a UV0 35664 1 1", "u gtexture 35678 1", "b 1")),
                gl.interfaceHash(7));
        assertArrayEquals(
                gl.interfaceHash(7),
                new Listing(List.of(UV, POSITION), List.of(SAMPLER), 1).interfaceHash(7),
                "whatever order the driver lists them in");
    }
}
