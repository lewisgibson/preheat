package com.lewisgibson.preheat.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StackOwnerTest {
    private static StackTraceElement f(String cls, String method) {
        return new StackTraceElement(cls, method, null, 0);
    }

    @Test
    void innermostModFrameWins() {
        StackTraceElement[] stack = {
            f("java.lang.Object", "wait"),
            f("java.util.concurrent.ThreadPoolExecutor", "awaitTermination"),
            f("com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker", "shutdownNow"),
            f("com.seibel.distanthorizons.core.api.internal.SharedApi", "setDhWorld"),
            f("net.minecraft.client.multiplayer.ClientPacketListener", "close"),
        };
        assertEquals("distanthorizons|PriorityTaskPicker.shutdownNow", StackOwner.of(stack));
    }

    @Test
    void minecraftAndUnknown() {
        assertEquals("minecraft|Minecraft.renderFrame", StackOwner.of(new StackTraceElement[] {
            f("org.lwjgl.opengl.GL11C", "glFinish"), f("net.minecraft.client.Minecraft", "renderFrame")
        }));
        assertEquals(
                "other|GL11C.glFinish",
                StackOwner.of(new StackTraceElement[] {f("org.lwjgl.opengl.GL11C", "glFinish")}));
        assertEquals("other|?", StackOwner.of(new StackTraceElement[0]));
    }

    @Test
    void preheatHandlersMergedIntoOtherClasses() {
        assertEquals("preheat|Minecraft.handler$zab000$preheat$dropOnLeave", StackOwner.of(new StackTraceElement[] {
            f("org.lwjgl.opengl.GL11C", "glDeleteTextures"),
            f("net.minecraft.client.Minecraft", "handler$zab000$preheat$dropOnLeave"),
            f("net.minecraft.client.Minecraft", "clearClientLevel")
        }));
        // Time inside Iris's own destroy, called from Preheat, is Iris's.
        assertEquals("iris|IrisRenderingPipeline.destroy", StackOwner.of(new StackTraceElement[] {
            f("net.irisshaders.iris.pipeline.IrisRenderingPipeline", "destroy"),
            f("net.irisshaders.iris.pipeline.PipelineManager", "preheat$destroyOne")
        }));
    }

    @Test
    void aClassInNoPackageIsItsOwnShortName() {
        assertEquals("Main", StackOwner.shortClass("Main"));
        assertEquals("Minecraft", StackOwner.shortClass("net.minecraft.client.Minecraft"));
    }
}
