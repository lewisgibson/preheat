# Troubleshooting

Preheat writes diagnostic lines beginning with `PREHEAT` to your game's log. The examples below show what to look for. See [where the log is](#where-the-log-is) if you need to find it.

## Check whether Preheat is involved

1. Untick every dimension and **Modded Dimensions** in **Options > Video Settings > Preheat**.
2. Restart the game and repeat the same steps.
3. If the problem disappears, enable the dimensions one at a time and [report the result](#reporting-a-bug). If it remains, try removing Preheat for a further comparison.

## A portal still freezes the game

Check the destination's tick first. An unticked dimension is prepared on visits as it would be without Preheat. The log shows `PREHEAT Pipeline for minecraft:the_nether ready in ... ms (built, ...)` instead of `(reused, ...)`.

A modded dimension may need preparation on its first visit. Only enabled, known dimensions selected by `prepareAtStartup` are prepared during loading. See [configuration](configuration.md#configpreheatjson) to add a dimension.

Your graphics card may also be short of video memory. Preheat releases inactive dimensions when it needs room, starting with modded dimensions and the least recently used in each group. A later visit must prepare them again. Look for `PREHEAT Dropped the kept pipeline for <dimension> in ... ms (video memory is low (... MB free))`.

If shaders were reused but the trip still paused, terrain generation, other client work or a slow server may be responsible. `PREHEAT longest frame after <from> => <to>: ... ms` records the longest frame after the change.

Finally, check the startup line, `PREHEAT Preheat 1.0.0 on ...; features: ...`. If retaining dimensions is unavailable, another line starts `PREHEAT Turned off keeping each dimension's shaders loaded: ...` and gives the reason. See [compatibility](compatibility.md).

## The game takes longer to start

Preheat prepares the Nether and the End during loading, moving work away from the first portal trip. With saved shaders, the measured startup builds took a second or two. Your hardware and shader pack affect the time.

A new shader pack, changed settings, updated mods or a new graphics driver can require fresh preparation. Build lines may say `0 from the cache` or `nothing cached yet`.

The graphics driver's own cache also matters. If it has been cleared and Preheat has no saved shaders, preparing every dimension can take noticeably longer. A filled Preheat cache reduces that work. See [measurements](performance.md).

To skip a dimension you never visit, remove it from `prepareAtStartup` and untick it. See [configuration examples](configuration.md#examples).

## Shaders look wrong after loading

Preheat is intended to preserve the shader pack's appearance. If it looks different with Preheat enabled:

1. Choose **Delete Cache** in **Options > Video Settings > Preheat > Shader Cache**, confirm, and restart.
2. If the problem remains, add `-Dpreheat.verifyPatchCache=true` to your launcher's Java arguments and launch again.
3. Find `PREHEAT Patch cache check: <n> saved patches match what Iris made, <m> differ` in the log. Report any difference with that log and screenshots.

The [cache documentation](shader-cache.md#validation-and-recovery) explains the checks.

## Ores or other blocks do not glow

Glowing ores are controlled by the shader pack. In Complementary Reimagined, set **Enable Glowing Ores** to **ON**, or use **Set By Style** with the **Unbound** visual style.

The defaults, **Set By Style** and **Reimagined**, leave ores unlit. Preheat does not change this setting.

## The cache is paused

The game stopped twice in a row while loading saved shaders from the same cache generation. Preheat then paused the cache. The log says `PREHEAT Shader cache paused: stopped-while-loading`.

Choose **Delete Cache** to clear the saved shaders and resume caching. Closing the game from your launcher during loading can count as one stop, which is why a single stop does not pause the cache.

## Your graphics card cannot save shaders

Some drivers offer no way to save compiled shaders. Preheat leaves the cache off while the other features remain available.

The log says `PREHEAT Shader cache off: the driver offers no program binary formats (GL_NUM_PROGRAM_BINARY_FORMATS is 0)`. This can occur on some drivers, including macOS drivers.

## Your graphics card turned down saved shaders

After three refusals in one session, Preheat prepares shaders fresh until the next launch. The log says `PREHEAT The driver refused 3 cached shaders this session (last: ...); compiling instead until the next launch`.

A driver change can cause saved shaders to be rejected. If it happens on every launch, [report it](#reporting-a-bug) with the log.

## Your chosen folder cannot be used

The folder may be unavailable or read-only, or it may contain a `preheat-shader-cache` folder that Preheat does not recognise as its own.

Preheat uses the default location meanwhile. The log says `PREHEAT Shader cache: the chosen folder <folder> cannot be used (<reason>); using <default> instead`.

Choose another location with **Change Folder**, or choose **Use the Default Folder**.

## Rejoining with Distant Horizons takes a moment

Preheat lets Distant Horizons finish closing a multiplayer world in the background. If you join again before it finishes, the game waits before accessing that world state.

The log says `PREHEAT waiting for Distant Horizons to finish closing before <what>`, followed by `PREHEAT waited ... ms for Distant Horizons to finish closing`.

## Where the log is

Open `logs/latest.log` inside your game folder.

| Launcher | Finding the game folder |
| --- | --- |
| Modrinth App | Open the instance page and use its folder button. |
| Prism Launcher | Right-click the instance and choose **Folder**. |
| Official launcher | Use the installation's game directory. Defaults are `%APPDATA%\.minecraft` on Windows, `~/Library/Application Support/minecraft` on macOS and `~/.minecraft` on Linux. |

The timing and hardware figures below are examples from recorded runs. Your log will contain your own versions and measurements.

| Log line | Meaning |
| --- | --- |
| `PREHEAT Preheat 1.0.0 on Fabric 0.19.5, Minecraft 26.3, Iris 1.11.7+mc26.3, Sodium 0.9.2+mc26.3, Distant Horizons 3.3.4; checked in 301.7 ms; features: keep_pipelines, ...` | Installed versions, time spent checking compatibility and the enabled features |
| `PREHEAT Turned off <feature>: <reason>` | A feature could not be enabled for this setup |
| `PREHEAT Turned off <feature> for this session after an error; the game carries on without it` | A feature failed during play; the error follows |
| `PREHEAT Preheat 1.0.0 is doing nothing: Iris is not installed` | Iris is missing |
| `PREHEAT GPU: NVIDIA GeForce RTX 5090/PCIe/SSE2 (NVIDIA Corporation), driver NVIDIA 616.92, OpenGL 3.3.0 NVIDIA 616.92, GLSL 3.30 NVIDIA via Cg compiler; NVIDIA hardware, NVIDIA driver; 1 program binary formats (0x8e21)` | Graphics card, driver and support for saving shaders |
| `PREHEAT Shader cache at <folder>: <note>` | Cache location and driver information |
| `PREHEAT Shader cache paused: ...`, `PREHEAT Shader cache off: ...` | Why saved shaders are not being used |
| `PREHEAT Shader cache: read 523 saved shaders (30.4 MB) ahead in 80.8 ms` | Saved shaders read during loading |
| `PREHEAT Built the Nether shaders (minecraft:the_nether) in 1139.5 ms = preprocess 150.1, transform 490.1, compile 0.3 (6 shaders), link 0.0, binary load 221.5, binary save 0.0, other 277.5; 101 programs, 101 from the cache; verified 101 cached binaries (12.2 MB) in 15.5 ms` | Total shader preparation time, its parts and how much came from saved files |
| `PREHEAT Prepared the pipeline for minecraft:the_nether at startup in 1160.1 ms (2 kept)` | A dimension prepared during loading |
| `PREHEAT Not preparing <dimension> at startup: video memory is low (... MB free); it will be built on the first visit` | A dimension left for its first visit because video memory was low |
| `PREHEAT Loading screen: 20 frames drawn during shader builds, 154.0 ms in total` | Time spent drawing loading-screen frames |
| `PREHEAT Pipeline for minecraft:the_nether ready in 0.7 ms (reused, 3 kept)` | A trip using shaders already prepared; `built` would mean they were prepared during the trip |
| `PREHEAT longest frame after minecraft:overworld => minecraft:the_nether: 94.1 ms (first frame 17.0 ms after the change)` | The longest frame after a dimension change |
| `PREHEAT Video memory: 21071 MB free of 32607 MB, 3 pipelines kept ({minecraft:overworld=2095, minecraft:the_end=2092, minecraft:the_nether=2095})` | Free video memory and estimated use by each retained dimension, in MB |
| `PREHEAT Dropped the kept pipeline for <dimension> in ... ms (<why>)` | A dimension released from video memory, with the reason |
| `PREHEAT Shader cache: deleted 1 older cache generation(s)` | Saved shaders for an older driver, Iris or Preheat removed |
| `PREHEAT Shader cache: over its 4 GB safety limit, deleted the <n> oldest files` | Cache maintenance removed older entries to reduce disk use |
| `PREHEAT The driver refused a cached shader (<why>); compiling it instead` | A rejected saved shader being prepared again |
| `PREHEAT disconnect: ... ms of work (...)` | Time spent leaving a world |

## Reporting a bug

[Open a bug report](https://github.com/lewisgibson/preheat/issues/new/choose) with:

- What happened, how to reproduce it and whether it changes with every dimension unticked.
- Your Minecraft, loader, Preheat, Iris and Sodium versions, shader pack, operating system, graphics card and driver version.
- A link to `logs/latest.log` uploaded to [mclo.gs](https://mclo.gs), plus screenshots if the problem is visual.

The `PREHEAT GPU:` line contains your graphics card and driver. Reports from AMD and Intel cards help extend testing beyond the NVIDIA and Mesa setups checked so far.
