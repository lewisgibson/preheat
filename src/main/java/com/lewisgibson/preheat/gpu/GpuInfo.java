package com.lewisgibson.preheat.gpu;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The GPU and driver as OpenGL names them (GL_VENDOR, GL_RENDERER, GL_VERSION, GL_SHADING_LANGUAGE_VERSION), and what
 * those strings say about who made the hardware and which driver runs it. The strings are part of every cache key,
 * so a driver update always means fresh binaries.
 */
public record GpuInfo(String vendor, String renderer, String version, String glsl) {
    /** Who made the GPU. */
    public enum Hardware {
        NVIDIA,
        AMD,
        INTEL,
        APPLE,
        SOFTWARE,
        OTHER
    }

    /** Which driver runs it: the vendor's own, Mesa (Linux, and Mesa's D3D12 layer on Windows), or Apple's. */
    public enum Driver {
        NVIDIA,
        AMD,
        INTEL,
        MESA,
        APPLE,
        OTHER
    }

    /** From the driver's strings, any of which a broken driver may leave out. */
    public static GpuInfo of(
            @Nullable String vendor, @Nullable String renderer, @Nullable String version, @Nullable String glsl) {
        return new GpuInfo(
                Objects.requireNonNullElse(vendor, ""),
                Objects.requireNonNullElse(renderer, ""),
                Objects.requireNonNullElse(version, ""),
                Objects.requireNonNullElse(glsl, ""));
    }

    public Hardware hardware() {
        String v = vendor.toLowerCase(Locale.ROOT);
        String r = renderer.toLowerCase(Locale.ROOT);
        if (r.contains("llvmpipe")
                || r.contains("softpipe")
                || r.contains("swiftshader")
                || r.contains("software rasterizer")) {
            return Hardware.SOFTWARE;
        }
        if (v.contains("nvidia")
                || r.contains("geforce")
                || r.contains("quadro")
                || r.contains("nouveau")
                || r.startsWith("nv")
                || r.contains("nvidia")) {
            return Hardware.NVIDIA;
        }
        if (v.contains("ati technologies")
                || v.contains("amd")
                || v.contains("advanced micro devices")
                || r.contains("radeon")
                || r.contains("amd ")) {
            return Hardware.AMD;
        }
        if (v.contains("intel") || r.contains("intel")) {
            return Hardware.INTEL;
        }
        if (v.contains("apple") || r.startsWith("apple")) {
            return Hardware.APPLE;
        }
        return Hardware.OTHER;
    }

    public Driver driver() {
        if (version.contains("Mesa") || renderer.contains("Mesa") || renderer.startsWith("D3D12")) {
            return Driver.MESA;
        }
        return switch (hardware()) {
            case NVIDIA -> Driver.NVIDIA;
            case AMD -> Driver.AMD;
            case INTEL -> Driver.INTEL;
            case APPLE -> Driver.APPLE;
            default -> Driver.OTHER;
        };
    }

    /**
     * The driver's own version, as far as GL_VERSION carries it: "NVIDIA 595.12", "Mesa 25.0.1", AMD's "24.10.1.240924",
     * Intel's "Build 31.0.101.5592". Everything after the GL version number, or the whole string when there is nothing.
     */
    public String driverVersion() {
        String s = version.trim();
        int i = 0;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
            i++;
        }
        String rest = s.substring(i).trim();
        if (rest.startsWith("-")) {
            rest = rest.substring(1).trim();
        }
        for (String noise : new String[] {
            "Compatibility Profile Context", "Core Profile Context", "(Compatibility Profile)", "(Core Profile)"
        }) {
            rest = rest.replace(noise, "").trim();
        }
        return rest.isEmpty() ? s : rest;
    }

    /** One line for the log and a bug report. */
    public String describe() {
        return renderer + " (" + vendor + "), driver " + driverVersion() + ", OpenGL " + version + ", GLSL " + glsl;
    }
}
