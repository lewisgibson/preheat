package com.lewisgibson.preheat.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** GL strings as real drivers report them, and what Preheat reads from each. */
class GpuInfoTest {
    private static void check(
            String vendor,
            String renderer,
            String version,
            GpuInfo.Hardware hw,
            GpuInfo.Driver driver,
            String driverVersion) {
        GpuInfo g = new GpuInfo(vendor, renderer, version, "4.60");
        assertEquals(hw, g.hardware(), renderer);
        assertEquals(driver, g.driver(), renderer);
        assertEquals(driverVersion, g.driverVersion(), version);
    }

    @Test
    void nvidiaOnWindows() {
        check(
                "NVIDIA Corporation",
                "NVIDIA GeForce RTX 5090/PCIe/SSE2",
                "4.6.0 NVIDIA 595.12",
                GpuInfo.Hardware.NVIDIA,
                GpuInfo.Driver.NVIDIA,
                "NVIDIA 595.12");
    }

    @Test
    void amdOnWindows() {
        check(
                "ATI Technologies Inc.",
                "AMD Radeon(TM) Graphics",
                "4.6.0 Compatibility Profile Context 24.10.1.240924",
                GpuInfo.Hardware.AMD,
                GpuInfo.Driver.AMD,
                "24.10.1.240924");
    }

    @Test
    void amdOnLinuxMesa() {
        check(
                "AMD",
                "AMD Radeon RX 6800 (radeonsi, navi21, LLVM 17.0.6, DRM 3.57, 6.8.0)",
                "4.6 (Compatibility Profile) Mesa 24.0.9",
                GpuInfo.Hardware.AMD,
                GpuInfo.Driver.MESA,
                "Mesa 24.0.9");
    }

    @Test
    void intelOnWindows() {
        check(
                "Intel",
                "Intel(R) UHD Graphics 630",
                "4.6.0 - Build 31.0.101.2111",
                GpuInfo.Hardware.INTEL,
                GpuInfo.Driver.INTEL,
                "Build 31.0.101.2111");
    }

    @Test
    void intelOnLinuxMesa() {
        check(
                "Intel",
                "Mesa Intel(R) Arc(tm) A770 Graphics (DG2)",
                "4.6 (Core Profile) Mesa 24.2.8",
                GpuInfo.Hardware.INTEL,
                GpuInfo.Driver.MESA,
                "Mesa 24.2.8");
    }

    @Test
    void nouveauIsNvidiaHardwareOnMesa() {
        check(
                "Mesa",
                "NV136",
                "4.3 (Compatibility Profile) Mesa 24.0.9",
                GpuInfo.Hardware.NVIDIA,
                GpuInfo.Driver.MESA,
                "Mesa 24.0.9");
    }

    @Test
    void softwareAndD3d12AndApple() {
        check(
                "Mesa",
                "llvmpipe (LLVM 19.1.7, 256 bits)",
                "4.5 (Core Profile) Mesa 25.0.7",
                GpuInfo.Hardware.SOFTWARE,
                GpuInfo.Driver.MESA,
                "Mesa 25.0.7");
        check(
                "Microsoft Corporation",
                "D3D12 (NVIDIA GeForce RTX 5090)",
                "4.6 (Core Profile) Mesa 24.2.8",
                GpuInfo.Hardware.NVIDIA,
                GpuInfo.Driver.MESA,
                "Mesa 24.2.8");
        check("Apple", "Apple M2", "4.1 Metal - 88.1", GpuInfo.Hardware.APPLE, GpuInfo.Driver.APPLE, "Metal - 88.1");
    }

    @Test
    void nullStringsAreEmptyAndDescribeIsOneLine() {
        GpuInfo g = GpuInfo.of(null, null, null, null);
        assertEquals(GpuInfo.Hardware.OTHER, g.hardware());
        assertFalse(g.describe().contains("\n"));
        assertTrue(new GpuInfo("NVIDIA Corporation", "RTX", "4.6.0 NVIDIA 595.12", "4.60")
                .describe()
                .contains("driver NVIDIA 595.12"));
    }

    @Test
    void policyTurnsTheCacheOffWithNoFormatsAndOnOtherwise() {
        GpuInfo nvidia = new GpuInfo("NVIDIA Corporation", "RTX", "4.6.0 NVIDIA 595.12", "4.60");
        assertFalse(CachePolicy.decide(nvidia, 0).allowed());
        assertTrue(CachePolicy.decide(nvidia, 1).allowed());
        assertTrue(CachePolicy.decide(new GpuInfo("Intel", "Intel(R) UHD Graphics 630", "4.6.0 - Build 31", "4.60"), 1)
                .note()
                .contains("not yet tested"));
        assertTrue(CachePolicy.decide(new GpuInfo("Mesa", "llvmpipe", "4.5 Mesa 25", "4.50"), 1)
                .note()
                .contains("Mesa"));
    }

    @Test
    void mesasD3d12LayerOnWslIsMesaOverTheCardBelow() {
        check(
                "Microsoft Corporation",
                "D3D12 (NVIDIA RTX A6000)",
                "4.6 (Core Profile)",
                GpuInfo.Hardware.NVIDIA,
                GpuInfo.Driver.MESA,
                "4.6 (Core Profile)");
    }
}
