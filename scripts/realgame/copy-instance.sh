# shellcheck shell=bash
# Sourced by scripts/realgame/run.sh and scripts/realgame/bench.sh: refuses while a Minecraft runs, then makes the copy of the instance
# in "$WORK_DIR/game" (G) with this build of Preheat (or PREHEAT_JAR, or none for PREHEAT_JAR=none) and the lab driver
# mod. Expects ROOT, OUT, SOURCE_INSTANCE, WORK_DIR and MINECRAFT to be set.
minecraft_running() {
  if command -v tasklist.exe >/dev/null 2>&1; then
    tasklist.exe 2>/dev/null | grep -qiE '^(javaw|java)\.exe' && return 0
  fi
  pgrep -f 'net.fabricmc.loader.impl.launch.knot.KnotClient|net.minecraft.client.main.Main' >/dev/null 2>&1
}
if minecraft_running; then
  echo "A Minecraft game is running; not starting (try again when it is closed)." | tee "$OUT/summary.txt"
  exit 2
fi

jars=("$ROOT"/build/libs/preheat-*+mc"$MINECRAFT".jar)
labjars=("$ROOT"/testmod/build/libs/testmod-*.jar)
JAR="${PREHEAT_JAR:-$([ -e "${jars[0]}" ] && echo "${jars[0]}")}"
LABJAR="$([ -e "${labjars[0]}" ] && echo "${labjars[0]}")"
WORLD="${TEST_WORLD:-$ROOT/run/lab/server/labworld}"
[ -n "$JAR" ] || { echo "no build/libs/preheat-*+mc$MINECRAFT.jar: run ./gradlew build"; exit 1; }
[ -n "$LABJAR" ] || { echo "no testmod jar: run ./gradlew :testmod:build"; exit 1; }
[ -d "$WORLD" ] || { echo "no test world at $WORLD: run scripts/lab/setup.sh (or set TEST_WORLD)"; exit 1; }

# The copy: the instance's enabled mods without any Preheat or irisfixes, plus this build and the driver mod.
G="$WORK_DIR/game"
mkdir -p "$G/mods" "$G/saves"
rm -f "$G"/mods/*.jar
find "$SOURCE_INSTANCE/mods" -maxdepth 1 -name '*.jar' ! -name 'preheat-*' ! -name 'irisfixes-*' -exec cp {} "$G/mods/" \;
cp "$LABJAR" "$G/mods/"
[ "$JAR" = none ] || cp "$JAR" "$G/mods/"
for d in config shaderpacks resourcepacks; do
  rm -rf "${G:?}/$d"
  [ -d "$SOURCE_INSTANCE/$d" ] && cp -r "$SOURCE_INSTANCE/$d" "$G/$d"
done
rm -f "$G/config/preheat.json"
sed -e 's/^fullscreen:.*/fullscreen:false/' -e 's/^pauseOnLostFocus:.*/pauseOnLostFocus:false/' \
    -e 's/^inactivityFpsLimit:.*/inactivityFpsLimit:"minimized"/' "$SOURCE_INSTANCE/options.txt" > "$G/options.txt"
rm -rf "$G/preheat/shader-cache"

# The test game is silent: whoever runs it may be working at the same time. Called before every launch (the work copy's options.txt
# may have been refreshed from the instance), and never on the instance's own options.txt.
mute() {
  local o="$G/options.txt"
  if grep -q '^soundCategory_master:' "$o" 2>/dev/null; then
    sed -i 's/^soundCategory_master:.*/soundCategory_master:0.0/' "$o"
  else
    echo 'soundCategory_master:0.0' >> "$o"
  fi
}
