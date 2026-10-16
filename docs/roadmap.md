# Roadmap

What is planned after 1.0.0. Each release's changes are in [CHANGELOG.md](../CHANGELOG.md) and on [GitHub Releases](https://github.com/lewisgibson/preheat/releases).

## Next

- [ ] Make the first Nether and End trips with a filled cache as quick as with an empty one when the graphics driver's cache is cold (0.82 and 0.90 s against 0.11 s in the [measurements](performance.md#the-first-trip-with-a-filled-cache)). A fix is being tested.
- [ ] Collect test results from AMD and Intel graphics cards. Reports should include the information in [troubleshooting](troubleshooting.md#reporting-a-bug).
- [ ] Run NeoForge on a real graphics card. The CPU-only lab already covers NeoForge.
- [ ] Test modded dimensions in real packs when the major dimension mods reach Minecraft 26.x. As of October 2026, the relevant releases target 1.21.1 or older.
- [ ] Measure cold driver-cache behaviour on AMD and Intel, alongside the existing [NVIDIA measurements](performance.md).

## Ideas under consideration

These have no planned release:

- Controls for `prepareAtStartup` and `neverKeep` on Preheat's screen.
- A backport to Minecraft 1.21.1.
