package com.lewisgibson.preheat.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

/** A driver for the cache: its strings and binary formats, and programs that link, load and give binaries as set. */
final class FakeDriver implements ProgramGl, DriverGl {
    static final byte[] IFACE = new byte[ProgramBinaryStore.INTERFACE_HASH_BYTES];

    boolean supported = true;
    int[] formats = {0x8E21};
    Map<Integer, String> strings = Map.of(
            GL11C.GL_VENDOR, "NVIDIA Corporation",
            GL11C.GL_RENDERER, "NVIDIA GeForce RTX 5090/PCIe/SSE2",
            GL11C.GL_VERSION, "4.6.0 NVIDIA 595.12",
            GL20C.GL_SHADING_LANGUAGE_VERSION, "4.60 NVIDIA");
    final Set<Integer> programs = new HashSet<>();
    int error;
    boolean linked = true;
    byte[] binary = {9, 8, 7, 6};
    int format = 0x8E21;
    int next = 100;
    final List<String> calls = new ArrayList<>();

    @Override
    public boolean programBinarySupported() {
        return supported;
    }

    @Override
    public int integer(int pname) {
        return formats.length;
    }

    @Override
    public void integers(int pname, int[] into) {
        System.arraycopy(formats, 0, into, 0, into.length);
    }

    @Override
    public @Nullable String string(int name) {
        return strings.get(name);
    }

    @Override
    public boolean isProgram(int program) {
        return programs.contains(program);
    }

    @Override
    public void programParameter(int program, int pname, int value) {
        calls.add("retrievable " + program);
    }

    @Override
    public void clearErrors() {}

    @Override
    public int error() {
        return error;
    }

    @Override
    public int createProgram() {
        int p = next++;
        programs.add(p);
        return p;
    }

    @Override
    public void programBinary(int program, int format, byte[] binary) {
        calls.add("binary " + program);
    }

    @Override
    public boolean linked(int program) {
        return linked;
    }

    @Override
    public byte[] interfaceHash(int program) {
        return IFACE.clone();
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
        return binary.length;
    }

    @Override
    public Binary binary(int program, int length) {
        return new Binary(format, binary);
    }

    @Override
    public void deleteProgram(int program) {
        programs.remove(program);
        calls.add("delete " + program);
    }
}
