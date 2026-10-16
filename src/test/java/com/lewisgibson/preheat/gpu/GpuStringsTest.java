package com.lewisgibson.preheat.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every way a driver names its maker, and every driver the cache policy knows. */
class GpuStringsTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Mesa|softpipe|SOFTWARE",
                "Google Inc.|SwiftShader Device|SOFTWARE",
                "Microsoft|GDI Generic Software Rasterizer|SOFTWARE",
                "Some Vendor|GeForce GTX 1080|NVIDIA",
                "Some Vendor|Quadro RTX 4000|NVIDIA",
                "Mesa|nouveau NV50|NVIDIA",
                "Mesa|NVIDIA (via something)|NVIDIA",
                "Advanced Micro Devices, Inc.|gfx1030|AMD",
                "Some Vendor|AMD Ryzen 7 graphics|AMD",
                "X.Org|Radeon RX 7900|AMD",
                "Some Vendor|Mesa Intel(R) UHD|INTEL",
                "Apple Inc.|Apple M1|APPLE",
                "Some Vendor|apple m3|APPLE",
                "Imagination|PowerVR|OTHER"
            })
    void hardwareFromTheStrings(String vendor, String renderer, GpuInfo.Hardware expected) {
        assertEquals(expected, new GpuInfo(vendor, renderer, "4.6", "4.60").hardware());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "NVIDIA Corporation|RTX|4.6.0 NVIDIA 595.12|NVIDIA|true|tested",
                "Mesa|llvmpipe|4.5 Mesa 25|MESA|true|Mesa",
                "ATI Technologies Inc.|Radeon|4.6.0 24.10|AMD|true|not yet tested",
                "Intel|Intel(R) UHD|4.6.0 - Build 31|INTEL|true|not yet tested",
                "Apple|Apple M2|4.1 Metal - 88.1|APPLE|true|not yet tested",
                "Imagination|PowerVR|3.2 build 1|OTHER|true|unrecognised",
                "Mesa|Mesa DRI|4.6|MESA|true|Mesa"
            })
    void everyDriverIsAllowedWithFormatsAndNamedHonestly(
            String vendor, String renderer, String version, GpuInfo.Driver driver, boolean allowed, String note) {
        GpuInfo g = new GpuInfo(vendor, renderer, version, "4.60");
        assertEquals(driver, g.driver());
        CachePolicy p = CachePolicy.decide(g, 2);
        assertEquals(allowed, p.allowed());
        assertTrue(p.note().contains(note), p.note());
        assertEquals("", p.reason());
        CachePolicy none = CachePolicy.decide(g, 0);
        assertFalse(none.allowed());
        assertTrue(none.reason().contains("GL_NUM_PROGRAM_BINARY_FORMATS is 0"));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"4.6|4.6", "4.6 - |4.6 -", "4.6.0 Core Profile Context 23.1|23.1", "NVIDIA only|NVIDIA only"})
    void driverVersionEdgeCases(String version, String expected) {
        assertEquals(expected, new GpuInfo("v", "r", version, "g").driverVersion());
    }
}
