#!/usr/bin/env bash
# The NeoForge lab: the same CPU-only check as scripts/lab/loading-screen.sh, on NeoForge 26.3 (Mesa llvmpipe on Xvfb :97).
# The first run installs NeoForge into run/lab/neoforge/ (gitignored) with its own installer and fetches the libraries it
# names, each checked against the sha1 its version file gives; later runs reuse it. The game folder gets Iris and Sodium
# for NeoForge from libs/matrix, this build of Preheat, testmod, and the Fabric lab's shader pack and settings.
# Screenshots and the log land in run/lab/results/<label>/.
# usage: scripts/lab/neoforge.sh <label>   env: OPEN_SETTINGS=true|video (open Preheat's screen or the video settings), HOLD=<s>
set -euo pipefail
LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$LAB/../.." && pwd)"
RUN="$ROOT/run/lab"
LABEL="$1"
NEOFORGE=26.3.0.58-beta
JAVA="${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}"
N="$RUN/neoforge"
G="$N/game"
OUT="$RUN/results/$LABEL"
mkdir -p "$N/mc" "$G/mods" "$G/config" "$OUT"

if [ ! -f "$N/classpath.txt" ]; then
  curl -sfL -o "$N/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEOFORGE/neoforge-$NEOFORGE-installer.jar"
  echo '{"profiles":{}}' > "$N/mc/launcher_profiles.json"
  "$JAVA" -jar "$N/installer.jar" --install-client "$N/mc" >/dev/null
  python3 - "$N" "$NEOFORGE" <<'PY'
import hashlib, json, os, sys, urllib.request
n, neo = sys.argv[1:3]
cp = []
for v in (f"neoforge-{neo}", "26.3"):
    j = json.load(open(f"{n}/mc/versions/{v}/{v}.json"))
    for lib in j["libraries"]:
        a = lib.get("downloads", {}).get("artifact")
        if not a:
            continue
        ok = True
        for r in lib.get("rules", []):
            if r.get("os", {}).get("name") in (None, "linux"):
                ok = r["action"] == "allow"
            elif r["action"] == "allow":
                ok = False
        if not ok or (":natives-" in lib["name"] and "natives-linux" not in lib["name"]):
            continue
        dst = f"{n}/mc/libraries/{a['path']}"
        if a.get("url") and not (os.path.exists(dst) and hashlib.sha1(open(dst, "rb").read()).hexdigest() == a["sha1"]):
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            urllib.request.urlretrieve(a["url"], dst)
            assert hashlib.sha1(open(dst, "rb").read()).hexdigest() == a["sha1"], dst
        if os.path.exists(dst) and dst not in cp:
            cp.append(dst)
cp.append(f"{n}/mc/versions/26.3/26.3.jar")
open(f"{n}/classpath.txt", "w").write(":".join(cp))
PY
  mkdir -p "$N/natives"
  for j in "$RUN"/runtime/natives/*.jar; do
    (cd "$N/natives" && unzip -o -q "$j" '*.so' 2>/dev/null) || true
  done
fi

rm -f "$G"/mods/*.jar
cp "$ROOT"/libs/matrix/26.3-neoforge-iris1.11.7/iris-neoforge-*.jar "$ROOT"/libs/matrix/26.3-neoforge-iris1.11.7/sodium-neoforge-*.jar "$G/mods/"
cp "$ROOT"/build/libs/preheat-*+mc26.3.jar "$ROOT"/testmod/build/libs/testmod-*.jar "$G/mods/"
rm -rf "$G/shaderpacks" && cp -r "$RUN/game/shaderpacks" "$G/"
cp "$RUN/game/options.txt" "$G/"
cp "$RUN/game/config/iris.properties" "$G/config/"
# NeoForge's early loading window asks for an sRGB visual, which Xvfb's GLX does not have.
printf 'earlyWindowControl = false\n' > "$G/config/fml.toml"
rm -f "$G/logs/latest.log"
rm -f "$OUT"/*.png

XVFB=""
pgrep -f "^Xvfb :97 " >/dev/null || { Xvfb :97 -screen 0 1280x720x24 >/dev/null 2>&1 & XVFB=$!; sleep 2; }
unset WAYLAND_DISPLAY
export DISPLAY=:97 SDL_VIDEO_DRIVER=x11 LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=8
cd "$G"
nice -n 19 "$JAVA" -Xms1G -Xmx"${LAB_HEAP:-3G}" -XX:+UseZGC -Dtestmod.mesa=true -Dtestmod.openSettings="${OPEN_SETTINGS:-false}" \
  -Djava.net.preferIPv6Addresses=system -DlibraryDirectory="$N/mc/libraries" \
  --add-opens java.base/java.lang.invoke=ALL-UNNAMED --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming \
  --enable-native-access=ALL-UNNAMED -Djava.library.path="$N/natives" -Dorg.lwjgl.librarypath="$N/natives" \
  -cp "$(cat "$N/classpath.txt")" net.neoforged.fml.startup.Client \
  --username PreheatLab --version "neoforge-$NEOFORGE" --gameDir "$G" --assetsDir "$RUN/runtime/assets" \
  --assetIndex "$(cat "$RUN/runtime/asset-index")" --uuid 00000000000040008000000000000001 --accessToken 0 \
  --versionType release --width 1280 --height 720 \
  --fml.neoForgeVersion "$NEOFORGE" --fml.mcVersion 26.3 --fml.neoFormVersion 1 > "$OUT/stdout.log" 2>&1 &
GAME=$!
# The game and its display never outlive this script.
trap 'kill -KILL "$GAME" 2>/dev/null || true; [ -n "$XVFB" ] && kill "$XVFB" 2>/dev/null || true' EXIT
start=$SECONDS
until grep -qE "IRISLAB title screen|Game crashed" "$G/logs/latest.log" 2>/dev/null || ! kill -0 "$GAME" 2>/dev/null \
    || [ $((SECONDS - start)) -gt 900 ]; do sleep 2; done
sleep "${HOLD:-10}"
import -display :97 -window root "$OUT/screen.png" 2>/dev/null || true
cp "$G/logs/latest.log" "$OUT/latest.log" 2>/dev/null || true
echo "$(grep -c PREHEAT "$OUT/latest.log" 2>/dev/null || echo 0) PREHEAT lines, $(grep -c 'PREHEAT Turned off' "$OUT/latest.log" 2>/dev/null || true) features turned off; see $OUT"
