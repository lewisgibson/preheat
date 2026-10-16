#!/usr/bin/env bash
# The end-to-end test on real hardware: a COPY of a real instance (its mods, settings, shader pack and resource packs)
# with this build of Preheat and the lab driver mod (testmod), launched on the machine's real GPU twice:
#   1. cold: no Preheat shader cache. Loads the test world, goes Overworld > Nether > Overworld > End > Overworld,
#      leaves the world and quits.
#   2. warm: the same again, with the cache the first launch wrote.
# Then scripts/realgame/check.py asserts on both logs: every feature applied, the warm launch loaded every shader from the
# cache with nothing refused, kept dimensions came back fast, no Preheat errors, the disconnect trace finished.
#
# Developer machines only, never CI. Refuses while a Minecraft process is running.
# Stops at the first crash; it never retries.
#
# usage: scripts/realgame/run.sh            (after ./gradlew build :testmod:build and scripts/lab/setup.sh, which makes the test world)
# env:
#   SOURCE_INSTANCE   the instance to copy (mods/, config/, shaderpacks/, resourcepacks/, options.txt)   required
#   WORK_DIR          where the copy lives; on WSL a folder Windows can reach (/mnt/c/...)             required
#   MC_META, JAVA, VERSION_ID, MEMORY_MB   as for scripts/realgame/launch.sh                                   MC_META, JAVA required
#   MINECRAFT         the Minecraft line of the jar to test                                             default 26.3
#   TRIPS             dimensions to visit ("reload" reloads resources)                                default the_nether,overworld,the_end,overworld
#   TIMEOUT_S         per launch                                                                        default 900
#   PREHEAT_JAR       a Preheat jar to test instead of this build's (a before/after comparison)
#   HARNESS_JVM_EXTRA extra JVM options for both launches (e.g. -Dpreheat.debugCacheMisses=<folder>)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
: "${SOURCE_INSTANCE:?set SOURCE_INSTANCE}" "${WORK_DIR:?set WORK_DIR}" "${MC_META:?set MC_META}" "${JAVA:?set JAVA}"
MINECRAFT="${MINECRAFT:-26.3}"
TRIPS="${TRIPS:-the_nether,overworld,the_end,overworld}"
TIMEOUT_S="${TIMEOUT_S:-900}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/run/realgame/$STAMP"
mkdir -p "$OUT"

# shellcheck source=scripts/realgame/copy-instance.sh
. "$HERE/copy-instance.sh"

run() {
  local label="$1"
  rm -rf "$G/saves/realgame" && cp -r "$WORLD" "$G/saves/realgame"
  rm -f "$G/logs/latest.log" "$G"/hs_err_pid*.log "$G/testmod-results.csv"
  echo "launch $label"
  mute
  set +e
  # Through the title screen like a player (not quick play), so the startup preparation is part of the test.
  GAME_DIR="$G" \
    JVM_EXTRA="-Dtestmod.openWorld=realgame -Dtestmod.arm=$label -Dtestmod.hideWindow=true -Dtestmod.leaveFirst=true -Dtestmod.trips=$TRIPS -Dtestmod.settleMs=10000 -Dtestmod.dwellMs=12000 ${HARNESS_JVM_EXTRA:-}" \
    timeout "$TIMEOUT_S" "$HERE/launch.sh"
  local status=$?
  set -e
  cp "$G/logs/latest.log" "$OUT/$label.log" 2>/dev/null || true
  cp "$G/preheat-scripts/realgame/stdout.log" "$OUT/$label-stdout.log" 2>/dev/null || true
  cp "$G"/hs_err_pid*.log "$OUT/" 2>/dev/null || true
  cp "$G/testmod-results.csv" "$OUT/$label-trips.csv" 2>/dev/null || true
  if [ "$status" != 0 ] || ls "$G"/hs_err_pid*.log >/dev/null 2>&1 || ! grep -q 'IRISLAB done' "$OUT/$label.log" 2>/dev/null; then
    echo "STOP: the $label launch did not finish cleanly (exit $status); see $OUT. Not retrying." | tee "$OUT/summary.txt"
    exit 3
  fi
}
run cold
run warm
python3 "$HERE/check.py" "$OUT/cold.log" "$OUT/warm.log" | tee "$OUT/summary.txt"
