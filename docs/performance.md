# Measured performance

These measurements were taken on 2026-10-09 with `scripts/realgame/bench.sh`. The system used an NVIDIA RTX 5090 with driver 616.92, a Ryzen 9 9950X3D and a 12 GB heap. The game ran Minecraft 26.3, Iris 1.11.7, Sodium 0.9.2 and Complementary Reimagined.

Each launch reached the title screen, opened a test world and followed the route Overworld, Nether, Overworld, End, Overworld, Nether, Overworld. Each figure is the median of three launches, with the range in brackets where recorded. A trip's figure is the longest frame during that trip. See [testing](testing.md#the-startup-and-portal-benchmark) for the harness.

A cold driver cache held no shaders for that launch. Each cold run used a copied Java runtime with a new executable name, which NVIDIA's per-application cache had not seen. A cold driver cache is not only a test condition: during development the driver also gave a normal launch a new cache entry, and each dimension then took about 13 s to build.

## The net comparison

Preheat moves work from the portal trips to the title screen. A slower title screen is the cost, and the trips are what it buys, so the fair comparison is the two together: the time Preheat adds to the title screen against the time it saves on the first Nether and End trips.

| Driver cache | Preheat's cache       | Title screen  | First Nether and End trips | Net per launch |
| ------------ | --------------------- | ------------- | -------------------------- | -------------- |
| Warm         | Filled                | 1.5 s slower  | 3.35 s faster              | 1.85 s saved   |
| Warm         | Empty                 | 3.9 s slower  | 3.45 s faster              | 0.45 s lost    |
| Cold         | Filled                | 11.2 s faster | 25.0 s faster              | 36.2 s saved   |
| Cold         | Empty, a first launch | 23.1 s slower | 26.5 s faster              | 3.4 s saved    |

Each row is worked out from the tables below, against the same driver cache without Preheat. Warm and filled is the normal case: every launch after the first with the same shader pack. An empty cache happens once, on the first launch with a new shader pack, new settings, a new Iris or a new graphics driver. With a warm driver that one launch costs 0.45 s more than it saves.

The net counts only the first trip to each dimension. Later trips without Preheat took about 0.75 s each and with Preheat under 0.15 s, so a session with more trips saves more than the table shows.

## The first trip with a filled cache

With a cold driver cache, the first Nether trip takes 0.82 s and the first End trip 0.90 s when Preheat's cache is filled, against 0.11 s each when it is empty. That is still far quicker than the 12.3 s and 14.4 s without Preheat, but slower than it should be.

The cause is in the driver. A shader restored from a saved binary is finished by NVIDIA's driver the first time it is drawn, so that work lands on the first trip. A shader compiled from source is finished when it is linked, on the loading screen. A fix that finishes the saved shaders a dimension's first frames use while the game loads is being tested. With a warm driver cache the difference is within the noise of the measurements.

## Driver cache cold

| Condition                 | Title screen          | First Nether trip     | First End trip        | Later trips |
| ------------------------- | --------------------- | --------------------- | --------------------- | ----------- |
| Without Preheat           | 29.0 s (28.5 to 32.7) | 12.3 s (11.9 to 13.9) | 14.4 s (13.4 to 14.9) | 0.78 s      |
| Preheat, its cache empty  | 52.1 s (51.7 to 62.5) | 0.11 s                | 0.11 s                | 0.06 s      |
| Preheat, its cache filled | 17.8 s (17.6 to 19.0) | 0.82 s                | 0.90 s                | 0.07 s      |

## Driver cache warm

| Condition                 | Title screen          | First Nether trip  | First End trip     | Later trips |
| ------------------------- | --------------------- | ------------------ | ------------------ | ----------- |
| Without Preheat           | 13.7 s (13.4 to 14.8) | 2.1 s (1.7 to 2.2) | 1.6 s (1.4 to 1.8) | 0.75 s      |
| Preheat, its cache empty  | 17.6 s (16.9 to 18.4) | 0.19 s             | 0.06 s             | 0.09 s      |
| Preheat, its cache filled | 15.2 s (15.1 to 16.4) | 0.25 s             | 0.10 s             | 0.12 s      |

## Where the time goes

With a cold driver cache and an empty Preheat cache, the driver's linking took 32 of the 37 s spent on the three startup builds. Loading Preheat's saved shaders cut the startup builds from 37.5 s to 2.7 s.

With a warm driver cache, the 1.5 s Preheat adds to the title screen is mostly preparing the Nether and the End, about 0.9 s each. Saved shaders and patched source cut the startup builds from 4.2 s to 2.1 s. With a warm driver, loading a saved binary takes about as long as the driver compiling the shader from its own cache, so the saving there comes from the patched source.

These results describe this hardware, shader pack and route. Logs and per-launch numbers are kept on the test machine under `run/realgame/`, with benchmark runs in `run/realgame/bench-<time>/`.
