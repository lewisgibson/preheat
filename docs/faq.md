# FAQ

## Shader appearance and saved files

Preheat is intended to preserve how your shaders look. It checks saved files before using them and prepares fresh shaders when a file fails validation. An incompatible feature switches off and logs the reason.

These checks reduce the risk from damaged saved files, but they cannot guarantee that every graphics driver behaves correctly. See [the shader cache](shader-cache.md#validation-and-recovery). If the picture changes with Preheat enabled, follow [troubleshooting](troubleshooting.md#shaders-look-wrong-after-loading).

## Video memory use

Keeping a dimension ready retains the shader pack's screen and shadow images for it. The amount depends on your shader pack and resolution. Complementary Reimagined used about 2 GB per dimension on the test machine.

When your graphics card reports that memory is low, Preheat releases inactive dimensions, with modded dimensions first and the least recently used first within each group. It may skip preparing a dimension at startup. Drivers that do not report usable memory figures receive a limit on retained modded dimensions.

You can untick dimensions you rarely visit. The [measurements](performance.md) describe the test machine.

## Disk space

A shader pack usually needs a few tens of MB. The cache has a 4 GB maintenance limit and deletes the saved shaders used longest ago when it exceeds that limit.

## Startup time

The first launch with a new shader pack can take longer because Preheat prepares the Nether and the End during loading. Later launches use the saved shaders.

The result depends on your graphics driver's own cache. On the measured system, Preheat added some startup time with a warm driver cache and reduced it with a cold driver cache. See [performance](performance.md) for the figures.

## Playing without shaders

Preheat has no shader work to do without an active pack. Choose a shader pack in Iris to use it.

## Servers

Install Preheat in your client. It needs no server installation and does not change what the server sees.

## Dimensions added by mods

A modded dimension with separate shaders is normally prepared on your first visit and kept ready afterwards while memory allows. Dimensions that share the Overworld's or End's shaders follow that dimension's setting.

See [compatibility](compatibility.md#modded-dimensions) for testing limits and [configuration](configuration.md#configpreheatjson) for modpack settings.

## Switching Preheat off

Untick every dimension and **Modded Dimensions** in **Options > Video Settings > Preheat**. Restart when comparing behaviour for a bug report.

## A slower launch after an update

Changing Iris, another mod or your graphics driver can invalidate saved work. Preheat then prepares the affected shaders again. This prevents it reusing shaders prepared for a different setup.

## Removing Preheat

Close the game and delete the Preheat jar from your `mods` folder. You can also delete `config/preheat.json` and the `preheat` folder in your game folder.

If you chose a custom cache folder, delete its `preheat-shader-cache` subfolder too if you no longer need it.
