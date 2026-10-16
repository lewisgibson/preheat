#!/usr/bin/env python3
"""Uploads a release's jars to Modrinth, one Modrinth version per Minecraft line.

Reads MODRINTH_TOKEN and MODRINTH_PROJECT_ID from the environment. Without a token it does nothing and says so, which
is how publishing stays off until the token is set. A version that already exists on Modrinth is skipped, so a
re-run of a release never uploads twice. With DRY_RUN=1 it prints what it would send and sends nothing.

Usage: publish-modrinth.py <version> <changelog-file> <jar>...
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
from collections.abc import Callable, Mapping
from pathlib import Path

API = "https://api.modrinth.com/v2"
USER_AGENT = "lewisgibson/preheat release workflow (https://github.com/lewisgibson/preheat)"
# Modrinth project ids, which never change (a slug can): Iris is required, Mod Menu adds a settings button.
IRIS = "YL57xq9U"
MOD_MENU = "mOgUt4GM"
# Modrinth keeps a mod's sides on each version, as one environment; Preheat runs only in the game, never on a server.
ENVIRONMENT = "client_only"
JAR = re.compile(r"preheat-(?P<version>[^+]+)\+mc(?P<minecraft>[\d.]+)\.jar")

# (method, url, headers, body) -> parsed JSON response
Send = Callable[[str, str, Mapping[str, str], "bytes | None"], object]


def send(method: str, url: str, headers: Mapping[str, str], body: bytes | None) -> object:
    request = urllib.request.Request(url, data=body, method=method, headers={"User-Agent": USER_AGENT, **headers})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"Modrinth {method} {url} failed: {error.code} {error.read().decode(errors='replace')}")


def version_data(project: str, version: str, minecraft: str, changelog: str) -> dict[str, object]:
    return {
        "project_id": project,
        "name": f"Preheat {version} for Minecraft {minecraft}",
        "version_number": f"mc{minecraft}-{version}",
        "changelog": changelog,
        "dependencies": [
            {"project_id": IRIS, "dependency_type": "required"},
            {"project_id": MOD_MENU, "dependency_type": "optional"},
        ],
        "game_versions": [minecraft],
        "environment": ENVIRONMENT,
        "version_type": "release",
        "loaders": ["fabric", "neoforge"],
        "featured": True,
        "status": "listed",
        "file_parts": ["file"],
        "primary_file": "file",
    }


def multipart(data: Mapping[str, object], jar: Path, boundary: str) -> bytes:
    """The two parts Modrinth's create-version endpoint takes: the version as JSON, and the jar."""
    return b"".join(
        [
            f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'.encode(),
            b"Content-Type: application/json\r\n\r\n",
            json.dumps(data).encode(),
            f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'.encode(),
            b"Content-Type: application/java-archive\r\n\r\n",
            jar.read_bytes(),
            f"\r\n--{boundary}--\r\n".encode(),
        ]
    )


def existing_versions(project: str, token: str, transport: Send) -> set[str]:
    listed = transport("GET", f"{API}/project/{project}/version", {"Authorization": token}, None)
    return {str(v["version_number"]) for v in listed} if isinstance(listed, list) else set()


def minecraft_of(jar: Path, version: str) -> str:
    """The Minecraft line a release jar is for, from its name."""
    match = JAR.fullmatch(jar.name)
    if match is None or match["version"] != version:
        raise RuntimeError(f"{jar.name} is not a Preheat {version} jar")
    return match["minecraft"]


def dry_run(version: str, changelog: str, jar: Path, project: str) -> str:
    data = version_data(project, version, minecraft_of(jar, version), changelog)
    return f"would POST {API}/version with {json.dumps(data, indent=2)} and {jar.name}"


def publish(
    version: str,
    changelog: str,
    jars: list[Path],
    env: Mapping[str, str],
    transport: Send = send,
    boundary: Callable[[], str] = lambda: uuid.uuid4().hex,
) -> list[str]:
    """Uploads each jar that is not on Modrinth yet; returns what was done, one line per jar."""
    token = env.get("MODRINTH_TOKEN", "")
    if env.get("DRY_RUN") == "1":
        return [dry_run(version, changelog, jar, env.get("MODRINTH_PROJECT_ID", "")) for jar in jars]
    if not token:
        return ["MODRINTH_TOKEN is not set, so nothing was uploaded to Modrinth"]
    project = env.get("MODRINTH_PROJECT_ID", "")
    if not project:
        raise RuntimeError("MODRINTH_TOKEN is set but MODRINTH_PROJECT_ID is not")
    done = existing_versions(project, token, transport)
    report = []
    for jar in jars:
        data = version_data(project, version, minecraft_of(jar, version), changelog)
        if data["version_number"] in done:
            report.append(f"{data['version_number']} is already on Modrinth, skipped")
            continue
        mark = boundary()
        transport(
            "POST",
            f"{API}/version",
            {"Authorization": token, "Content-Type": f"multipart/form-data; boundary={mark}"},
            multipart(data, jar, mark),
        )
        report.append(f"uploaded {jar.name} as {data['version_number']}")
    return report


def main(argv: list[str], env: Mapping[str, str] = os.environ, transport: Send = send) -> int:
    if len(argv) < 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    changelog = Path(argv[2]).read_text(encoding="utf-8").strip()
    for line in publish(argv[1], changelog, [Path(j) for j in argv[3:]], env, transport):
        print(line)
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
