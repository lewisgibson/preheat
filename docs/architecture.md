# Architecture

Preheat retains Iris shader pipelines across dimension changes, prepares selected dimensions during startup and caches both linked programs and patched sources. Features are checked against the installed game before their mixins apply. A handled runtime failure disables the affected feature for the session.

## Why portals pause

Iris maintains a pipeline map keyed by dimension, but its level-change hook calls `PipelineManager.destroyPipeline()`, destroying every entry. The next frame builds the destination pipeline on the render thread. That includes preprocessing the shader pack, patching its sources, and compiling and linking often 100 or more programs.

The first visit can cost more because preprocessing for that dimension has not run yet. The graphics driver's shader cache may also be empty.

## Keeping each dimension's shaders

Preheat recognises the dimension-change call to `destroyPipeline()` and puts the active pipeline aside. `preparePipeline` can then return the destination's retained pipeline. Other destruction paths, including shader reloads, pack or option changes and F3+R, retain their normal behaviour.

Reactivation restores the state a new pipeline would establish:

- Restore global chunk-meshing settings from the pipeline's pack directives, allowing the level to remesh.
- Zero shader storage buffers or refill them with their declared content.
- Clear custom images and request full clears of render and shadow targets.
- Run the pack's setup programs again.
- Reset Distant Horizons' depth attachment and overrides.

Putting a pipeline aside releases texture units, samplers, framebuffer bindings and storage buffer bindings without freeing the retained resources.

Only enabled dimensions are retained. `neverKeep` takes precedence over the dimension's setting and startup policy. Leaving a disabled dimension destroys its pipeline as Iris normally would.

### Video memory

Retained pipelines keep their render and shadow targets, so memory use depends on the shader pack and resolution. Where the driver reports usable memory figures, `KeptBudget` aims to leave at least 1024 MB or 15% of total video memory free, whichever is larger. It releases inactive modded dimensions before vanilla dimensions, with the least recently used first within each group.

Startup preparation checks whether another pipeline is expected to fit. The initial estimate is 512 MB until a size has been measured. Where memory figures are unavailable, the policy limits inactive retained modded dimensions to 2 in addition to the vanilla dimensions.

## Preparing at startup

Iris builds the Overworld pipeline as loading finishes. Preheat then builds other enabled, known dimensions selected by `prepareAtStartup`, which contains the vanilla three by default. The Nether and End therefore enter the retained set before a world is opened, provided the feature applies and memory allows.

Iris files a dimension the pack does not name by its sky. One with the Overworld's or the End's sky shares that pipeline and needs no separate build. One without a sky, such as a server's second Nether, gets a pipeline of its own built from the pack's default shaders; Preheat treats it as a modded dimension. Startup patterns select dimensions already recorded in the settings. They do not discover arbitrary dimension ids.

Preheat constructs a pipeline directly instead of calling `Iris.createPipeline`. A failed preparation therefore cannot trigger Iris's fallback that disables shaders. The failure is logged and the dimension is left for its first visit. Shader storage buffers created by the failed attempt are deleted. Other resources from a partially constructed pipeline cannot be safely destroyed through Iris and are left to the driver.

## The loading screen

<img src="images/loading-screen.gif" alt="Minecraft's loading screen while Preheat prepares the Nether's shaders" width="100%">

During startup builds, Preheat restores Minecraft's loading overlay and supplies progress from the build. Frames are drawn between programs, only between game frames and while no world is loaded. Each frame restores the OpenGL state needed by the build.

Drawing is spaced to cost at most about a twentieth of build time. Builds inside a world do not draw loading frames. On Minecraft 26.3, Preheat pumps the window during these builds so the operating system continues to receive a response. Minecraft 26.2 lacks a suitable window-pumping path that avoids running game input handling during the build.

## The shader cache

Preheat hooks Iris's program compilation paths in `ProgramBuilder` and `ShaderCreator`. It obtains linked program binaries through `glGetProgramBinary` and queues their disk writes. Later builds use `glProgramBinary` to restore a program when the cache entry passes validation.

