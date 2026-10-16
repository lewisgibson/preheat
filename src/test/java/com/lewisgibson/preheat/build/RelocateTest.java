package com.lewisgibson.preheat.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RelocateTest {
    @Test
    void mapsTheRenamedPackagesInInternalNamesDottedNamesAndMixinTargets() {
        assertEquals(
                "com/mojang/blaze3d/opengl/GlStateManager",
                Relocate.map("com/mojang/renderpearl/backend/opengl/GlStateManager"));
        assertEquals(
                "com.mojang.blaze3d.vertex.VertexFormat",
                Relocate.map("com.mojang.renderpearl.api.vertex.VertexFormat"));
        assertEquals(
                "Lcom/mojang/blaze3d/opengl/GlStateManager;glLinkProgram(I)V",
                Relocate.map("Lcom/mojang/renderpearl/backend/opengl/GlStateManager;glLinkProgram(I)V"));
        assertEquals(
                "com/mojang/renderpearl/api/textures/GpuTexture",
                Relocate.map("com/mojang/renderpearl/api/textures/GpuTexture"),
                "packages Preheat does not use stay as they are, and the matrix test would catch a use");
    }
}
