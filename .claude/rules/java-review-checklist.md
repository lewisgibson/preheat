# Review checklist

Every change is reviewed against this list before it is committed.

## Correctness

- [ ] Every Iris, Sodium or Minecraft member the change relies on was read from the class file (`javap -p -c` against `libs/`), not assumed.
- [ ] A new mixin target or helper call is covered by its feature's shape check, and `CompatibilityMatrixTest` still passes for every row of `deps.json`. If what applies where changed, `src/test/resources/compat-expected.txt` and `docs/compatibility.md` changed with it.
- [ ] Every path that can throw ends in `Features.fail` or a logged, handled failure. Nothing reaches the game as an exception.
- [ ] OpenGL and Iris calls are on the render thread; disk work is not.
- [ ] Anything saved to disk is checked (length, CRC-32C, the driver's own checks) before it reaches the driver, and a failed check falls back to building as Iris does.
- [ ] A change to what the cache key holds makes old entries miss rather than load something stale.

## Tests

- [ ] `./gradlew build` is green: formatting, Error Prone, NullAway, the tests and 100% line and branch coverage.
- [ ] A bug fix has a test that fails without it.
- [ ] A change that touches rendering, the loading screen or the settings was run in the CPU lab ([docs/testing.md](../../docs/testing.md)) and its screenshots were looked at.

## Docs

- [ ] Anything a player would notice is in the README or `docs/`, in plain words, and in `CHANGELOG.md`.
- [ ] A new setting is in `docs/configuration.md`; a new log line is in `docs/troubleshooting.md`.
- [ ] Links between docs still resolve.
