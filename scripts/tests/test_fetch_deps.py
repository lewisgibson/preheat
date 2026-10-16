from __future__ import annotations

import contextlib
import hashlib
import io
import json
import os
import tempfile
import unittest

from support import load

fetch_deps = load("fetch-deps")


def sha1(data: bytes) -> str:
    return hashlib.sha1(data).hexdigest()


class FakeWeb:
    """Serves bytes by URL and records every download, so a test can see what was fetched again."""

    def __init__(self, files: dict[str, bytes]) -> None:
        self.files = files
        self.opened: list[str] = []

    def __call__(self, url: str) -> io.BytesIO:
        self.opened.append(url)
        return io.BytesIO(self.files[url])


def version_json(client: bytes, libraries: list[dict[str, object]]) -> bytes:
    return json.dumps(
        {"downloads": {"client": {"url": "u:client", "sha1": sha1(client)}}, "libraries": libraries}
    ).encode()


def library(name: str, data: bytes, rules: bool = False) -> dict[str, object]:
    lib: dict[str, object] = {
        "name": name,
        "downloads": {"artifact": {"url": f"u:{name}", "sha1": sha1(data), "path": f"x/{name.split(':')[1]}.jar"}},
    }
    if rules:
        lib["rules"] = [{"action": "allow"}]
    return lib


class FetchDepsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = self.tmp.name
        client = b"client"
        libs = {
            "org.lwjgl:lwjgl:3": b"lwjgl",
            "com.mojang:brigadier:1": b"brigadier",
            "org.lwjgl:lwjgl:3:natives-linux": b"native",
            "org.lwjgl:lwjgl-glfw:3": b"glfw-rules",
        }
        entries = [
            library("org.lwjgl:lwjgl:3", libs["org.lwjgl:lwjgl:3"]),
            library("com.mojang:brigadier:1", libs["com.mojang:brigadier:1"]),
            library("org.lwjgl:lwjgl:3:natives-linux", libs["org.lwjgl:lwjgl:3:natives-linux"]),
            library("org.lwjgl:lwjgl-glfw:3", libs["org.lwjgl:lwjgl-glfw:3"], rules=True),
            {"name": "no.download:here:1"},
        ]
        meta = version_json(client, entries)
        self.web = FakeWeb(
            {
                "u:meta": meta,
                "u:client": client,
                "u:iris": b"iris",
                "u:row-iris": b"row-iris",
                "u:neighbour": b"neighbour",
                **{f"u:{name}": data for name, data in libs.items()},
            }
        )
        self.deps = {
            "minecraft": {"26.3": {"url": "u:meta", "sha1": sha1(meta)}},
            "compile": {"minecraft": "26.3", "jars": [{"url": "u:iris", "sha1": sha1(b"iris"), "file": "iris.jar"}]},
            "matrix": [
                {
                    "minecraft": "26.3",
                    "loader": "fabric",
                    "iris": "1.11.7",
                    "sodium": "0.9.2",
                    "jars": [{"url": "u:row-iris", "sha1": sha1(b"row-iris"), "file": "iris.jar"}],
                }
            ],
            "neighbours": {
                "_comment": "skipped",
                "26.3": [{"url": "u:neighbour", "sha1": sha1(b"neighbour"), "file": "n.jar"}],
            },
        }
        with open(os.path.join(self.root, "deps.json"), "w") as f:
            json.dump(self.deps, f)

    def path(self, *parts: str) -> str:
        return os.path.join(self.root, "libs", *parts)

    def test_fetches_the_compile_jars_the_matrix_and_the_neighbours(self) -> None:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(fetch_deps.main(["fetch-deps.py"], self.root, self.web), 0)
        self.assertEqual(out.getvalue().strip(), "libs ready: 4 compile jars, 1 matrix rows")
        compile_jars = sorted(os.listdir(self.path("compile")))
        # natives and OS-specific (ruled) libraries are left out
        self.assertEqual(compile_jars, ["brigadier.jar", "iris.jar", "lwjgl.jar", "minecraft-client-26.3.jar"])
        row = self.path("matrix", "26.3-fabric-iris1.11.7")
        self.assertEqual(sorted(os.listdir(os.path.join(row, "lwjgl"))), ["lwjgl.jar"])
        with open(os.path.join(row, "row.json")) as f:
            self.assertEqual(json.load(f), {"minecraft": "26.3", "loader": "fabric", "iris": "1.11.7", "sodium": "0.9.2"})
        self.assertTrue(os.path.exists(self.path("neighbours", "26.3", "n.jar")))
        self.assertFalse(os.path.exists(self.path("neighbours", "_comment")))

    def test_compile_only_skips_the_matrix_and_the_neighbours(self) -> None:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            fetch_deps.main(["fetch-deps.py", "--compile-only"], self.root, self.web)
        self.assertEqual(out.getvalue().strip(), "libs ready: 4 compile jars")
        self.assertFalse(os.path.exists(self.path("matrix")))

    def test_a_file_already_present_with_the_right_hash_is_not_downloaded_again(self) -> None:
        with contextlib.redirect_stdout(io.StringIO()):
            fetch_deps.main(["fetch-deps.py", "--compile-only"], self.root, self.web)
            first = len(self.web.opened)
            fetch_deps.main(["fetch-deps.py", "--compile-only"], self.root, self.web)
        self.assertEqual(len(self.web.opened), first)

    def test_a_wrong_hash_stops_and_leaves_nothing_behind(self) -> None:
        self.web.files["u:iris"] = b"tampered"
        with self.assertRaises(SystemExit) as stop, contextlib.redirect_stdout(io.StringIO()):
            fetch_deps.main(["fetch-deps.py", "--compile-only"], self.root, self.web)
        self.assertIn("sha1 mismatch for u:iris", str(stop.exception))
        self.assertFalse(os.path.exists(self.path("compile", "iris.jar")))
        self.assertFalse(os.path.exists(self.path("compile", "iris.jar.part")))

    def test_the_real_opener_sends_a_user_agent(self) -> None:
        seen = {}

        def fake_urlopen(request: object, timeout: int) -> str:
            seen["agent"] = request.get_header("User-agent")  # type: ignore[attr-defined]
            seen["timeout"] = timeout
            return "response"

        real = fetch_deps.urllib.request.urlopen
        fetch_deps.urllib.request.urlopen = fake_urlopen
        try:
            self.assertEqual(fetch_deps.open_url("https://example.invalid/x.jar"), "response")
        finally:
            fetch_deps.urllib.request.urlopen = real
        self.assertIn("preheat", seen["agent"])
        self.assertEqual(seen["timeout"], 300)


if __name__ == "__main__":
    unittest.main()
