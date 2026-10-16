#!/usr/bin/env bash
# The loading screen while shaders build, seen from outside: launches the lab client to the title screen (no world)
# on Xvfb with Mesa's llvmpipe, and screenshots the display every 250 ms until the title screen has been up for a
# while, then stops the game. CPU only; nothing runs on a GPU. Screenshots and the log land in run/lab/results/<label>/.
# usage: loading-screen.sh <label> [seconds]   env: COLD_CACHE=1 (delete Preheat's shader cache first), OPEN_SETTINGS=true|video (open Preheat's settings or the video settings at the title screen), HOLD=<seconds>
set -euo pipefail
LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$LAB/../.." && pwd)"
RUN="$ROOT/run/lab"
LABEL="$1"
SECONDS_MAX="${2:-240}"
JAVA="${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}"
"$JAVA" -version 2>&1 | grep -qE 'version "(2[5-9]|[3-9][0-9])' || { echo "loading-screen.sh: $JAVA is not Java 25 or later; set JAVA_HOME" >&2; exit 2; }
G="$RUN/game"
OUT="$RUN/results/$LABEL"
mkdir -p "$OUT/frames"
rm -f "$OUT"/frames/*.png

SLOT=""
until [ -n "$SLOT" ]; do
  for n in 1 2 3 4; do
    exec {fd}>"/tmp/skyblock-lab.slot$n"
    if flock -n "$fd"; then SLOT=$n; break; fi
    exec {fd}>&-
  done
  [ -n "$SLOT" ] || sleep 10
done
while [ "$(cut -d' ' -f1 /proc/loadavg | cut -d. -f1)" -ge 28 ]; do sleep 15; done

rm -f "$G"/mods/irisfixes-*.jar "$G"/mods/preheat-*.jar "$G"/mods/testmod-*.jar
cp "$ROOT"/testmod/build/libs/testmod-*.jar "$G/mods/"
cp "$ROOT"/build/libs/preheat-*+mc26.3.jar "$G/mods/"
[ "${COLD_CACHE:-0}" = 1 ] && rm -rf "$G/preheat/shader-cache"
rm -f "$G/logs/latest.log"

# Its own virtual display, closed again when this script ends (an anchored match, so a caller's command line that
# merely mentions it is not taken for a running display).
XVFB=""
pgrep -f "^Xvfb :96 " >/dev/null || { Xvfb :96 -screen 0 1280x720x24 >/dev/null 2>&1 & XVFB=$!; sleep 2; }
unset WAYLAND_DISPLAY
export DISPLAY=:96 SDL_VIDEO_DRIVER=x11 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=8
CP="$(printf '%s:' "$RUN"/runtime/mc/*.jar "$RUN"/runtime/natives/*.jar)"
N="$RUN/runtime/extract"
mkdir -p "$N"
cd "$G"
nice -n 19 "$JAVA" -Dtestmod.mesa=true -Xms1G -Xmx"${LAB_HEAP:-3G}" -XX:+UseZGC -XX:StackShadowPages=32 \
  --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  -Djava.library.path="$N" -Djna.tmpdir="$N/jna" -Dorg.lwjgl.system.SharedLibraryExtractPath="$N/lwjgl" -Dio.netty.native.workdir="$N/netty" \
  -Dtestmod.arm="$LABEL" -Dtestmod.trips="" -Dtestmod.openSettings="${OPEN_SETTINGS:-false}" \
  -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username PreheatLab --version 26.3 --gameDir "$G" --assetsDir "$RUN/runtime/assets" --assetIndex "$(cat "$RUN/runtime/asset-index")" \
  --uuid 00000000000040008000000000000001 --accessToken 0 --versionType release --width 1280 --height 720 > "$OUT/stdout.log" 2>&1 &
GAME=$!
# The game never outlives this script, however it ends.
trap 'kill -KILL "$GAME" 2>/dev/null || true; [ -n "$XVFB" ] && kill "$XVFB" 2>/dev/null || true' EXIT

i=0
titled=""
start=$SECONDS
while kill -0 "$GAME" 2>/dev/null && [ $((SECONDS - start)) -lt "$SECONDS_MAX" ]; do
  import -display :96 -window root "$OUT/frames/$(printf '%04d' $i).png" 2>/dev/null || true
  i=$((i + 1))
  # HOLD more seconds (20 by default) once the last startup build has been logged, to see the fade or a settings screen.
  if [ -z "$titled" ] && grep -q 'PREHEAT Loading screen:' "$G/logs/latest.log" 2>/dev/null; then titled=$SECONDS; fi
  if [ -n "$titled" ] && [ $((SECONDS - titled)) -ge "${HOLD:-20}" ]; then break; fi
  sleep 0.25
done
kill -TERM "$GAME" 2>/dev/null || true
sleep 5
kill -KILL "$GAME" 2>/dev/null || true
cp "$G/logs/latest.log" "$OUT/latest.log" 2>/dev/null || true
echo "$i frames in $OUT/frames; $(grep -c PREHEAT "$OUT/latest.log" 2>/dev/null || echo 0) PREHEAT lines"
