# Java architecture

How Preheat is put together, and the invariants every change keeps. The full description is [docs/architecture.md](../../docs/architecture.md).

## Fail soft

Nothing Preheat does may crash the game or change how it looks.

- **A feature that does not fit is never applied.** Before any mixin is applied, `FeatureCheck` reads the installed Iris, Sodium and Minecraft class files and checks every member each `Feature` relies on. A feature with one missing target is skipped whole, and the log names the member.
- **A feature that throws is switched off.** Code a mixin calls catches what it can throw and calls `Features.fail(feature, error)`, which logs once and turns that feature off for the session. The game then carries on as if Preheat were not installed for that feature.
- **Iris behaviour Preheat depends on is fingerprinted.** `PipelineFingerprint` and `PatchFingerprint` hash the bytecode of the Iris methods whose behaviour (not only shape) Preheat relies on. An Iris whose fingerprint is unknown keeps the feature off until the new code has been read and the fingerprint added.

## Mixins are thin

- Every mixin belongs to exactly one `Feature` (`feature/Feature.java`) and is listed in `preheat.mixins.json`; `FeatureTest` fails otherwise.
- A mixin only forwards to a plain class (`keep/KeptPipelines`, `cache/ProgramBinaries`, `progress/...`). The decisions live in that class, behind small interfaces where they need OpenGL, Iris or Minecraft (`cache/ProgramGl`, `KeptPipelines.Host`), so they run in unit tests.
- Handlers are named `preheat$...`, inject rather than overwrite, and use `require = 0`: a target the shape check passed but Mixin could not match is reported by `InjectionCheck`, not a crash.
- Helper classes that call Iris or Minecraft are listed in their feature's `helpers`, so the shape check covers their calls too.

## Threads

OpenGL and Iris are touched only on the render thread. Disk work runs on the single `Preheat cache writer` thread or the read-ahead pool; nothing on the render thread waits for disk except the barrier before a cache folder is moved or deleted. A class shared between threads says which fields are confined and which are published, and publishes with `volatile` or a final field.

## Coverage

JaCoCo gates the build at 100% of lines and branches. The only classes left out are adapters that cannot run outside the game: mixins, the loader entry points, the screens, and the one-line OpenGL and Iris adapters. Each is listed in `build.gradle.kts` with its reason. New logic goes into a counted class with a test, never into an adapter.
