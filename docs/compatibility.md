# Compatibility

## Java

Minecraft 26.2 and 26.3 require Java 25. The Modrinth App, Prism Launcher and official launcher can install it. With an older Java runtime, the loader reports the requirement before loading Preheat.

## Minecraft, loaders and Iris

Preheat checks each feature against the installed class files before applying it. The tests run those checks against the actual jars pinned in [`deps.json`](../deps.json). Expected results are reviewed in [`compat-expected.txt`](../src/test/resources/compat-expected.txt).

| Minecraft | Loader | Iris | Keep dimensions | Prepare at startup | Shader cache | Loading screen | Distant Horizons close |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 26.3 | Fabric | 1.11.5, 1.11.6, 1.11.7 | Yes | Yes | Yes | Yes | Yes |
| 26.3 | NeoForge | 1.11.7 | Yes | Yes | Yes | Yes | Yes |
| 26.2 | Fabric, NeoForge | 1.11.4 | Yes | Yes | Yes | Yes | Yes |
| 26.1 | Fabric, NeoForge | 1.11.4 | No | No | No | No | No |

The Distant Horizons column applies when it is installed. The matrix checks Distant Horizons 3.3.4 on 26.3 and 3.3.3 on 26.2.

Preheat compiles against 26.3. Its 26.2 jar contains the same code with references to the renamed OpenGL packages mapped back. On 26.2, the loading screen still draws, but Preheat cannot keep the window responding during builds inside a world without also running game input handling.

Minecraft 26.1 is unsupported because its renderer differs at the required hooks. The matrix also contains 26.1.2 checks to record that boundary. There is no jar for 26.1 or older versions such as 1.21.x.

A newer Iris may disable an affected feature if its structure or checked behaviour changes. The log gives the reason. See [architecture](architecture.md#fitting-each-version-of-iris).

## Leaving a server with Distant Horizons

With the checked Distant Horizons 3.3 implementation, closing a multiplayer world took about 2 seconds on the render thread. Moving that work to the background reduced the foreground disconnect work to about 0.05 seconds.

A new connection or world waits if closure is still in progress. Mods listening for Distant Horizons' level-unload event receive that event on the background close thread.

## Other mods

`NeighbourMixinsTest` reads other mods' mixins and detects injections into methods Preheat also uses. An overlap must be reviewed and recorded in [`neighbour-overlaps.txt`](../src/test/resources/neighbour-overlaps.txt).

The checked mods on 26.3 are ImmediatelyFast, Sodium Extra, Reese's Sodium Options, Entity Culling, More Culling, Lithium, C2ME, BetterF3, Nvidium, FerriteCore and Distant Horizons. On 26.2, the set includes Voxy and Distant Horizons.

The recorded overlaps involve Distant Horizons, Voxy and ImmediatelyFast at world loading or disconnect points. Their hooks can run alongside Preheat's. These checks cover injection overlap, not every possible interaction during play.

If another mod prevents a Preheat hook from applying, the affected feature is disabled and the log records it.

## Modded dimensions

The default startup list contains the Overworld, Nether and End. Other dimensions are prepared on their first visit and retained while memory allows.

A modded dimension with shaders of its own in the pack can appear separately in Preheat's settings. It starts enabled according to **Modded Dimensions**, unless an explicit setting already exists. Preheat's screen can show newly discovered dimensions immediately; the Video Settings page shows them after a restart.

A dimension without shaders of its own in the pack uses the Overworld's or the End's shaders when it has that dimension's sky, and follows that dimension's setting without another retained pipeline. A dimension with no sky, such as a server's second Nether, gets its own retained pipeline built from the pack's default shaders. It follows **Modded Dimensions** (or its own entry in `config/preheat.json`) and is released before the vanilla three when video memory is short.

Preheat discovers dimensions from information the client already receives and from the dimension being entered. Nothing is required on the server. Dimensions created during play can be discovered on entry.

### Dimension lists

`config/preheat.json` contains two pattern lists:

```json
{
  "prepareAtStartup": ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "aether:the_aether"],
  "neverKeep": ["rftoolsdim:*"]
}
```

`prepareAtStartup` selects enabled dimensions already known to Preheat. An explicit modded dimension can be included in the `dimensions` map when distributing a modpack configuration. Otherwise it must first be discovered in a game.

`neverKeep` takes precedence over startup preparation and individual ticks. It suits packs that create many dimensions, such as dimensions per player or item. Example namespaces include `rftoolsdim:*`, `mystcraft_ageless:*` and `multiworld:*`.

As of October 2026, the major dimension mods discussed here, including Twilight Forest, the Aether, Blue Skies, Deeper and Darker, and RFTools Dimensions, target Minecraft 1.21.1 or older. Their dimensions have not been tested with Iris in a real Preheat-compatible pack. The examples document the configuration format.

See [configuration](configuration.md#patterns) for pattern matching.

## Video memory

Retained dimensions use video memory for their render and shadow targets. Where the driver reports memory availability, Preheat releases inactive modded dimensions before vanilla ones, starting with the least recently used in each group. It can also skip startup preparation when another dimension is unlikely to fit.

Drivers without usable memory reports receive a count limit for retained modded dimensions. The exact policy is in [architecture](architecture.md#video-memory).

## Graphics cards

Program binary support varies by driver. The other features do not depend on program binary support.

| Driver | Cache testing |
| --- | --- |
| NVIDIA on Windows | Tested. Cached binaries contain compiled GPU code. |
| Mesa llvmpipe | Tested. The cache fills, subsequent launches restore the programs, and rendered results match compilation. Mesa recompiles its binaries on load and has its own cache, so gains depend on that cache's state. |
| AMD on Windows or Mesa radeonsi on Linux | Not yet tested. Enabled with the normal validation checks. |
| Intel on Windows or Mesa iris on Linux | Not yet tested. Enabled with the normal validation checks. |
| Apple | Not yet tested. The cache disables itself when the driver offers no binary formats. |

See [the shader cache](shader-cache.md#validation-and-recovery) for validation and [troubleshooting](troubleshooting.md#reporting-a-bug) for reporting results.
