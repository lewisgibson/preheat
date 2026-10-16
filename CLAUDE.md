# Preheat

Instructions for anyone, person or agent, working in this repository. User instructions take precedence. Claude Code loads this file and `.claude/rules/` by itself; other agents start from `AGENTS.md`, which points here.

**If `AGENTS.local.md` exists, read it now and follow it.** It is gitignored and holds one machine's specifics (where a game instance lives, what not to touch). Local notes go there, never in a tracked file.

## What this is

A client-side Minecraft mod that cuts the freeze at portals with Iris shaders by preparing every dimension ahead of time. It keeps each dimension's shader pipeline built across dimension changes, prepares the Nether and the End while the game loads, saves compiled shaders and Iris's patched sources to disk, and lets Distant Horizons close its multiplayer world off the render thread. One jar per Minecraft line loads on both Fabric and NeoForge. Minecraft 26.x ships unobfuscated, so there is no Loom and no remapping: the code compiles straight against the game's jars in `libs/`. [README.md](README.md) is the user-facing page, the detail is in [docs/](docs/), and [docs/architecture.md](docs/architecture.md) explains how the code fits together.

## Build and test

| Command | What it does |
| --- | --- |
| `python3 scripts/fetch-deps.py` | Fetch every jar in `deps.json` into `libs/`, pinned by sha1 (once, and after `deps.json` changes) |
| `./gradlew build` | Formatting, Error Prone and NullAway, the tests, 100% line and branch coverage, the compatibility matrix, both jars |
| `./gradlew spotlessApply` | Format the Java code |
| `./gradlew :testmod:build` | Build the test driver mod the CPU lab and the real-game harness use |
| `make test-scripts` | Every Python script's tests, at 100% line and branch coverage |
| `make lint` | Prettier, shellcheck, actionlint, gitleaks and the changelog format |
| `make hooks` | Install the pre-commit gate (run once after cloning) |

`JAVA_HOME` must be a Java 25 JDK. `hooks/pre-commit` runs the checks that what is staged needs, and the Build, Lint and Security workflows run them all on every push to `main` and every pull request. No workflow ever starts a game.

## Layout

| Path | What is there |
| --- | --- |
| `src/main/java/com/lewisgibson/preheat/` | The mod; [docs/architecture.md](docs/architecture.md#code-layout) describes each package |
| `src/test/` | Unit tests, and `compat-expected.txt`, what applies on each version in `deps.json` |
| `src/relocate/` | Turns the 26.3 jar into the 26.2 jar |
| `testmod/` | A test-only mod that drives the game through a route for the lab and the harness. Never shipped |
| `scripts/` | `fetch-deps.py`, the release scripts, `lab/` (the CPU lab), `realgame/` (the real-game harness) and `tests/` |
| `deps.json` | Every external jar, pinned by sha1, and the compatibility matrix rows |
| `docs/` | Everything beyond the README, the images, and the Modrinth listing in `docs/listing/` |
| `.github/workflows/` | Build, Lint, Security, Release, Dependabot Auto-Merge and Dependabot Gradle Locks |
| `.claude/skills/` | Agent skills (`release`); Codex finds them through the `.agents/skills` link |

## Rules

The binding standards are the four files in `.claude/rules/`. Read them before changing code:

- `java-architecture.md`: fail soft, thin mixins, threads, coverage
- `java-consistency.md`: match the closest existing file
- `java-review-checklist.md`: what every change is reviewed against
- `operational-awareness.md`: releases, the jars, running the game

The ones broken most often:

- Nothing Preheat does may crash the game or change how it looks. A feature that does not fit the installed Iris is never applied, and one that throws is switched off for the session.
- Mixins only forward to plain classes. The decisions live in counted classes with tests, held to 100% line and branch coverage.
- Every Iris, Sodium or Minecraft member the code relies on is read from the class file in `libs/`, never assumed.
- Every jar is pinned in `deps.json` and every Maven version in `gradle/libs.versions.toml`, and nowhere else.
- The CPU lab never points at a hardware driver, and the real-game harness runs only on your own machine with the game closed, stopping at the first crash or driver error.
- A change to anything a player reads (the settings pages, the loading screen line, a log line in the troubleshooting table) is called out in the commit message.
- Prose is never hard-wrapped.
- No personal paths (home folders, user names, machine names) in tracked files.

## Versions and releases

The version is `mod_version` in `gradle.properties`, for example `1.0.0`, and the jars are `preheat-<version>+mc26.3.jar` and `preheat-<version>+mc26.2.jar`. Every version after the first adds a `## <version>` entry to `CHANGELOG.md` in the format in [docs/releasing.md](docs/releasing.md#the-changelog). A push to `main` whose version has no GitHub release yet publishes it, so bump the version only when a release is meant. The `release` skill walks through it.
