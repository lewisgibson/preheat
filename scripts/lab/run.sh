#!/usr/bin/env bash
# One arm of the dimension-change lab: a headless client on Xvfb :95 (nothing on the desktop), software GL through
# Mesa's llvmpipe (CPU only), the copied instance's mods plus testmod, and Preheat only when the arm is not "stock". The world is reset
# from the pristine copy first, so every arm starts from the same save. Results append to run/lab/results/results.csv,
# the log and screenshots land in run/lab/results/<label>/.
# usage: run.sh <label> <stock|fixed> [extra -D options for the JVM...]
# env: COLD_CACHE=1 (delete Preheat's shader cache first), QUICKPLAY (default "--quickPlaySingleplayer labworld"; "--quickPlayMultiplayer host:port" for a server), GL (llvmpipe, the default, or d3d12), TRIPS (comma list of dimensions), JFR_STEPS (comma list of step numbers to profile), SETTLE_MS, DWELL_MS
set -euo pipefail
LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$LAB/../.." && pwd)"
RUN="$ROOT/run/lab"
LABEL="$1"; ARM="$2"; shift 2
JAVA="${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}"
# Minecraft 26.x needs Java 25; an older one fails mod loading after a minute of startup, so refuse it here.
"$JAVA" -version 2>&1 | grep -qE 'version "(2[5-9]|[3-9][0-9])' || { echo "run.sh: $JAVA is not Java 25 or later; set JAVA_HOME" >&2; exit 2; }
G="$RUN/game"
OUT="$RUN/results/$LABEL"
mkdir -p "$OUT"

# Four lab slots on this box (docs/skyblock-client.md, "Lab client"): hold one for the whole run.
SLOT=""
until [ -n "$SLOT" ]; do
  for n in 1 2 3 4; do
    exec {fd}>"/tmp/skyblock-lab.slot$n"
    if flock -n "$fd"; then SLOT=$n; break; fi
    exec {fd}>&-
  done
  [ -n "$SLOT" ] || sleep 10
done
# A measured run starts only under a 1-minute load of 28.
while [ "$(cut -d' ' -f1 /proc/loadavg | cut -d. -f1)" -ge 28 ]; do sleep 15; done
echo "slot $SLOT, load $(cut -d' ' -f1 /proc/loadavg)" > "$OUT/env.txt"

rm -f "$G"/mods/irisfixes-*.jar "$G"/mods/preheat-*.jar "$G"/mods/testmod-*.jar
cp "$ROOT"/testmod/build/libs/testmod-*.jar "$G/mods/"
[ "$ARM" = stock ] || cp "$ROOT"/build/libs/preheat-*+mc26.3.jar "$G/mods/"
rm -rf "$G/saves/labworld" && cp -r "$RUN/server/labworld" "$G/saves/labworld"
# COLD_CACHE=1 starts with no Preheat shader cache (a first launch); otherwise the previous run's stays (a warm one).
[ "${COLD_CACHE:-0}" = 1 ] && rm -rf "$G/preheat/shader-cache"
rm -f "$G"/testmod-results.csv "$G"/testmod-*.jfr "$G"/screenshots/testmod-*.png "$G/logs/latest.log"

# Its own virtual display, closed again when this script ends (an anchored match, so a caller's command line that
# merely mentions it is not taken for a running display).
XVFB=""
pgrep -f "^Xvfb :95 " >/dev/null || { Xvfb :95 -screen 0 1920x1080x24 >/dev/null 2>&1 & XVFB=$!; sleep 2; }
# SDL would otherwise pick a Wayland compositor (WSLg's, for one) and open the window on the desktop.
unset WAYLAND_DISPLAY
export DISPLAY=:95 SDL_VIDEO_DRIVER=x11
# Mesa's D3D12 driver crashes in libgallium on Sodium 0.9's chunk uploads (mapped or not), so the lab renders with
# llvmpipe: software GL on the WSL half of the CPU. The Java half of building a pipeline (the pack's preprocessing and
# the transforms) runs on the same CPU and JVM as a real game; the GL compile does not transfer and is reported apart.
case "${GL:-llvmpipe}" in
  d3d12) export GALLIUM_DRIVER=d3d12 MESA_D3D12_DEFAULT_ADAPTER_NAME=NVIDIA ;;
  *) export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=8 ;;
esac
CP="$(printf '%s:' "$RUN"/runtime/mc/*.jar "$RUN"/runtime/natives/*.jar)"
N="$RUN/runtime/extract"
mkdir -p "$N"
cd "$G"
set +e
# shellcheck disable=SC2086 # QUICKPLAY is two words on purpose
timeout 1500 nice -n 19 "$JAVA" -Dtestmod.mesa=true -Xms1G -Xmx"${LAB_HEAP:-3G}" -XX:+UseZGC -XX:StackShadowPages=32 \
  --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  -Djava.library.path="$N" -Djna.tmpdir="$N/jna" -Dorg.lwjgl.system.SharedLibraryExtractPath="$N/lwjgl" -Dio.netty.native.workdir="$N/netty" \
  -Dtestmod.arm="$LABEL" -Dtestmod.trips="${TRIPS:-the_nether,overworld,the_nether,overworld,the_end,overworld,the_nether,overworld}" \
  -Dtestmod.jfrSteps="${JFR_STEPS:-}" -Dtestmod.settleMs="${SETTLE_MS:-30000}" -Dtestmod.dwellMs="${DWELL_MS:-15000}" "$@" \
  -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username PreheatLab --version 26.3 --gameDir "$G" --assetsDir "$RUN/runtime/assets" --assetIndex "$(cat "$RUN/runtime/asset-index")" \
  --uuid 00000000000040008000000000000001 --accessToken 0 --versionType release --width 960 --height 540 \
  ${QUICKPLAY:---quickPlaySingleplayer labworld} > "$OUT/stdout.log" 2>&1 &
GAME=$!
# The game never outlives this script, however it ends (timeout passes the signal on to java).
trap 'kill -TERM "$GAME" 2>/dev/null || true; [ -n "$XVFB" ] && kill "$XVFB" 2>/dev/null || true' EXIT
wait "$GAME"
STATUS=$?
set -e
cp "$G/logs/latest.log" "$OUT/latest.log" 2>/dev/null || true
cp "$G"/testmod-*.jfr "$OUT/" 2>/dev/null || true
cp "$G"/screenshots/testmod-*.png "$OUT/" 2>/dev/null || true
if [ -f "$G/testmod-results.csv" ]; then
  [ -f "$RUN/results/results.csv" ] || head -1 "$G/testmod-results.csv" > "$RUN/results/results.csv"
  tail -n +2 "$G/testmod-results.csv" | tee -a "$RUN/results/results.csv"
fi
echo "exit $STATUS"
