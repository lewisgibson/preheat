# Deployment

Preheat is published on GitHub Releases and Modrinth. Each release has one jar per Minecraft line, and each jar loads on both Fabric and NeoForge.

## Release files

| Jar                        | Minecraft | Loaders             |
| -------------------------- | --------- | ------------------- |
| `preheat-1.0.0+mc26.3.jar` | 26.3      | Fabric and NeoForge |
| `preheat-1.0.0+mc26.2.jar` | 26.2      | Fabric and NeoForge |

Both jars contain `fabric.mod.json` and `META-INF/neoforge.mods.toml`. The 26.2 jar is derived from the 26.3 build by `src/relocate/`, which maps the renamed OpenGL packages back to their 26.2 names.

Release jars contain no bundled third-party code. They include the licence texts as `LICENSE_preheat`, `COPYING_preheat` and `COPYING.LESSER_preheat`. See [third-party notices](../THIRD-PARTY-NOTICES.txt).

Preheat is client-side. Fabric's metadata prevents it loading on a dedicated server. NeoForge can load the jar on a server, but Preheat applies no features and produces no Preheat startup log there.

## Installation

Install Iris and Sodium for the same Minecraft version, then add the matching Preheat jar to the instance's `mods` folder.

| Launcher | Installation |
| --- | --- |
| Modrinth App | Use **Add content** on the instance page, or drop the jar onto its mod list. |
| Prism Launcher | Open **Edit > Mods > Add file** for the instance. |
| Official launcher | Use the installation's game folder. Default mod folders are `%APPDATA%\.minecraft\mods` on Windows, `~/Library/Application Support/minecraft/mods` on macOS and `~/.minecraft/mods` on Linux. |

Replace the previous Preheat jar when upgrading. Settings remain in `config/preheat.json`.

To uninstall, close the game and remove the jar. The settings file and default `preheat` folder can also be removed. If a custom cache folder was selected, its `preheat-shader-cache` subfolder remains there until deleted.

How versions are numbered and how a release is published is in [releasing](releasing.md).
