from __future__ import annotations

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from support import load

check = load("realgame/check")

START = "[Render thread/INFO]: PREHEAT Preheat 1.0.0 on Fabric 0.19.5; checked in 300 ms; features: keep_pipelines"
GPU = "[Render thread/INFO]: PREHEAT GPU: NVIDIA GeForce RTX 5090, driver 616.92"
NETHER = (
    "[Render thread/INFO]: PREHEAT Built the Nether shaders (minecraft:the_nether) in 1139.5 ms = preprocess 150.1;"
    " 101 programs, {hits} from the cache{rest}"
)
DISCONNECT = "[Render thread/INFO]: PREHEAT disconnect: 52.0 ms of work (dh close)"


class CheckTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def logs(self, cold: list[str], warm: list[str]) -> tuple[str, str]:
        paths = []
        for name, lines in (("cold", cold), ("warm", warm)):
            path = Path(self.tmp.name, f"{name}.log")
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
            paths.append(str(path))
        return paths[0], paths[1]

    def test_a_clean_pair_of_runs_passes_and_notes_what_happened(self) -> None:
        cold = [
            START,
            GPU,
            NETHER.format(hits=0, rest=""),
            "PREHEAT Pipeline for minecraft:the_nether ready in 0.7 ms (reused, 3 kept)",
            "PREHEAT Video memory: 21071 MB free of 32607 MB, 3 pipelines kept ({minecraft:overworld=2095})",
            "PREHEAT Dropped the kept pipeline for aether:the_aether in 3.1 ms (video memory is low)",
            "PREHEAT longest frame after minecraft:overworld => minecraft:the_nether: 94.1 ms (first frame 17.0 ms)",
            DISCONNECT,
        ]
        warm = [START, NETHER.format(hits=95, rest="; 6 not saved"), DISCONNECT]
        failures, notes = check.check(*self.logs(cold, warm))
        self.assertEqual(failures, [])
        self.assertIn("GPU: NVIDIA GeForce RTX 5090, driver 616.92", notes)
        self.assertIn("warm build Nether: 1140 ms, 95/101 from the cache, 6 could not be saved", notes)
        self.assertIn("cold switch to minecraft:the_nether: 0.7 ms (reused)", notes)
        self.assertIn("cold dropped aether:the_aether: video memory is low", notes)
        self.assertIn("cold longest frame minecraft:overworld => minecraft:the_nether: 94 ms", notes)
        self.assertIn("warm disconnect: 52 ms of work", notes)

    def test_names_every_failure(self) -> None:
        cold = [
            "[Render thread/INFO]: PREHEAT Turned off keep_pipelines: missing a method",
            "[Render thread/ERROR]: PREHEAT cache write failed",
            "[Render thread/WARN]: PREHEAT The driver refused a cached shader (bad length)",
            "PREHEAT Pipeline for minecraft:the_end ready in 300.0 ms (reused, 2 kept)",
        ]
        warm = [
            START,
            NETHER.format(hits=50, rest=""),
            "PREHEAT Pipeline for minecraft:the_nether ready in 900.0 ms (built, 1 kept)",
        ]
        failures, _ = check.check(*self.logs(cold, warm))
        self.assertEqual(
            failures,
            [
                "cold: no startup line",
                "cold: Turned off keep_pipelines: missing a method",
                "cold: PREHEAT cache write failed",
                "cold: The driver refused a cached shader (bad length)",
                "warm: Nether loaded 50 of 101 from the cache (0 could not be saved)",
                "cold: switching to kept minecraft:the_end took 300.0 ms",
                "cold: no disconnect trace",
                "warm: minecraft:the_nether was built on the trip instead of prepared at startup",
                "warm: no disconnect trace",
            ],
        )

    def test_a_warm_run_without_builds_fails(self) -> None:
        failures, _ = check.check(*self.logs([START, DISCONNECT], [START, DISCONNECT]))
        self.assertEqual(failures, ["warm: no shader builds logged"])

    def test_main_prints_pass_or_fail(self) -> None:
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            good = self.logs([START, DISCONNECT], [START, NETHER.format(hits=101, rest=""), DISCONNECT])
            self.assertEqual(check.main(["check.py", *good]), 0)
        self.assertTrue(out.getvalue().endswith("PASS\n"))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(check.main(["check.py", *self.logs([], [])]), 1)
        self.assertIn("FAIL (5)\n  cold: no startup line", out.getvalue())

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(check.main(["check.py"]), 2)
        self.assertIn("Usage: check.py", err.getvalue())


if __name__ == "__main__":
    unittest.main()
