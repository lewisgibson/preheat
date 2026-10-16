#!/usr/bin/env python3
"""Reads the logs of a benchmark (scripts/realgame/bench.sh) and prints, per condition, the median and the range of
the time to the title screen, the time spent in Preheat's startup builds and their binary load, patch and compile
parts, and the longest frame of the first trip to the Nether, the first trip to the End, and every later trip. Writes
bench.csv beside the logs.

Usage: bench.py <results folder>
"""

from __future__ import annotations

import csv
import os
import re
import statistics
import sys

BUILT = re.compile(
    r"PREHEAT Built the (\w[\w ]*) shaders \([^)]+\) in ([\d.]+) ms = (.*?); (\d+) programs, (\d+) from the cache")
TITLE = re.compile(r"IRISLAB title screen at (\d+) ms")
LABEL = re.compile(r"^((?:stock|cold|warm)(?:-d(?:cold|warm))?)-(\d+)\.log$")
FIELDS = ["condition", "round", "title_ms", "builds_ms", "load_ms", "transform_ms", "compile_link_ms", "hits",
          "programs", "first_nether_ms", "first_end_ms", "later_trip_ms"]


def parts(split):
    out = {}
    for item in split.split(", "):
        m = re.match(r"([a-z ]+) ([\d.]+)", item)
        if m:
            out[m.group(1)] = float(m.group(2))
    return out


def read(path):
    row = {k: None for k in FIELDS}
    row.update(builds_ms=0.0, load_ms=0.0, transform_ms=0.0, compile_link_ms=0.0, hits=0, programs=0)
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = TITLE.search(line)
            if m and row["title_ms"] is None:
                row["title_ms"] = float(m.group(1))
            m = BUILT.search(line)
            # Only the startup builds (before the title screen) count here; trips are measured by their frames.
            if m and row["title_ms"] is None:
                p = parts(m.group(3))
                row["builds_ms"] += float(m.group(2))
                row["load_ms"] += p.get("binary load", 0.0)
                row["transform_ms"] += p.get("transform", 0.0)
                row["compile_link_ms"] += p.get("compile", 0.0) + p.get("link", 0.0)
                row["programs"] += int(m.group(4))
                row["hits"] += int(m.group(5))
    trips = path[:-4] + "-trips.csv"
    if os.path.exists(trips):
        seen, later = set(), []
        with open(trips, encoding="utf-8") as f:
            for t in csv.DictReader(f):
                to, frame = t["to"], float(t["max_frame_ms"])
                if to not in seen:
                    seen.add(to)
                    if to == "the_nether":
                        row["first_nether_ms"] = frame
                    elif to == "the_end":
                        row["first_end_ms"] = frame
                    else:
                        later.append(frame)
                else:
                    later.append(frame)
        if later:
            row["later_trip_ms"] = statistics.median(later)
    return row


def spread(values):
    values = [v for v in values if v is not None]
    if not values:
        return "n/a"
    return f"{statistics.median(values):.0f} ms ({min(values):.0f} to {max(values):.0f}, n={len(values)})"


NAMES = {"stock": "without Preheat", "cold": "Preheat, its cache empty", "warm": "Preheat, its cache filled",
         "dcold": "driver cache cold", "dwarm": "driver cache warm"}


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    folder = argv[1]
    rows = []
    for name in sorted(os.listdir(folder)):
        m = LABEL.match(name)
        if m:
            r = read(os.path.join(folder, name))
            r.update(condition=m.group(1), round=int(m.group(2)))
            rows.append(r)
    with open(os.path.join(folder, "bench.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        w.writerows(rows)
    for cond in sorted({r["condition"] for r in rows}, key=lambda c: (c.split("-")[-1], c)):
        sel = [r for r in rows if r["condition"] == cond]
        bits = cond.split("-")
        title = ", ".join(NAMES.get(b, b) for b in reversed(bits))
        if bits[0] != "stock":
            title += f", {sel[0]['hits']}/{sel[0]['programs']} from the cache"
        print(title)
        for label, key in (("title screen", "title_ms"), ("startup builds", "builds_ms"),
                           ("  binary load", "load_ms"), ("  patching", "transform_ms"),
                           ("  compile + link", "compile_link_ms"), ("first Nether trip", "first_nether_ms"),
                           ("first End trip", "first_end_ms"), ("later trips", "later_trip_ms")):
            print(f"  {label:18} {spread([r[key] for r in sel])}")
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
