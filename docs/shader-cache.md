# The shader cache

Preheat stores linked shader programs and Iris's patched source. Both avoid repeating work on later builds, but they have different keys and validation steps.

## Where it is

The default cache is `<game folder>/preheat/shader-cache/`.

Use **Change Folder** on Preheat's screen to choose another location. Preheat creates a `preheat-shader-cache` subfolder there and marks it with `.preheat-cache`. It manages its own entries inside that folder rather than treating the selected parent as disposable.

Cache maintenance does not follow links or junctions. A folder lock coordinates maintenance between running games that share a location. If another game holds the lock, this game does not retire or tidy generations there.

If the selected folder cannot be used, Preheat falls back to the default and displays the reason. **Open Folder** opens the active cache in the system file manager.

## Deleting or moving it

**Delete Cache** asks for confirmation and removes Preheat's saved entries. The next builds compile and save them again. Deletion also clears the crash guard's paused state.

The cache can also be deleted while the game is closed.

When changing folders, **Move Them** transfers the saved entries. **Start Fresh** selects the new location and leaves the existing cache behind. Cache operations coordinate with outstanding work before changing folders.

## Stored files

Each generation contains two entry types:

| Extension | Contents                                            |
| --------- | --------------------------------------------------- |
| `.bin`    | A linked program binary and its validation metadata |
| `.src`    | Iris's patched source, compressed with deflate      |

A generation groups entries that share the GPU, driver and relevant software versions. On the first save for a new generation, old generations are retired when this process can perform maintenance.

Shader packs and their settings share a generation. Changing packs or moving a setting does not empty the whole cache. Returning to a previous configuration can reuse its entries if they remain on disk.

The cache has a fixed 4 GB maintenance limit. When it exceeds the limit, entries used longest ago are removed first. A single shader pack usually occupies tens of MB.

## Program keys

A program's key includes the GPU, driver and GLSL version strings, the Iris and Preheat versions, the program type, attribute bindings and final source. The source already reflects the shader pack, its settings and the dimension.

A change to these inputs produces a miss rather than loading an entry prepared for a different setup.

## Patched-source keys

The source key contains everything Iris's own in-memory patch cache compares, including stage sources and every field of the patch parameters read by reflection. It also includes the program name, device depth range, Iris's debug-printing state and every installed mod with its version.

Adding, removing or updating a mod therefore causes patching to run again. `PatchFingerprint` checks the Iris implementation whose behaviour the cache relies on. An unknown implementation disables saved patches for that Iris while leaving compatible binary caching available.

## Read-ahead

During loading, background workers read the most recently used entries from the newest generation and validate them. The read-ahead limit is 256 MB, counting decompressed source. One pack typically involves 30 to 40 MB of files.

Startup builds consume these entries from memory. The read-ahead results are released after startup preparation.

## Validation and recovery

Some drivers can crash when given a damaged program binary. Preheat checks entries before passing them to the driver and checks the restored program afterwards.

| Check | Failure behaviour |
| --- | --- |
| File length and CRC-32C | Delete the entry and compile normally. Patched-source checksums cover the uncompressed text. |
| Temporary-file write followed by rename | An incomplete or damaged entry fails validation on a later read. |
| Driver acceptance, OpenGL error state and link status | Reject the loaded program, delete the entry and compile normally. |
| Stored fingerprint of attributes and uniforms | Reject a restored program whose interface differs from the compiled original. |
| Three driver refusals in a session | Stop loading cached binaries until the next launch. |
| Unexpected cache error | Disable the affected cache feature for the session and let Iris compile. |
| No supported program binary formats | Leave the cache disabled on that driver. |

Reading and validating one dimension's 10 to 12 MB cache took 16 to 19 ms in testing.

Before loading cached programs, the crash guard records the generation being used. If the process stops during that work, the next launch deletes that generation and prepares it again. Two consecutive stops on the same generation pause the cache until it is deleted. A clean load clears the strike. Closing the game through the launcher during a load can count as a stop.

These checks detect corruption and incompatible results. CRC-32C is not an authenticity check, and a validated file is not a guarantee against a graphics-driver defect.

### Comparing saved patches

This Java option makes Iris patch the sources again and compares them with the saved results:

```text
-Dpreheat.verifyPatchCache=true
```

The log reports `PREHEAT Patch cache check`. In the lab, all saved patches for the tested pack matched Iris's output. A mismatch should be reported with the log.

See [troubleshooting](troubleshooting.md) for player-facing messages and [SECURITY.md](../SECURITY.md) for private vulnerability reporting.
