# Building from source

Set `JAVA_HOME` to a Java 25 JDK. Python 3 is also required. Minecraft 26.x is unobfuscated, so Preheat compiles directly against the game jars without Loom or remapping.

Run from the repository root:

```sh
python3 scripts/fetch-deps.py
./gradlew build
make test-scripts
```

The fetch script downloads the jars listed in `deps.json` into `libs/` and checks their SHA-1 hashes. Run it again after changing `deps.json`.

`./gradlew build` checks Java formatting, runs Error Prone and NullAway, executes the tests and compatibility matrix, and enforces 100% line and branch coverage under JaCoCo before producing both jars. `make test-scripts` tests every Python script separately, at 100% line and branch coverage. `make hooks` installs a pre-commit hook that runs the checks a commit needs.

## Outputs

With the current local version, the release jars are:

- `build/libs/preheat-1.0.0+mc26.3.jar`
- `build/libs/preheat-1.0.0+mc26.2.jar`

Each jar loads on Fabric and NeoForge. The 26.2 jar is derived from the 26.3 build by mapping the OpenGL packages renamed in 26.3 back to their earlier names.

| Report               | Path                                        |
| -------------------- | ------------------------------------------- |
| Unit tests           | `build/reports/tests/test/index.html`       |
| Coverage             | `build/reports/jacoco/test/html/index.html` |
| Compatibility matrix | `build/reports/compat/matrix.md`            |

`mod_version` in `gradle.properties` is the version, for local builds and releases alike. See [releasing](releasing.md).

## Formatting and dependencies

Format Java with:

```sh
./gradlew spotlessApply
```

Maven dependency versions are in `gradle/libs.versions.toml`. Dependency locking and verification are enabled. After changing a dependency, update both:

```sh
./gradlew dependencies --write-locks
./gradlew --write-verification-metadata sha256 build
```

## Game test driver

Build the test-only driver separately:

```sh
./gradlew :testmod:build
```

It is used by `scripts/lab/` and `scripts/realgame/` and is never included in a release.

See [testing](testing.md) for game checks and [releasing](releasing.md) for publishing.
