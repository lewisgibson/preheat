# Modrinth listing

## Project settings

| Setting                  | Value                                                                                   |
| ------------------------ | --------------------------------------------------------------------------------------- |
| Name                     | Preheat                                                                                 |
| Slug                     | `preheat`                                                                               |
| Summary                  | Cuts the freeze at portals with Iris shaders by preparing every dimension ahead of time |
| Project type             | Mod                                                                                     |
| Loaders                  | Fabric, NeoForge                                                                        |
| Game versions            | 26.2, 26.3                                                                              |
| Client environment       | Required                                                                                |
| Server environment       | Unsupported                                                                             |
| Categories               | Optimization, Utility                                                                   |
| Licence                  | LGPL-3.0-only                                                                           |
| Source                   | https://github.com/lewisgibson/preheat                                                  |
| Issues                   | https://github.com/lewisgibson/preheat/issues                                           |
| Wiki                     | https://github.com/lewisgibson/preheat/tree/main/docs                                   |
| Dependencies per version | Iris required; Mod Menu optional                                                        |
| First release            | 1.0.0                                                                                   |
| Version numbers          | `mc26.3-1.0.0`, `mc26.2-1.0.0`                                                          |
| Icon                     | `src/main/resources/assets/preheat/icon.png`                                            |

## Gallery

| Image | Title | Description |
| --- | --- | --- |
| `docs/images/banner.png` | Preheat | Featured image |
| `docs/images/video-settings.png` | Video Settings | Preheat's page beside Iris, with Sodium's tick boxes for choosing enabled dimensions. |
| `docs/images/settings.png` | Preheat settings | A tick for each dimension, ready dimensions, saved shaders and controls for the cache folder. |
| `docs/images/loading-screen.gif` | Preparing dimensions | Minecraft's loading screen while Preheat prepares shaders for other dimensions. |

## Description

![Preheat](https://raw.githubusercontent.com/lewisgibson/preheat/main/docs/images/banner.png)

Going through a portal with shaders can freeze your game while Iris prepares the next dimension, and coming back can mean waiting again. Preheat prepares the Nether and the End while the game loads and keeps each dimension's shaders ready for your next visit. It also saves the shaders, so later launches have less to prepare.

With Complementary Reimagined on an RTX 5090, the first Nether trip without Preheat froze the game for 2.1 seconds, or 12.3 when the graphics driver's own shader cache was empty. With Preheat every measured trip took under a second. The work moves to the title screen instead, and the [measurements](https://github.com/lewisgibson/preheat/blob/main/docs/performance.md) compare the two sides.

### What Preheat does

- Keeps enabled dimensions ready while your graphics card has room, avoiding repeated shader preparation on return trips.
- Saves compiled shaders and Iris's prepared shader source for later launches. Saved files are checked before use.
- Supports modded dimensions, preparing their separate shaders on the first visit unless configured for startup.
- Lets Distant Horizons finish closing a multiplayer world in the background.

Keeping dimensions ready uses additional video memory. Preheat releases inactive dimensions when reported memory is low. The first launch with an empty cache can take longer because preparation happens during loading.

### Install

Preheat requires Java 25, [Iris](https://modrinth.com/mod/iris) and Sodium for your Minecraft version.

Pick the file for your Minecraft version, 26.3 or 26.2. Each file works on Fabric and NeoForge.

Put the jar in your client's `mods` folder alongside Iris. Nothing is required on the server.

Checked Iris versions are 1.11.5 to 1.11.7 on Fabric for Minecraft 26.3, 1.11.7 on NeoForge for 26.3, and 1.11.4 on both loaders for 26.2. See [compatibility](https://github.com/lewisgibson/preheat/blob/main/docs/compatibility.md) for the test matrix and graphics card coverage.

### Settings

Open **Options > Video Settings > Preheat**. Use Sodium's tick boxes to choose which dimensions are enabled. **Modded Dimensions** controls the initial setting for newly discovered dimensions.

Choose **Shader Cache** to open Preheat's own screen. It shows which dimensions are ready and how much is saved. **Delete Cache** removes saved shaders, and **Change Folder** lets you move them. This screen is also available from Mod Menu or NeoForge's mod list.

Untick every dimension and **Modded Dimensions** to switch Preheat off. Modpack settings are documented in [configuration](https://github.com/lewisgibson/preheat/blob/main/docs/configuration.md).

### Help and source

Read the [FAQ](https://github.com/lewisgibson/preheat/blob/main/docs/faq.md) or [troubleshooting](https://github.com/lewisgibson/preheat/blob/main/docs/troubleshooting.md). Bug reports, source code and release notes are on [GitHub](https://github.com/lewisgibson/preheat).

This is an unofficial, community mod. It is not affiliated with, authorised by, or endorsed by Iris, Sodium or Mojang. It is released under LGPL-3.0-only. Screenshots use [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) by EminGT.
