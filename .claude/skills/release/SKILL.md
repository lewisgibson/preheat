---
name: release
description: Cut a new Preheat release. Use when asked to release, publish or ship a new version, bump the version, or write a changelog entry.
---

# Releasing Preheat

A release is a push to `main` that changes the version. The Release workflow does the rest. `docs/releasing.md` is the full reference; this is the order to do it in.

## 1. Pick the version

Read the current one with `./gradlew -q printVersion`. Bump `mod_version` in `gradle.properties`:

- PATCH for fixes and newly checked Iris, Sodium or Distant Horizons versions,
- MINOR for a new feature or setting,
- MAJOR for a change that breaks existing settings files or caches.

The `+mc` part of each jar name comes from the build and is never typed.

## 2. Write the changelog entry

Add `## <version>` at the top of `CHANGELOG.md` with one paragraph:

- Start with "This release" and list what changed as a comma list with "and" before the last item.
- Name every checked version that moved in `deps.json`'s matrix with both sides, "Sodium 0.9.2 to 0.9.3 on Fabric 26.3", or "Iris 1.11.8 on Fabric 26.3" for one added or removed. `git diff <previous tag> -- deps.json` shows them.
- No colon, and no full stop at the end. A short second sentence is allowed for a note.
- Only what a player notices. No refactors, tests or tooling.

Check it with `python3 scripts/changelog.py check`.

## 3. Prove it

```
python3 scripts/fetch-deps.py
./gradlew build
make test-scripts
make lint
```

A change to rendering, the loading screen or the settings also gets a CPU lab run with its screenshots looked at ([docs/testing.md](../../../docs/testing.md)). Fix anything that fails before pushing.

## 4. Push and watch

Commit and push to `main`, then follow the Release workflow with `gh run watch`. When it finishes, check:

- `gh release view v<version>` shows both jars, both `.sha256` files and the changelog entry under "What's changed",
- the Modrinth step says it uploaded `mc26.3-<version>` and `mc26.2-<version>`, or that its token is not set.

If the notes step fails on the changelog, the message names what is wrong or which moved version is missing. Fix the entry and push again; nothing was published.
