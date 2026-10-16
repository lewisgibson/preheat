#!/usr/bin/env bash
# The benchmark on real hardware: how long a launch takes to reach the title screen, how long Preheat's startup builds
# take and where their time goes, and how long each portal trip freezes the game, without Preheat and with it, with the
# graphics driver's own shader cache cold and warm.
#
# Conditions, each "<preheat>-<driver>", interleaved ROUNDS times so a slow minute on the machine lands on all of them:
#   stock-*   without Preheat (Iris builds each dimension on its first visit)
#   cold-*    Preheat with its cache deleted before the launch
#   warm-*    Preheat with the cache one unmeasured launch filled
#   *-dwarm   the driver's cache as the machine has it, which after the first launch is warm
#   *-dcold   a cold driver cache, for this launch only: NVIDIA's OpenGL driver keeps a cache per application,
#             keyed by the executable's file name (a copy of the runtime at another path with the same javaw.exe
#             shared the warm entry; a renamed one got its own), so the game runs from a fresh copy of the Java
#             runtime with javaw.exe renamed to a name the driver has never seen. Right after the launch the copy is
#             deleted, and so are the driver cache files that appeared during it (and only those), so no other
#             game's cache is ever touched; their names and sizes go in driver-cache-created.txt, the proof it ran
#             cold.
#             Never redirect the driver's cache through the game's environment: that crashed NVIDIA's driver.
#
# Every launch goes through the title screen like a player; with TRIPS set (the default) it then opens the test world
# and goes through the portals, otherwise it quits at the title screen. The result is
# run/realgame/bench-<time>/: one log and trips file per launch, bench.csv, and summary.txt with the median and the
# range of each number per condition (scripts/realgame/bench.py).
#
# Developer machines only, never CI. Refuses while a Minecraft process is running and
# stops at the first launch that does not finish cleanly; it never retries.
#
# env: as scripts/realgame/run.sh (SOURCE_INSTANCE, WORK_DIR, MC_META, JAVA, MEMORY_MB, PREHEAT_JAR), and
#   ROUNDS        launches per condition                                                            default 3
#   CONDITIONS    comma-separated conditions                          default all six: stock,cold,warm by dcold,dwarm
#   TRIPS         the portal route; empty quits at the title screen   default the_nether,overworld,the_end,overworld,the_nether,overworld
#   GLCACHE       the driver's cache folder (Windows)                 default <LOCALAPPDATA>/NVIDIA/GLCache
#   TIMEOUT_S     per launch                                                                        default 900
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
: "${SOURCE_INSTANCE:?set SOURCE_INSTANCE}" "${WORK_DIR:?set WORK_DIR}" "${MC_META:?set MC_META}" "${JAVA:?set JAVA}"
MINECRAFT="${MINECRAFT:-26.3}"
ROUNDS="${ROUNDS:-3}"
CONDITIONS="${CONDITIONS:-stock-dcold,cold-dcold,warm-dcold,stock-dwarm,cold-dwarm,warm-dwarm}"
TRIPS="${TRIPS-the_nether,overworld,the_end,overworld,the_nether,overworld}"
TIMEOUT_S="${TIMEOUT_S:-900}"
OUT="$ROOT/run/realgame/bench-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

# shellcheck source=scripts/realgame/copy-instance.sh
. "$HERE/copy-instance.sh"

PREHEAT_WARM="$WORK_DIR/preheat-cache-warm"
CACHE="$G/preheat/shader-cache"
HELD="$WORK_DIR/held-back"
JRE="$(dirname "$(dirname "$JAVA")")"
if [ -z "${GLCACHE:-}" ] && command -v cmd.exe >/dev/null 2>&1; then
  GLCACHE="$(wslpath "$(cmd.exe /c 'echo %LOCALAPPDATA%' 2>/dev/null | tr -d '\r')")/NVIDIA/GLCache"
fi
GLCACHE="${GLCACHE:-}"

glcache_files() {
  if [ -d "$GLCACHE" ]; then find "$GLCACHE" -type f | sort; fi
}

