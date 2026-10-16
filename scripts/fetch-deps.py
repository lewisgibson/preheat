#!/usr/bin/env python3
"""Download every jar in deps.json into libs/, checking each against its pinned sha1.

libs/compile/          what Preheat compiles against (Minecraft 26.3 and its libraries, both loaders, Iris, Sodium,
                       Distant Horizons, Mod Menu)
libs/matrix/<row>/     a Minecraft client jar, its LWJGL jars, and the Iris and Sodium (and Distant Horizons) jars of
                       one row of the compatibility matrix, read by CompatibilityMatrixTest to say which features
                       apply on which versions
libs/neighbours/<mc>/  other client mods players run beside Preheat, read by NeighbourMixinsTest for mixins that
                       inject into the same methods as Preheat's

Only standard-library Python, so it runs the same on a laptop and in CI. A file already present with the right hash is
not downloaded again.

Usage: fetch-deps.py [--compile-only]
"""

from __future__ import annotations

import hashlib
import json
import os
import sys
import urllib.request
from collections.abc import Callable
from typing import IO, Any

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

Open = Callable[[str], IO[bytes]]


def open_url(url: str) -> IO[bytes]:
    request = urllib.request.Request(url, headers={"User-Agent": "preheat-build (github.com/lewisgibson/preheat)"})
    return urllib.request.urlopen(request, timeout=300)


def sha1(path: str) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


class Fetcher:
    """Downloads into one libs folder, through an opener the tests can replace."""

    def __init__(self, libs: str, opener: Open = open_url) -> None:
        self.libs = libs
        self.opener = opener

    def fetch(self, url: str, want: str, dst: str) -> str:
        if os.path.exists(dst) and sha1(dst) == want:
            return dst
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        tmp = dst + ".part"
        with self.opener(url) as response, open(tmp, "wb") as f:
            for chunk in iter(lambda: response.read(1 << 20), b""):
                f.write(chunk)
        got = sha1(tmp)
        if got != want:
            os.remove(tmp)
            raise SystemExit(f"sha1 mismatch for {url}: expected {want}, got {got}")
        os.replace(tmp, dst)
        return dst

    def version_json(self, deps: dict[str, Any], mc: str) -> dict[str, Any]:
        pin = deps["minecraft"][mc]
        with open(self.fetch(pin["url"], pin["sha1"], os.path.join(self.libs, "meta", f"{mc}.json"))) as f:
            return json.load(f)

    def client(self, deps: dict[str, Any], mc: str, dst: str) -> str:
        c = self.version_json(deps, mc)["downloads"]["client"]
        return self.fetch(c["url"], c["sha1"], dst)

    def run(self, deps: dict[str, Any], only_compile: bool) -> str:
        out = os.path.join(self.libs, "compile")
        mc = deps["compile"]["minecraft"]
        self.client(deps, mc, os.path.join(out, f"minecraft-client-{mc}.jar"))
        # Every library of that version that is a plain jar (natives and OS-specific entries are not needed).
        for lib in self.version_json(deps, mc)["libraries"]:
            art = lib.get("downloads", {}).get("artifact")
            if art and not lib.get("rules") and ":natives-" not in lib["name"]:
                self.fetch(art["url"], art["sha1"], os.path.join(out, os.path.basename(art["path"])))
        for jar in deps["compile"]["jars"]:
            self.fetch(jar["url"], jar["sha1"], os.path.join(out, jar["file"]))
        if only_compile:
            return f"libs ready: {len(os.listdir(out))} compile jars"

        for row in deps["matrix"]:
            d = os.path.join(self.libs, "matrix", f"{row['minecraft']}-{row['loader']}-iris{row['iris']}")
            self.client(deps, row["minecraft"], os.path.join(d, "minecraft.jar"))
            # The row's own LWJGL: the window and GL bindings Preheat calls differ between Minecraft versions.
            for lib in self.version_json(deps, row["minecraft"])["libraries"]:
                art = lib.get("downloads", {}).get("artifact")
                name = lib["name"]
                if art and not lib.get("rules") and name.startswith("org.lwjgl:") and ":natives-" not in name:
                    self.fetch(art["url"], art["sha1"], os.path.join(d, "lwjgl", os.path.basename(art["path"])))
            for jar in row["jars"]:
                self.fetch(jar["url"], jar["sha1"], os.path.join(d, jar["file"]))
            with open(os.path.join(d, "row.json"), "w") as f:
                json.dump({k: row[k] for k in ("minecraft", "loader", "iris", "sodium")}, f)
        for mc_line, jars in deps.get("neighbours", {}).items():
            if mc_line.startswith("_"):
                continue
            for jar in jars:
                self.fetch(jar["url"], jar["sha1"], os.path.join(self.libs, "neighbours", mc_line, jar["file"]))
        return f"libs ready: {len(os.listdir(out))} compile jars, {len(deps['matrix'])} matrix rows"


def main(argv: list[str], root: str = ROOT, opener: Open = open_url) -> int:
    with open(os.path.join(root, "deps.json")) as f:
        deps = json.load(f)
    print(Fetcher(os.path.join(root, "libs"), opener).run(deps, "--compile-only" in argv))
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
