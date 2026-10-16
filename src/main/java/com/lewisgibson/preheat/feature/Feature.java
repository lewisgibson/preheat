package com.lewisgibson.preheat.feature;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The parts of Preheat that are checked and switched as one unit. A feature applies only when every mixin and helper
 * class it lists fits the installed Iris and Minecraft ({@link ShapeCheck}) and every feature it requires applies too;
 * otherwise none of its mixins are applied, it logs why once, and the rest of Preheat carries on.
 *
 * Mixin names are relative to {@code com.lewisgibson.preheat.mixin}; helper classes to {@code com.lewisgibson.preheat}.
 * Entries under {@code dh.} are checked and applied only when Distant Horizons is installed.
 */
public enum Feature {
    /** Keep each dimension's Iris pipeline when the player changes dimension. */
    KEEP_PIPELINES(
            "keeping each dimension's shaders loaded",
            List.of(
                    "keep.PipelineManagerMixin",
                    "keep.ShaderStorageBufferHolderMixin",
                    "keep.IrisRenderingPipelineAccessor",
                    "keep.RenderTargetsAccessor",
                    "keep.ShaderStorageBufferAccessor",
                    "keep.ShaderStorageBufferHolderAccessor",
                    "keep.ShadowRenderTargetsAccessor",
                    "keep.DHCompatAccessor",
                    "keep.DimensionDiscoveryMixin",
                    "dh.DHCompatInternalAccessor"),
            List.of(
                    "keep.IrisReactivation",
                    "keep.DimensionChange",
                    "keep.KeptBudget",
                    "keep.LwjglVideoMemory",
                    "dh.DhBridge"),
            List.of()),

    /** Count programs and time each step while a pipeline builds; drives the progress screen and the build log line. */
    BUILD_TRACKING(
            "tracking shader builds",
            List.of(
                    "track.GlShaderMixin",
                    "track.ShaderCreatorTrackMixin",
                    "track.ProgramCreatorTrackMixin",
                    "track.ProgramBuilderTrackMixin",
                    "track.PipelineBuildMixin"),
            List.of("build.BuildTimer", "build.BuildTracker"),
            List.of()),

    /** The finer split of a build in the log (preprocessing, patching, the wait for a link). */
    BUILD_SPLIT(
            "timing each step of a shader build",
            List.of("track.ShaderPackMixin", "track.TransformPatcherMixin", "track.GlStateManagerMixin"),
            List.of(),
            List.of("BUILD_TRACKING")),

    /** The hook on the end of the game's loading, where Iris builds its first pipeline. */
    STARTUP(
            "hooking the end of the game's loading",
            List.of("startup.IrisLoadingMixin"),
            List.of("startup.Startup", "startup.StartupBuilds"),
            List.of()),

    /** Build the Nether's (and optionally the End's) pipeline while the game is loading. */
    PREBUILD(
            "preparing other dimensions at startup",
            List.of(),
            List.of("keep.Prebuild"),
            List.of("KEEP_PIPELINES", "STARTUP")),

    /** Keep compiled shader programs on disk and load them instead of compiling. */
    PROGRAM_CACHE(
            "the shader cache",
            List.of(
                    "cache.ProgramBuilderCacheMixin",
                    "cache.ProgramBuilderAccessor",
                    "cache.ShaderCreatorCacheMixin",
                    "cache.PartialShaderAccessor",
                    "cache.ProgramCreatorCacheMixin",
                    "cache.GlStateManagerDeleteMixin"),
            List.of("cache.ProgramBinaries", "cache.LwjglProgramGl", "cache.ProgramInterface"),
            List.of("BUILD_TRACKING")),

    /** Keep Iris's patched shader sources on disk and use them instead of patching again. */
    PATCH_CACHE(
            "saving patched shaders",
            List.of("cache.TransformPatcherCacheMixin"),
            List.of("cache.PatchCache"),
            List.of("PROGRAM_CACHE")),

    /** Show build progress on Minecraft's own loading screen. */
    LOADING_PROGRESS(
            "progress on the loading screen",
            List.of("progress.LoadingOverlayMixin", "progress.MinecraftFrameMixin", "progress.MinecraftInvoker"),
            List.of(
                    "progress.BuildProgress",
                    "progress.ProgressReload",
                    "progress.FrameRenderer",
                    "progress.LwjglStateGl"),
            List.of("BUILD_TRACKING", "STARTUP")),

    /** Close Distant Horizons' multiplayer world on a background thread when leaving a server. */
    BACKGROUND_DH_CLOSE(
            "closing Distant Horizons in the background",
            List.of("dh.ClientApiMixin", "barrier.MinecraftBarrierMixin", "barrier.ClientPacketListenerBarrierMixin"),
            List.of("dh.AsyncDisconnect"),
            List.of(),
            true),

    /** The timing lines for dimension changes and disconnects. */
    DIAGNOSTICS(
            "timing lines in the log",
            List.of("diag.MinecraftDiagMixin", "diag.ClientPacketListenerDiagMixin"),
            List.of("diag.FrameWatch", "diag.DisconnectTrace"),
            List.of());

    /** How the feature reads in a log line or the settings screen. */
    public final String label;
    /** Mixin classes, relative to the mixin package. */
    public final ImmutableList<String> mixins;
    /** Helper classes whose calls into Iris and Minecraft must resolve, relative to the root package. */
    public final ImmutableList<String> helpers;
    /** Names of the features this one needs. */
    public final ImmutableList<String> requires;

    /** Whether the feature has anything to do without Distant Horizons installed. */
    public final boolean needsDistantHorizons;

    Feature(String label, List<String> mixins, List<String> helpers, List<String> requires) {
        this(label, mixins, helpers, requires, false);
    }

    Feature(
            String label,
            List<String> mixins,
            List<String> helpers,
            List<String> requires,
            boolean needsDistantHorizons) {
        this.label = label;
        this.mixins = ImmutableList.copyOf(mixins);
        this.helpers = ImmutableList.copyOf(helpers);
        this.requires = ImmutableList.copyOf(requires);
        this.needsDistantHorizons = needsDistantHorizons;
    }

    /** True for an entry that names Distant Horizons' own classes, so it is checked and applied only with it installed. */
    public static boolean distantHorizonsEntry(String entry) {
        return entry.startsWith("dh.");
    }

    /** The feature a mixin class (fully qualified) belongs to, or null. */
    public static @Nullable Feature ofMixin(String mixinClassName) {
        for (Feature f : values()) {
            for (String m : f.mixins) {
                if (mixinClassName.endsWith(".mixin." + m)) {
                    return f;
                }
            }
        }
        return null;
    }
}