Before compilation, Iris patches shader source through `TransformPatcher`. With a warm driver cache, this work can account for much of a build. Preheat saves the patched source and restores it on later launches. Its key includes all inputs compared by Iris's own session cache, plus the program name, relevant device and debug settings, and the installed mod versions.

Read-ahead workers load and validate saved files while the game starts. The startup builds can then use those entries from memory rather than opening hundreds of files on the render thread. [The shader cache](shader-cache.md) documents keys, storage and failure handling.

## Leaving a server with Distant Horizons

Distant Horizons closes its multiplayer world on the render thread, including its levels, thread pools and databases, followed by a full garbage collection. For the supported Distant Horizons code, Preheat runs that same close operation on a background thread.

Barriers wait for it before a new connection, level change, singleplayer world or shutdown could access the world being closed. Distant Horizons level-unload listeners consequently run on the close thread.

## Threads

| Thread | Work |
| --- | --- |
| Render thread | OpenGL and Iris calls, pipeline retention and reactivation, binary extraction and loading, applying cached patches, loading frames and screens |
| `Preheat cache writer` | A single worker for cache writes, file-use timestamps, maintenance, deletion and moves |
| `Preheat cache read-ahead` | Four workers that read and validate saved entries during startup; their results are released after startup builds |
| `Preheat DH close` | Distant Horizons multiplayer-world closure, guarded by barriers |

Cache deletion and moves coordinate with outstanding work before changing the active folder. Classes that share state between threads document ownership and how updates are published.

## Code layout

| Package under `com.lewisgibson.preheat` | Responsibility |
| --- | --- |
| `feature` | Feature ownership, class-shape checks, injection checks and behaviour fingerprints |
| `keep` | Retention through `KeptPipelines`, reactivation through `Reactivation`, memory policy through `KeptBudget` and `VideoMemory`, and startup selection through `Prebuild` |
| `cache` | Program binaries, patched sources, cache keys, validation, read-ahead, folder ownership and crash recovery |
| `config` | `PreheatConfig`, dimension discovery and policy, and cache-folder selection |
| `progress` | Loading progress, frame rendering and window pumping |
| `build` | Shader-build tracking and timing |
| `ui` | Preheat's screens, Mod Menu integration and Sodium options |
| `dh` | Background closure and barriers |
| `diag` | Dimension-change, frame and disconnect diagnostics |
| `startup` | Integration with the end of Iris loading |
| `platform`, `neoforge` | Loader bridges and NeoForge entry point |
| `mixin` | Thin adapters grouped by feature |

The `src/relocate/` source set maps the Minecraft 26.3 OpenGL package references back to their 26.2 names. `testmod/` is a separate Gradle subproject used by the game harnesses and is never shipped.

## Fitting each version of Iris

Preheat checks installed class files rather than relying on an exact Iris version pin. `ShapeCheck` and `FeatureCheck` verify the methods, fields and calls each feature uses, including calls made by its helpers. A feature applies only when its targets fit.

`PipelineFingerprint` and `PatchFingerprint` also check bytecode for behaviour that cannot be established from method signatures alone. An unknown fingerprint disables the feature that depends on it. `InjectionCheck` detects a hook that Mixin could not apply despite the earlier checks.

A rejected feature is logged with its reason. Runtime failures are handled through `Features.fail` or a specific recovery path. See [compatibility](compatibility.md) for the checked versions.

## Tests and coverage

JaCoCo requires 100% line and branch coverage for the code that runs outside the game. Thin adapters that require a live game, loader or graphics context are excluded individually in `build.gradle.kts`, with a reason for each exclusion. Decisions belong in tested plain classes, behind small interfaces where they need game services.

The compatibility matrix runs the startup checks against the pinned jars. The CPU lab exercises the game adapters and rendering. See [testing](testing.md).
