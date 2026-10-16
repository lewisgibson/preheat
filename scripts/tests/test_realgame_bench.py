from __future__ import annotations

import contextlib
import csv
import io
import tempfile
import unittest
from pathlib import Path

from support import load

bench = load("realgame/bench")


def log(title_ms: int, hits: int) -> str:
    return "\n".join(
        [
            "PREHEAT Built the Nether shaders (minecraft:the_nether) in 1000.0 ms = preprocess 100.0, transform 400.0,"
            f" compile 50.0 (6 shaders), link 10.0, binary load 200.0, other 240.0; 101 programs, {hits} from the cache",
            f"IRISLAB title screen at {title_ms} ms",
            "PREHEAT Built the End shaders (minecraft:the_end) in 9999.0 ms = transform 1.0; 90 programs, 0 from the cache",
        ]
    )


TRIPS = "to,max_frame_ms\nthe_nether,820\noverworld,60\nthe_end,900\noverworld,70\nthe_nether,65\n"


class BenchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)

    def write(self, name: str, text: str) -> Path:
        path = self.folder / name
        path.write_text(text, encoding="utf-8")
        return path

    def test_reads_the_startup_builds_and_the_trips(self) -> None:
        path = self.write("warm-dcold-1.log", log(17800, 95))
        self.write("warm-dcold-1-trips.csv", TRIPS)
        row = bench.read(str(path))
        self.assertEqual(row["title_ms"], 17800.0)
        self.assertEqual((row["builds_ms"], row["load_ms"], row["transform_ms"], row["compile_link_ms"]), (1000.0, 200.0, 400.0, 60.0))
        self.assertEqual((row["hits"], row["programs"]), (95, 101))
        self.assertEqual((row["first_nether_ms"], row["first_end_ms"], row["later_trip_ms"]), (820.0, 900.0, 65.0))

    def test_a_launch_without_trips_or_a_title_screen(self) -> None:
        row = bench.read(str(self.write("stock-dwarm-1.log", "nothing\n")))
        self.assertIsNone(row["title_ms"])
        self.assertIsNone(row["first_nether_ms"])
        self.write("stock-dwarm-2-trips.csv", "to,max_frame_ms\nthe_nether,2100\n")
        row = bench.read(str(self.write("stock-dwarm-2.log", "")))
        self.assertEqual(row["first_nether_ms"], 2100.0)
        self.assertIsNone(row["later_trip_ms"])

    def test_parts_skips_what_is_not_a_timing(self) -> None:
        self.assertEqual(bench.parts("transform 1.5, 6 shaders, link 2"), {"transform": 1.5, "link": 2.0})

    def test_spread(self) -> None:
        self.assertEqual(bench.spread([None, 3.0, 1.0, 2.0]), "2 ms (1 to 3, n=3)")
        self.assertEqual(bench.spread([None]), "n/a")

    def test_main_writes_the_csv_and_prints_each_condition(self) -> None:
        self.write("warm-dcold-1.log", log(17800, 95))
        self.write("warm-dcold-1-trips.csv", TRIPS)
        self.write("stock-dwarm-1.log", log(13700, 0))
        self.write("summary.txt", "ignored")
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(bench.main(["bench.py", str(self.folder)]), 0)
        text = out.getvalue()
        self.assertIn("driver cache cold, Preheat, its cache filled, 95/101 from the cache\n", text)
        self.assertIn("driver cache warm, without Preheat\n", text)
        self.assertIn("  first Nether trip  820 ms (820 to 820, n=1)", text)
        with open(self.folder / "bench.csv", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        self.assertEqual([(r["condition"], r["round"]) for r in rows], [("stock-dwarm", "1"), ("warm-dcold", "1")])

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(bench.main(["bench.py"]), 2)
        self.assertIn("Usage: bench.py", err.getvalue())


if __name__ == "__main__":
    unittest.main()
