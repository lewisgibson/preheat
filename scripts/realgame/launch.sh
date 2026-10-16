#!/usr/bin/env bash
# Launches a real Minecraft instance directly (no launcher), offline, the way the Modrinth App and Prism launch it:
# the version json's libraries and client jar from a launcher's cache, the instance as the game directory. Used by the
# real-game harness (scripts/realgame/run.sh) and for a quick look at the title screen. Never joins a server: it is offline.
#
# Runs on Linux, or from WSL against a Windows install (a JAVA ending in .exe; paths are converted with wslpath).
#
# env:
#   GAME_DIR    the instance folder (holds mods/, config/, options.txt)                         required
#   MC_META     the launcher's meta folder, Modrinth App layout: versions/, libraries/, assets/ required
#   JAVA        the Java 25 executable (java or java.exe)                                         required
#   VERSION_ID  the version folder under versions/ (Fabric: <minecraft>-<loader>)                 default 26.3-0.19.5
#   MEMORY_MB   heap size                                                                         default 8192
#   QUICKPLAY   e.g. "--quickPlaySingleplayer <world>"; empty for the title screen              default empty
#   JVM_EXTRA   extra JVM options                                                                 default empty
#   DETACH=1    start it and return (Windows only); otherwise wait for the game to exit
# On Windows the game is started through WMI: no console window, and it outlives this shell.
set -euo pipefail
: "${GAME_DIR:?set GAME_DIR}" "${MC_META:?set MC_META}" "${JAVA:?set JAVA}"
VERSION_ID="${VERSION_ID:-26.3-0.19.5}"
MEMORY_MB="${MEMORY_MB:-8192}"
WIN=0
case "$JAVA" in *.exe) WIN=1 ;; esac
p() { if [ "$WIN" = 1 ]; then wslpath -w "$1"; else printf '%s' "$1"; fi; }
SEP=":"; [ "$WIN" = 1 ] && SEP=";"
OS=linux; [ "$WIN" = 1 ] && OS=windows
STATE="$GAME_DIR/preheat-realgame"
mkdir -p "$STATE/natives"

CP=$(python3 - "$MC_META" "$VERSION_ID" "$OS" <<'PY'
import json, os, sys
meta, version, osname = sys.argv[1:4]
def load(v):
    j = json.load(open(f"{meta}/versions/{v}/{v}.json"))
    return j
j = load(version)
libs = list(j.get("libraries", []))
jar_version = version
if "inheritsFrom" in j:
    parent = load(j["inheritsFrom"])
    libs = libs + parent.get("libraries", [])
    jar_version = j["inheritsFrom"]
paths, seen = [], set()
for lib in libs:
    rules = lib.get("rules")
    if rules:
        ok = False
        for r in rules:
            if r.get("os", {}).get("name") in (None, osname):
                ok = r["action"] == "allow"
        if not ok:
            continue
    parts = lib["name"].split(":")
    if len(parts) == 4 and parts[3].startswith("natives-") and parts[3] != f"natives-{osname}":
        continue
    group, name, ver = parts[:3]
    classifier = f"-{parts[3]}" if len(parts) == 4 else ""
    path = f"{meta}/libraries/{group.replace('.', '/')}/{name}/{ver}/{name}-{ver}{classifier}.jar"
    if path not in seen and os.path.exists(path):
        seen.add(path)
        paths.append(path)
client = f"{meta}/versions/{version}/{version}.jar"
if not os.path.exists(client):
    client = f"{meta}/versions/{jar_version}/{jar_version}.jar"
paths.append(client)
print("\n".join(paths))
PY
)
INDEX=$(python3 -c "import json,sys; j=json.load(open(sys.argv[1])); print(j.get('assetIndex',{}).get('id') or j.get('assets',''))" "$MC_META/versions/$VERSION_ID/$VERSION_ID.json")
MAIN=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))['mainClass'])" "$MC_META/versions/$VERSION_ID/$VERSION_ID.json")
CPW=""
while IFS= read -r f; do CPW="${CPW:+$CPW$SEP}$(p "$f")"; done <<< "$CP"

# A java argument file: the classpath is longer than a Windows command line allows. Every value is quoted.
q() { printf '"%s"' "$(printf '%s' "$1" | sed 's/\\/\\\\/g')"; }
{
  echo "-Xms2G -Xmx${MEMORY_MB}M -XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED ${JVM_EXTRA:-}"
  echo "$(q "-Djava.library.path=$(p "$STATE/natives")") $(q "-Dorg.lwjgl.system.SharedLibraryExtractPath=$(p "$STATE/natives")")"
  echo "-cp $(q "$CPW")"
  echo "$MAIN"
  echo "--username PreheatTest --version $VERSION_ID --gameDir $(q "$(p "$GAME_DIR")") --assetsDir $(q "$(p "$MC_META/assets")") --assetIndex $INDEX"
  echo "--uuid 00000000000040008000000000000002 --accessToken 0 --versionType release ${QUICKPLAY:-}"
} > "$STATE/args.txt"

if [ "$WIN" = 1 ]; then
  # Created through WMI (Win32_Process.Create): the game is a top-level Windows process with no console and no tie to
  # this shell, so no window opens, nothing lingers, and closing this shell never closes the game. javaw.exe writes
  # no console output; the game's own logs/latest.log is the log.
  cmdline="\"$(p "$JAVA")\" @\"$(p "$STATE/args.txt")\""
  ps1="$STATE/launch.ps1"
  cat > "$ps1" <<PS
\$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = '$cmdline'; CurrentDirectory = '$(p "$GAME_DIR")' }
if (\$r.ReturnValue -ne 0) { Write-Error "Win32_Process.Create returned \$(\$r.ReturnValue)"; exit 1 }
Set-Content -Path '$(p "$STATE/pid")' -Value \$r.ProcessId
if ('${DETACH:-0}' -ne '1') { Wait-Process -Id \$r.ProcessId -ErrorAction SilentlyContinue }
PS
  powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "$(p "$ps1")"
else
  cd "$GAME_DIR"
  "$JAVA" @"$STATE/args.txt" > "$STATE/stdout.log" 2>&1
fi
