#!/usr/bin/env bash
# Build the headless lab client: a Modrinth App instance's mods, config and shader pack, the Minecraft 26.3 client and its
# libraries (with the Fabric loader) from the Modrinth App's cache, Linux natives from Mojang's library URLs, and a
# fixed-seed world generated once by the vanilla server. Everything lands in gitignored run/lab/runtime, run/lab/game and
# run/lab/server. The instance is only read, never written. CPU only: the lab renders with Mesa's llvmpipe (scripts/lab/run.sh),
# never on a GPU.
#
# usage: MODRINTH_META=<ModrinthApp>/meta MODRINTH_INSTANCE=<ModrinthApp>/profiles/<instance> scripts/lab/setup.sh
#   (on Windows the Modrinth App's folder is %APPDATA%\ModrinthApp; from WSL, the same folder under /mnt/c)
set -euo pipefail
LAB="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN="$(cd "$LAB/../.." && pwd)/run/lab"
META="${MODRINTH_META:?set MODRINTH_META to the Modrinth App meta folder}"
INSTANCE="${MODRINTH_INSTANCE:?set MODRINTH_INSTANCE to the Modrinth App instance to copy}"
# Where options.txt and the shader pack's settings come from when the instance has none yet.
SETTINGS_FROM="${SETTINGS_FROM:-$INSTANCE}"
JAVA="${JAVA:-${JAVA_HOME:+$JAVA_HOME/bin/}java}"
VERSION=26.3-0.19.5

# The runtime classpath: the client jar and every non-native library the Fabric version json names. This is the
# game's own classpath (Fabric loader, Mixin, ASM), not libs/compile, which also holds NeoForge's jars.
mkdir -p "$RUN/runtime/mc"
[ -f "$RUN/runtime/mc/minecraft-client-26.3.jar" ] || cp "$META/versions/$VERSION/$VERSION.jar" "$RUN/runtime/mc/minecraft-client-26.3.jar"
python3 - "$META" "$VERSION" "$RUN/runtime/mc" <<'PY2'
import json, os, shutil, sys
meta, version, out = sys.argv[1:4]
j = json.load(open(f"{meta}/versions/{version}/{version}.json"))
for lib in j["libraries"]:
    if lib.get("rules") or "natives" in lib:
        continue
    parts = lib["name"].split(":")
    if len(parts) != 3:
        continue
    group, name, ver = parts
    path = f"{group.replace('.', '/')}/{name}/{ver}/{name}-{ver}.jar"
    dst = os.path.join(out, os.path.basename(path))
    if not os.path.exists(dst):
        shutil.copy(os.path.join(meta, "libraries", path), dst)
PY2

# Linux natives: the natives-linux jars go on the classpath and LWJGL extracts them itself.
mkdir -p "$RUN/runtime/natives"
python3 - "$META" "$VERSION" "$RUN/runtime/natives" <<'PY'
import hashlib, json, os, sys, urllib.request
meta, version, out = sys.argv[1:4]
j = json.load(open(f"{meta}/versions/{version}/{version}.json"))
for lib in j["libraries"]:
    if not lib["name"].endswith(":natives-linux"):
        continue
    art = lib["downloads"]["artifact"]
    dst = os.path.join(out, os.path.basename(art["path"]))
    if os.path.exists(dst) and hashlib.sha1(open(dst, "rb").read()).hexdigest() == art["sha1"]:
        continue
    urllib.request.urlretrieve(art["url"], dst)
    assert hashlib.sha1(open(dst, "rb").read()).hexdigest() == art["sha1"], dst
PY

# Assets: the index and only the objects it names.
mkdir -p "$RUN/runtime/assets/indexes" "$RUN/runtime/assets/objects"
INDEX=$(python3 -c "import json; print(json.load(open('$META/versions/$VERSION/$VERSION.json'))['assetIndex']['id'])")
cp "$META/assets/indexes/$INDEX.json" "$RUN/runtime/assets/indexes/"
python3 - "$META/assets" "$RUN/runtime/assets" "$INDEX" <<'PY'
import json, os, shutil, sys
src, dst, index = sys.argv[1:4]
for obj in json.load(open(f"{dst}/indexes/{index}.json"))["objects"].values():
    h = obj["hash"]
    d = f"{dst}/objects/{h[:2]}/{h}"
    if not os.path.exists(d):
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.copy(f"{src}/objects/{h[:2]}/{h}", d)
PY
echo "$INDEX" > "$RUN/runtime/asset-index"

