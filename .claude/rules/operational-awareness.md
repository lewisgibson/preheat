# Operational awareness

What happens to a change after it is merged.

## A new version on main is a release

`.github/workflows/release.yaml` runs on every push to `main`. When `mod_version` in `gradle.properties` has no GitHub release yet, it builds both jars at that version, creates the GitHub release `v<version>` with each jar and its `.sha256`, and uploads both jars to Modrinth when the `MODRINTH_TOKEN` secret is set. Players following the mod are notified of each one. So:

- Bump `mod_version` only when a release is meant, and only once the change is finished.
- Every version after the first has a `## <version>` entry in `CHANGELOG.md` in the changelog format; the release stops without one, or when it does not name a checked version that moved in `deps.json`.
- Commit messages explain a change in plain words, but they are not the release notes. The changelog entry is.

[docs/releasing.md](../../docs/releasing.md) has the whole process.

## The jars

- One jar per Minecraft line (`preheat-<version>+mc26.3.jar`, `+mc26.2.jar`), each loading on both Fabric and NeoForge. The 26.2 jar is made from the 26.3 build by `src/relocate`, which maps the OpenGL packages 26.3 renamed back to 26.2's names.
- Supporting a new Minecraft or Iris version means a new row in `deps.json`, a green `CompatibilityMatrixTest`, and the result in `docs/compatibility.md`.

## Running the game

Unit tests and CI never start Minecraft. The CPU lab (`scripts/lab/`) renders with Mesa's software OpenGL and never uses a graphics card. The real-game harness (`scripts/realgame/`) runs a copy of an instance on your own graphics card: run it only on your own machine, never against an instance someone is playing, and stop at the first crash or driver error rather than retrying.
