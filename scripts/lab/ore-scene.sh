#!/usr/bin/env bash
# A dark room of ores at night, photographed from inside, for checking emissive blocks (Complementary's glowing ores)
# with and without Preheat: the first photo is the pipeline as built, the second after a Nether trip, so a kept
# pipeline brought back. usage: scripts/lab/ore-scene.sh <label> <stock|fixed>   (COLD_CACHE=1 as for scripts/lab/run.sh)
set -euo pipefail
LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPOT="overworld@2;-37;6;-90;0"
export TRIPS="$SPOT,the_nether,$SPOT" SETTLE_MS="${SETTLE_MS:-15000}" DWELL_MS="${DWELL_MS:-12000}"
exec "$LAB/run.sh" "$1" "$2" \
  "-Dtestmod.setup=fill 0 -40 0 12 -34 12 minecraft:stone|fill 1 -39 1 11 -35 11 minecraft:air|fill 11 -39 2 11 -35 10 minecraft:diamond_ore|fill 11 -37 2 11 -37 10 minecraft:redstone_ore|fill 11 -38 3 11 -38 4 minecraft:emerald_ore|fill 11 -36 8 11 -36 9 minecraft:lapis_ore|setblock 11 -35 6 minecraft:gold_ore|time set 18000" \
  -Dtestmod.shotAfterMs=8000