launch() {
  local label="$1" java="$2"
  rm -f "$G/logs/latest.log" "$G"/hs_err_pid*.log "$G/testmod-results.csv"
  rm -rf "$G/saves/realgame" && cp -r "$WORLD" "$G/saves/realgame"
  local extra="-Dtestmod.quitAtTitle=true"
  if [ -n "$TRIPS" ]; then
    extra="-Dtestmod.openWorld=realgame -Dtestmod.leaveFirst=true -Dtestmod.trips=$TRIPS -Dtestmod.settleMs=10000 -Dtestmod.dwellMs=10000"
  fi
  echo "launch $label"
  mute
  set +e
  GAME_DIR="$G" JAVA="$java" JVM_EXTRA="$extra -Dtestmod.arm=$label -Dtestmod.hideWindow=true ${HARNESS_JVM_EXTRA:-}" \
    timeout "$TIMEOUT_S" "$HERE/launch.sh"
  local status=$?
  set -e
  cp "$G/logs/latest.log" "$OUT/$label.log" 2>/dev/null || true
  cp "$G/testmod-results.csv" "$OUT/$label-trips.csv" 2>/dev/null || true
  cp "$G"/hs_err_pid*.log "$OUT/" 2>/dev/null || true
  if [ "$status" != 0 ] || ls "$G"/hs_err_pid*.log >/dev/null 2>&1 || ! grep -q 'IRISLAB done' "$OUT/$label.log" 2>/dev/null; then
    echo "STOP: launch $label did not finish cleanly (exit $status); see $OUT. Not retrying." | tee "$OUT/summary.txt"
    exit 3
  fi
}

# A launch with a driver cache the driver has never seen: a fresh copy of the runtime, removed afterwards with the
# driver cache files that appeared while it ran.
cold_launch() {
  local label="$1"
  local copy="$WORK_DIR/jre-cold-$label-$$"
  local exe="preheatcold${label//[^a-z0-9]/}$$.exe"
  rm -rf "$copy" && cp -r "$JRE" "$copy" && mv "$copy/bin/$(basename "$JAVA")" "$copy/bin/$exe"
  local before after
  before="$(glcache_files)"
  set +e
  ( launch "$label" "$copy/bin/$exe" )
  local status=$?
  set -e
  after="$(glcache_files)"
  comm -13 <(printf '%s\n' "$before") <(printf '%s\n' "$after") | while IFS= read -r f; do
    [ -n "$f" ] || continue
    echo "$label $(stat -c %s "$f") $(basename "$f")" >> "$OUT/driver-cache-created.txt"
    rm -f "$f"
  done
  rm -rf "$copy"
  [ "$status" = 0 ] || exit "$status"
  grep -q "^$label " "$OUT/driver-cache-created.txt" 2>/dev/null \
    || { echo "STOP: $label made no new driver cache entry, so it did not run cold; see $OUT" | tee "$OUT/summary.txt"; exit 4; }
}

# Priming: one launch fills Preheat's cache (and the driver's own, if it was not already).
# (copy-instance.sh has just put this run's Preheat in mods, so anything a stopped run held back is stale.)
rm -rf "$PREHEAT_WARM" "$CACHE" "$HELD"
launch prime "$JAVA"
cp -r "$CACHE" "$PREHEAT_WARM"

IFS=',' read -r -a conds <<< "$CONDITIONS"
for round in $(seq 1 "$ROUNDS"); do
  for c in "${conds[@]}"; do
    rm -rf "$CACHE"
    case "$c" in
      stock-*) mkdir -p "$HELD" && mv "$G"/mods/preheat-*.jar "$HELD/" ;;
      cold-*) ;;
      warm-*) mkdir -p "$(dirname "$CACHE")" && cp -r "$PREHEAT_WARM" "$CACHE" ;;
      *) echo "unknown condition $c"; exit 1 ;;
    esac
    case "$c" in
      *-dcold) cold_launch "$c-$round" ;;
      *-dwarm) launch "$c-$round" "$JAVA" ;;
      *) echo "unknown condition $c"; exit 1 ;;
    esac
    case "$c" in stock-*) mv "$HELD"/preheat-*.jar "$G/mods/" ;; esac
  done
done
python3 "$HERE/bench.py" "$OUT" | tee "$OUT/summary.txt"
