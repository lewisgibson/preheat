# Contributing

Issues and pull requests are welcome. For a bug report, include the information in [troubleshooting](docs/troubleshooting.md#reporting-a-bug). Report security issues through the private channel in [SECURITY.md](SECURITY.md).

## Prerequisites

Building requires a Java 25 JDK, with `JAVA_HOME` set to it, and Python 3. The build and unit tests need no graphics card or game installation.

Read [CLAUDE.md](CLAUDE.md) and the four repository rule files before changing code:

- [Java architecture](.claude/rules/java-architecture.md)
- [Java consistency](.claude/rules/java-consistency.md)
- [Review checklist](.claude/rules/java-review-checklist.md)
- [Operational awareness](.claude/rules/operational-awareness.md)

## Build and test

| Command | Purpose |
| --- | --- |
| `python3 scripts/fetch-deps.py` | Fetch the jars pinned by SHA-1 in `deps.json` into `libs/`. Run once and after changing `deps.json`. |
| `./gradlew spotlessApply` | Format Java code. |
| `./gradlew build` | Check formatting, run Error Prone and NullAway, run the tests and compatibility matrix, enforce 100% line and branch coverage, then build both jars. |
| `make test-scripts` | Test every Python script, at 100% line and branch coverage. |
| `make lint` | Prettier, shellcheck, actionlint, gitleaks and the changelog format. |
| `make hooks` | Install the pre-commit hook, once after cloning. |
| `./gradlew :testmod:build` | Build the game test driver. It is never shipped. |

JaCoCo counts the code that can run outside the game. The only exclusions are thin game adapters, including mixins, loader entry points, screens and OpenGL or Iris adapters. Each exclusion is listed with its reason in `build.gradle.kts`. New logic belongs in a counted class.

`CompatibilityMatrixTest` runs the startup checks against the actual jars in `deps.json`. A change to which features apply must update `src/test/resources/compat-expected.txt` and [compatibility](docs/compatibility.md). `NeighbourMixinsTest` detects new overlaps between Preheat's injection targets and those of the other mods in the test set.

## Checking changes in a game

Use the CPU-only lab in `scripts/lab/` for rendering, loading screen and settings changes. It runs Minecraft with Mesa's software OpenGL on a virtual display under Linux or WSL. Inspect the screenshots as well as the logs. `scripts/lab/neoforge.sh` covers NeoForge.

The harness in `scripts/realgame/` runs a copy of an instance on a real graphics card. It checks cold and warm launches and can measure startup and portal trips. Run it on a developer's machine with the game closed. Stop after a crash or driver error.

Commands and prerequisites are in [testing](docs/testing.md). CI never runs a game.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/com/lewisgibson/preheat/feature/` | Startup compatibility checks and feature ownership |
| `src/main/java/com/lewisgibson/preheat/keep/` | Pipeline retention, reactivation, startup preparation and video memory policy |
| `src/main/java/com/lewisgibson/preheat/cache/` | Cache keys, file formats, validation and read-ahead |
| `src/main/java/com/lewisgibson/preheat/ui/` | Preheat's screens and Sodium settings integration |
| `src/main/java/com/lewisgibson/preheat/mixin/` | Thin mixins grouped by feature |
| `src/relocate/` | Conversion of the 26.3 jar's package references for 26.2 |
| `src/test/` | Unit tests and reviewed compatibility expectations |
| `testmod/` | Test-only driver mod |
| `scripts/` | Dependency and release scripts, the game harnesses, and the tests for every Python script |
| `run/` | Gitignored game runtimes and test results |
| `docs/` | Contributor documentation and player guides |

The complete package map is in [architecture](docs/architecture.md#code-layout).

## Coding requirements

- Failures must be contained. A feature with incompatible targets must not apply. A runtime failure must be handled and, where appropriate, reported through `Features.fail`.
- Every mixin belongs to exactly one `Feature` and is listed in `preheat.mixins.json`. Keep decisions in plain classes that can be tested.
- Inspect the actual Iris, Sodium and Minecraft class files with `javap` against `libs/` before relying on their internals.
- Keep OpenGL and Iris calls on the render thread. Document ownership and publication of state shared between threads.
- Match neighbouring files. Spotless controls Java formatting; Error Prone and NullAway run with warnings treated as errors.
- Use British spelling and plain words in player-facing text. Put game text in `assets/preheat/lang/en_us.json`.
- Add a regression test for a bug fix. Keep settings and log documentation current.

External jars belong in `deps.json`, pinned by SHA-1. Do not copy them into `libs/` by hand. Maven dependency versions belong in `gradle/libs.versions.toml`. After changing them, refresh dependency locks and verification metadata:

```sh
./gradlew dependencies --write-locks
./gradlew --write-verification-metadata sha256 build
```

## Pull requests and releases

Work on a branch and open a pull request when the change is ready. Explain the problem, the resulting behaviour and how it was checked. Run the build and script tests before submitting.

CI runs the build, the script tests, Prettier, actionlint, shellcheck and gitleaks. Dependabot opens weekly updates for the Gradle build, the workflows and the Prettier pin.

A pull request does not change the version. The maintainer bumps `mod_version` and writes the changelog entry when a release is cut, as described in [releasing](docs/releasing.md).

Contributions use the project's [LGPL-3.0-only licence](LICENSE).
