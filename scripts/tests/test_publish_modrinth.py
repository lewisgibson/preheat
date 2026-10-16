from __future__ import annotations

import contextlib
import http.server
import io
import json
import tempfile
import threading
import unittest
from collections.abc import Mapping
from pathlib import Path

from support import load

publish = load("publish-modrinth")

ENV = {"MODRINTH_TOKEN": "secret", "MODRINTH_PROJECT_ID": "proj"}


class FakeModrinth:
    """Records each request and answers the version list with what is already published."""

    def __init__(self, published: list[str]) -> None:
        self.published = published
        self.requests: list[tuple[str, str, Mapping[str, str], bytes | None]] = []

    def __call__(self, method: str, url: str, headers: Mapping[str, str], body: bytes | None) -> object:
        self.requests.append((method, url, headers, body))
        if method == "GET":
            return [{"version_number": v} for v in self.published]
        return {"id": "new"}


class PublishTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jars = []
        for mc in ("26.3", "26.2"):
            jar = Path(self.tmp.name, f"preheat-1.0.0+mc{mc}.jar")
            jar.write_bytes(b"PK jar " + mc.encode())
            self.jars.append(jar)

    def test_without_a_token_nothing_is_sent(self) -> None:
        fake = FakeModrinth([])
        self.assertEqual(
            publish.publish("1.0.0", "notes", self.jars, {}, fake),
            ["MODRINTH_TOKEN is not set, so nothing was uploaded to Modrinth"],
        )
        self.assertEqual(fake.requests, [])

    def test_a_token_without_a_project_is_an_error(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "MODRINTH_PROJECT_ID"):
            publish.publish("1.0.0", "notes", self.jars, {"MODRINTH_TOKEN": "secret"}, FakeModrinth([]))

    def test_uploads_one_version_per_minecraft_line(self) -> None:
        fake = FakeModrinth([])
        report = publish.publish("1.0.0", "The notes", self.jars, ENV, fake, lambda: "BOUNDARY")
        self.assertEqual(
            report,
            ["uploaded preheat-1.0.0+mc26.3.jar as mc26.3-1.0.0", "uploaded preheat-1.0.0+mc26.2.jar as mc26.2-1.0.0"],
        )
        get, post, _ = fake.requests
        self.assertEqual(get[:3], ("GET", "https://api.modrinth.com/v2/project/proj/version", {"Authorization": "secret"}))
        method, url, headers, body = post
        self.assertEqual((method, url), ("POST", "https://api.modrinth.com/v2/version"))
        self.assertEqual(headers["Content-Type"], "multipart/form-data; boundary=BOUNDARY")
        self.assertEqual(headers["Authorization"], "secret")
        assert body is not None
        head, data, rest = body.split(b"\r\n\r\n", 2)
        self.assertIn(b'name="data"', head)
        meta = json.loads(data.split(b"\r\n--BOUNDARY")[0])
        self.assertEqual(meta["version_number"], "mc26.3-1.0.0")
        self.assertEqual(meta["name"], "Preheat 1.0.0 for Minecraft 26.3")
        self.assertEqual(meta["game_versions"], ["26.3"])
        self.assertEqual(meta["loaders"], ["fabric", "neoforge"])
        self.assertEqual(meta["environment"], "client_only", "client required, server unsupported")
        self.assertEqual(meta["changelog"], "The notes")
        self.assertEqual(meta["project_id"], "proj")
        self.assertEqual(
            meta["dependencies"],
            [
                {"project_id": publish.IRIS, "dependency_type": "required"},
                {"project_id": publish.MOD_MENU, "dependency_type": "optional"},
            ],
        )
        self.assertIn(b'name="file"; filename="preheat-1.0.0+mc26.3.jar"', body)
        self.assertIn(b"PK jar 26.3\r\n--BOUNDARY--\r\n", rest)

    def test_a_dry_run_prints_each_request_and_sends_nothing(self) -> None:
        fake = FakeModrinth([])
        report = publish.publish("1.0.0", "n", self.jars, {"DRY_RUN": "1", "MODRINTH_PROJECT_ID": "proj"}, fake)
        self.assertEqual(len(report), 2)
        self.assertTrue(report[0].startswith("would POST https://api.modrinth.com/v2/version with {"))
        self.assertIn('"version_number": "mc26.3-1.0.0"', report[0])
        self.assertIn('"environment": "client_only"', report[0])
        self.assertTrue(report[1].endswith("and preheat-1.0.0+mc26.2.jar"))
        self.assertEqual(fake.requests, [])

    def test_a_version_already_on_modrinth_is_skipped(self) -> None:
        fake = FakeModrinth(["mc26.3-1.0.0"])
        report = publish.publish("1.0.0", "n", self.jars, ENV, fake)
        self.assertEqual(report[0], "mc26.3-1.0.0 is already on Modrinth, skipped")
        self.assertEqual([r[0] for r in fake.requests], ["GET", "POST"])

    def test_an_unexpected_list_answer_counts_as_nothing_published(self) -> None:
        self.assertEqual(publish.existing_versions("p", "t", lambda *a: {"error": "x"}), set())

    def test_a_jar_of_another_version_is_refused(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "not a Preheat 1.0.1 jar"):
            publish.publish("1.0.1", "n", self.jars, ENV, FakeModrinth([]))

    def test_a_file_that_is_not_a_preheat_jar_is_refused(self) -> None:
        other = Path(self.tmp.name, "notes.txt")
        other.write_text("x")
        with self.assertRaisesRegex(RuntimeError, "notes.txt"):
            publish.publish("1.0.0", "n", [other], ENV, FakeModrinth([]))


