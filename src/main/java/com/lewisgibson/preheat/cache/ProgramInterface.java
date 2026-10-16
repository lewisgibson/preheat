package com.lewisgibson.preheat.cache;

import com.lewisgibson.preheat.Sha256;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;

/**
 * A hash of what a linked program exposes: its active attributes (name, type, size, location), its active uniforms
 * (name, type, size) and its uniform block count. Taken when a program is compiled and stored with its binary; taken
 * again after the driver loads that binary. A driver that reports a successful link for a binary it did not really
 * restore (the case a link status alone cannot catch) shows up as a different interface, and the program is compiled
 * instead. Uniform locations are left out: Iris asks for every location after the program is created.
 */
public final class ProgramInterface {
    private ProgramInterface() {}

    /** The interface hash of a linked program; render thread only. */
    public static byte[] hash(ProgramGl gl, int program) {
        List<String> lines = new ArrayList<>();
        int attributes = gl.programInt(program, GL20C.GL_ACTIVE_ATTRIBUTES);
        for (int i = 0; i < attributes; i++) {
            ProgramGl.Active a = gl.activeAttribute(program, i);
            lines.add(
                    "a " + a.name() + " " + a.type() + " " + a.size() + " " + gl.attributeLocation(program, a.name()));
        }
        int uniforms = gl.programInt(program, GL20C.GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < uniforms; i++) {
            ProgramGl.Active u = gl.activeUniform(program, i);
            lines.add("u " + u.name() + " " + u.type() + " " + u.size());
        }
        lines.add("b " + gl.programInt(program, GL31C.GL_ACTIVE_UNIFORM_BLOCKS));
        return of(lines);
    }

    /** The hash of interface lines, independent of the order the driver lists them in. */
    static byte[] of(List<String> lines) {
        List<String> sorted = new ArrayList<>(lines);
        Collections.sort(sorted);
        return Sha256.ofParts(sorted);
    }
}