# The game directory: the instance's mods (enabled jars only), config and shader pack.
G="$RUN/game"
mkdir -p "$G/mods" "$G/shaderpacks" "$G/saves" "$RUN/runtime/arm-mods"
rm -f "$G"/mods/*.jar
find "$INSTANCE/mods" -maxdepth 1 -name '*.jar' -exec cp {} "$G/mods/" \;
# The mod under test is added per arm by scripts/lab/run.sh, never taken from the instance.
rm -f "$G"/mods/irisfixes-*.jar "$G"/mods/preheat-*.jar
rm -rf "$G/config" && cp -r "$INSTANCE/config" "$G/config"
cp "$INSTANCE/shaderpacks/ComplementaryReimagined_r5.9.3.zip" "$G/shaderpacks/"
for src in "$INSTANCE" "$SETTINGS_FROM"; do
  [ -f "$src/shaderpacks/ComplementaryReimagined_r5.9.3.zip.txt" ] && { cp "$src/shaderpacks/ComplementaryReimagined_r5.9.3.zip.txt" "$G/shaderpacks/"; break; }
done
grep -q '^shaderPack=' "$G/config/iris.properties" 2>/dev/null || grep -v '^#' "$SETTINGS_FROM/config/iris.properties" > "$G/config/iris.properties"
OPTIONS="$INSTANCE/options.txt"; [ -f "$OPTIONS" ] || OPTIONS="$SETTINGS_FROM/options.txt"
# The instance's options, with the window, the resource packs (512x textures only slow the lab's load) and the render
# distance (the pipeline's cost does not depend on it; Mesa's frame time does) set for a headless box.
sed -e 's/^renderDistance:.*/renderDistance:12/' -e 's/^resourcePacks:.*/resourcePacks:["vanilla"]/' \
    -e 's/^fullscreen:.*/fullscreen:false/' -e 's/^pauseOnLostFocus:.*/pauseOnLostFocus:false/' \
    -e 's/^soundDevice:.*/soundDevice:""/' "$OPTIONS" > "$G/options.txt"
# Distant Horizons generates LODs on 16 threads by default; four and a 64-chunk radius keep the lab's load sane.
[ ! -f "$G/config/DistantHorizons.toml" ] || sed -i -e 's/^\(\s*numberOfThreads = \).*/\14/' -e 's/^\(\s*lodChunkRenderDistanceRadius = \).*/\164/' "$G/config/DistantHorizons.toml"

# A fixed-seed world, generated once by the vanilla server so the client opens it straight from quick play.
if [ ! -d "$G/saves/labworld" ]; then
  S="$RUN/server"
  mkdir -p "$S"
  URL=$(python3 -c "import json; print(json.load(open('$META/versions/$VERSION/$VERSION.json'))['downloads']['server']['url'])")
  [ -f "$S/server.jar" ] || curl -sSL -o "$S/server.jar" "$URL"
  echo "eula=true" > "$S/eula.txt"
  printf 'level-name=labworld\nlevel-seed=preheat\nonline-mode=false\nserver-port=25601\nspawn-protection=0\n' > "$S/server.properties"
  # A piped "stop" errors on this version and leaves the server up, so it is stopped with SIGTERM once it is Done;
  # its shutdown hook saves the world before it exits.
  rm -rf "$S/labworld" "$S/server.log"
  (cd "$S" && exec nice -n 19 "$JAVA" -Xmx2G -jar server.jar --nogui < /dev/null > server.log 2>&1) &
  PID=$!
  end=$((SECONDS+600))
  until grep -q 'Done (' "$S/server.log" 2>/dev/null || [ $SECONDS -gt $end ]; do sleep 2; done
  kill -TERM "$PID"; wait "$PID" || true
  grep -q 'Done (' "$S/server.log" || { echo "world generation failed"; tail -20 "$S/server.log"; exit 1; }
  cp -r "$S/labworld" "$G/saves/labworld"
fi
mods=("$G"/mods/*.jar)
echo "lab ready: ${#mods[@]} mods, assets index $INDEX"