class MainTest(unittest.TestCase):
    def test_reads_the_changelog_and_prints_what_was_done(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            notes = Path(tmp, "notes.md")
            notes.write_text("  Notes  \n")
            jar = Path(tmp, "preheat-1.0.0+mc26.3.jar")
            jar.write_bytes(b"jar")
            fake = FakeModrinth([])
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                code = publish.main(["publish-modrinth.py", "1.0.0", str(notes), str(jar)], ENV, fake)
        self.assertEqual(code, 0)
        self.assertEqual(out.getvalue(), "uploaded preheat-1.0.0+mc26.3.jar as mc26.3-1.0.0\n")
        self.assertIn(b'"changelog": "Notes"', fake.requests[1][3] or b"")

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(publish.main(["publish-modrinth.py", "1.0.0"], ENV, FakeModrinth([])), 2)
        self.assertIn("Usage:", err.getvalue())


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802 (the name http.server calls)
        if self.path == "/fail":
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b'{"error":"invalid_input"}')
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(json.dumps({"agent": self.headers["User-Agent"], "auth": self.headers["Authorization"]}).encode())

    def do_POST(self) -> None:  # noqa: N802
        self.send_response(204)
        self.end_headers()

    def log_message(self, *args: object) -> None:
        pass


class SendTest(unittest.TestCase):
    """The real transport, against a server on this machine."""

    def setUp(self) -> None:
        self.server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def test_sends_the_user_agent_and_headers_and_parses_json(self) -> None:
        answer = publish.send("GET", f"{self.base}/ok", {"Authorization": "t"}, None)
        self.assertEqual(answer, {"agent": publish.USER_AGENT, "auth": "t"})

    def test_an_empty_answer_is_none(self) -> None:
        self.assertIsNone(publish.send("POST", f"{self.base}/version", {}, b"x"))

    def test_an_error_names_the_request_and_the_answer(self) -> None:
        with self.assertRaisesRegex(RuntimeError, r"Modrinth GET .*/fail failed: 400 .*invalid_input"):
            publish.send("GET", f"{self.base}/fail", {}, None)


if __name__ == "__main__":
    unittest.main()
