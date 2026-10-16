# Releasing

Releases are automatic. Every push to `main` checks `mod_version` in `gradle.properties`, and if that version has no GitHub release yet, [`.github/workflows/release.yaml`](../.github/workflows/release.yaml) runs the full gate and publishes it. A push that keeps a released version publishes nothing. Tags do not start a release; the workflow creates the tag.

## Versions

Versions are `MAJOR.MINOR.PATCH`, for example `1.0.0`, following [SemVer](https://semver.org/). Each jar adds the Minecraft line it is for as build metadata.

| Where                      | Example                                                   |
| -------------------------- | --------------------------------------------------------- |
| `gradle.properties`        | `mod_version=1.0.0`, the only place it is set             |
| The game and the mod lists | `1.0.0`                                                   |
| Jars                       | `preheat-1.0.0+mc26.3.jar` and `preheat-1.0.0+mc26.2.jar` |
| Git tag                    | `v1.0.0`, created by the release workflow                 |
| Modrinth versions          | `mc26.3-1.0.0` and `mc26.2-1.0.0`                         |

`./gradlew -q printVersion` prints the version.

## The changelog

`CHANGELOG.md` has one section per version, newest first, except the first release, which has nothing before it to compare with. Each section is a `## <version>` heading and one paragraph:

- It starts "This release" and says what changed as a comma list, with "and" before the last item.
- A checked version that moved in `deps.json`'s compatibility matrix is named with both sides and where it was checked: "updates Sodium 0.9.2 to 0.9.3 on Fabric 26.3". One added or removed reads "adds Iris 1.11.8 on Fabric 26.3".
- It has no colon and no full stop at the end.
- A short note may follow as a second sentence, such as "Iris 1.11.8 is a beta build".
- It covers what a player notices. Refactors, tests and build tooling are left out.

```markdown
## 1.1.0

This release adds Iris 1.11.8 on Fabric 26.3 and NeoForge 26.3, updates Distant Horizons 3.3.4 to 3.3.5 on 26.3 and prepares modded dimensions at startup when they are listed
```

`scripts/changelog.py check` checks every section's format; the Lint workflow and the pre-commit hook run it. At release time, `scripts/release-notes.py` also checks that the entry names every Iris, Sodium and Distant Horizons version that moved in the matrix since the previous release, and stops the release if one is missing.

## Cutting a release

1. Bump `mod_version` in `gradle.properties`.
2. Add the matching section to `CHANGELOG.md`.
3. Run `python3 scripts/fetch-deps.py`, `./gradlew build` and `make test-scripts`, and fix anything that fails. A change to rendering, the loading screen or the settings also gets a CPU lab run ([testing](testing.md)).
4. Push to `main`.

For a version without a GitHub release, the workflow then:

1. Reads the version with `./gradlew -q printVersion`.
2. Writes the notes with `scripts/release-notes.py`: [`.github/release-body-template.md`](../.github/release-body-template.md) with the version filled in, then the changelog entry, then the commit it was built from. A missing or wrong entry stops the release here.
3. Fetches the pinned jars and runs the full gate with `./gradlew build`.
4. Writes a `.sha256` beside each jar.
5. Creates the GitHub release `v<version>` with both jars and their checksums.
6. Uploads one Modrinth version per Minecraft line with `scripts/publish-modrinth.py` when `MODRINTH_TOKEN` is set: loaders Fabric and NeoForge, client only, Iris required and Mod Menu optional.

The upload skips a version Modrinth already has, so running the workflow again is safe. Only the publish job sees the token. `DRY_RUN=1 scripts/publish-modrinth.py <version> <changelog> <jar>...` prints what would be sent.

## Setting up publishing

The repository owner does this once:

1. Create the Modrinth project from the text and images in [docs/listing/](listing/).
2. Create a Modrinth personal access token with the "Create versions" scope.
3. In Settings > Secrets and variables > Actions, add:

| Kind     | Name                  | Value                          |
| -------- | --------------------- | ------------------------------ |
| Secret   | `MODRINTH_TOKEN`      | Modrinth personal access token |
| Variable | `MODRINTH_PROJECT_ID` | Modrinth project id            |

Without the token, the workflow still publishes to GitHub and says that Modrinth was skipped. With the token but no project id, the upload fails.

## Dependency updates

Dependabot opens a pull request each week for newer Gradle dependencies, GitHub Actions and the Prettier pin, grouped into one for minor and patch updates. Those merge by themselves once Build, Lint and Security pass; major updates wait for a person. `maven-artifact` is left alone on purpose, because it moves with NeoForge. A Gradle update also needs its lock and verification files refreshed. The Dependabot Gradle Locks workflow does that on each Gradle pull request, the same way as by hand (`./gradlew --write-verification-metadata sha256 dependencies --write-locks` and `./gradlew --write-verification-metadata sha256 build`): it records the checksum Maven Central serves for each new artifact, keeps every checksum already pinned, pushes the files to the pull request and starts the checks again, which then verify them strictly. A dependency update does not change the mod's version, so it does not publish a release.

The jars in `deps.json` are not Maven dependencies, so Dependabot does not see them. A new Iris, Sodium or Distant Horizons is added there by hand, with its row in the compatibility matrix.

For the merge to wait for the checks, the repository needs "Allow auto-merge" turned on and a rule on `main` that requires the Build, Lint and Security checks. Until `main` has required checks, the workflow merges nothing and leaves each update for a person, because GitHub would otherwise merge it before the checks finish.

## CI

| Workflow                      | Checks                                                                           |
| ----------------------------- | -------------------------------------------------------------------------------- |
| `build.yaml`                  | Pinned jar fetch, the Gradle build, coverage and the compatibility matrix        |
| `lint.yaml`                   | Prettier, actionlint, shellcheck, the script tests and the changelog format      |
| `security.yaml`               | gitleaks                                                                         |
| `release.yaml`                | The version check, then the release gate, checksums, notes and publishing        |
| `dependabot-auto-merge.yml`   | Merges Dependabot's minor and patch updates once the required checks pass        |
| `dependabot-gradle-locks.yml` | Writes the Gradle locks and verification metadata on Dependabot's Gradle updates |

CI runs deterministic checks and never launches Minecraft.
