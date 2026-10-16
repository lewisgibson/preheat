# Java consistency

Match the closest existing file exactly. A new class looks like its neighbours: the same package layout, naming, logging and tests.

- **Java 25 and Gradle.** Every plugin and library version is in `gradle/libs.versions.toml`. Dependency locking and verification are on: after changing a dependency run `./gradlew dependencies --write-locks` and `./gradlew --write-verification-metadata sha256 build`.
- **Jars the build reads from outside Maven** (Minecraft, Iris, Sodium, Distant Horizons and the neighbouring mods the tests check) are in `deps.json`, pinned by sha1, and fetched by `scripts/fetch-deps.py` into `libs/`. Never copy a jar into `libs/` by hand.
- **Formatting is Spotless** (palantir-java-format). Run `./gradlew spotlessApply`; never format against it by hand. Markdown, YAML and JSON are formatted by Prettier (`.prettierrc.yml`).
- **Error Prone and NullAway** run on every compile, warnings as errors. Every package has a `package-info.java` with `@NullMarked`; what can be null is `@Nullable`. A suppression is as narrow as possible and carries a one-line reason.
- **Log lines** go through `Preheat.LOG`, start with `PREHEAT `, and make sense on their own in a bug report: what happened, the numbers, and the versions or folder involved. A new or changed line is added to the table in `docs/troubleshooting.md`.
- **Player-facing text** is in `assets/preheat/lang/en_us.json`, never in code. It says shaders, dimensions and MB, never programs, pipelines or OpenGL terms. British spelling.
- **Tests** are JUnit 5, one test class per class, named for the behaviour they check. They use the real jars in `libs/` where a test needs Iris or Minecraft classes (`CompatibilityMatrixTest`, `NeighbourMixinsTest`) and small fakes everywhere else.
- **Comments say why.** No TODOs, no commented-out code, no history ("used to", "now").
