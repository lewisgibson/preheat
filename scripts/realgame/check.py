#!/usr/bin/env python3
"""Checks the logs of a real-game run (scripts/realgame/run.sh), a cold launch then a warm one, and prints PASS or
FAIL with what it found. Exits 0 when every check passes.

Usage: check.py <cold latest.log> <warm latest.log>
"""

from __future__ import annotations

import re
import sys

BUILT = re.compile(r"PREHEAT Built the (\w[\w ]*) shaders \(([^)]+)\) in ([\d.]+) ms .*?; (\d+) programs, (\d+) from the cache(.*)$")
VRAM = re.compile(r"PREHEAT Video memory: (\d+) MB free of (\d+) MB, (\d+) pipelines kept \((.*)\)")
DROPPED = re.compile(r"PREHEAT Dropped the kept pipeline for (\S+) in [\d.]+ ms \((.*)\)")
READY = re.compile(r"PREHEAT Pipeline for (\S+) ready in ([\d.]+) ms \((reused|built)")
LONGEST = re.compile(r"PREHEAT longest frame after (\S+) => (\S+): ([\d.]+) ms")
DISCONNECT = re.compile(r"PREHEAT disconnect: ([\d.]+) ms of work")
GPU = re.compile(r"PREHEAT GPU: (.*)$")
REUSE_LIMIT_MS = 250.0


def lines(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def check(cold_path, warm_path):
    failures, notes = [], []
    cold, warm = lines(cold_path), lines(warm_path)
    for name, log in (("cold", cold), ("warm", warm)):
        if not any("PREHEAT Preheat " in l and "features:" in l for l in log):
            failures.append(f"{name}: no startup line")
        for l in log:
            if "PREHEAT Turned off" in l:
                failures.append(f"{name}: {l.split('PREHEAT ', 1)[1]}")
            if "/ERROR]" in l and "PREHEAT" in l:
                failures.append(f"{name}: {l.split(': ', 1)[-1]}")
            if "PREHEAT The driver refused" in l:
                failures.append(f"{name}: {l.split('PREHEAT ', 1)[1]}")
        for l in log:
            m = GPU.search(l)
            if m and name == "cold":
                notes.append(f"GPU: {m.group(1)}")
    builds = {}
    for name, log in (("cold", cold), ("warm", warm)):
        for l in log:
            m = BUILT.search(l)
            if m:
                unsaved = sum(int(n) for n in re.findall(r"(\d+) not saved", m.group(6)))
                builds.setdefault(name, []).append((m.group(1), float(m.group(3)), int(m.group(4)), int(m.group(5)), unsaved))
    for dim, ms, programs, hits, unsaved in builds.get("cold", []):
        notes.append(f"cold build {dim}: {ms:.0f} ms, {hits}/{programs} from the cache, {unsaved} could not be saved")
    if not builds.get("warm"):
        failures.append("warm: no shader builds logged")
    for dim, ms, programs, hits, unsaved in builds.get("warm", []):
        notes.append(f"warm build {dim}: {ms:.0f} ms, {hits}/{programs} from the cache, {unsaved} could not be saved")
        # A program that cannot be saved (it did not link, or the driver gives no binary) is compiled every time.
        if hits + unsaved != programs:
            failures.append(f"warm: {dim} loaded {hits} of {programs} from the cache ({unsaved} could not be saved)")
    for name, log in (("cold", cold), ("warm", warm)):
        for l in log:
            m = READY.search(l)
            if m:
                notes.append(f"{name} switch to {m.group(1)}: {float(m.group(2)):.1f} ms ({m.group(3)})")
                if m.group(3) == "reused" and float(m.group(2)) > REUSE_LIMIT_MS:
                    failures.append(f"{name}: switching to kept {m.group(1)} took {m.group(2)} ms")
                if m.group(3) == "built" and name == "warm":
                    failures.append(f"warm: {m.group(1)} was built on the trip instead of prepared at startup")
            m = VRAM.search(l)
            if m:
                notes.append(f"{name} video memory: {m.group(1)} MB free of {m.group(2)} MB, {m.group(3)} kept, sizes {m.group(4)}")
            m = DROPPED.search(l)
            if m:
                notes.append(f"{name} dropped {m.group(1)}: {m.group(2)}")
            m = LONGEST.search(l)
            if m:
                notes.append(f"{name} longest frame {m.group(1)} => {m.group(2)}: {float(m.group(3)):.0f} ms")
        disconnects = [float(m.group(1)) for l in log if (m := DISCONNECT.search(l))]
        if disconnects:
            notes.append(f"{name} disconnect: {disconnects[-1]:.0f} ms of work")
        else:
            failures.append(f"{name}: no disconnect trace")
    return failures, notes


def main(argv):
    if len(argv) != 3:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    failures, notes = check(argv[1], argv[2])
    for n in notes:
        print(n)
    print()
    if failures:
        print(f"FAIL ({len(failures)})")
        for f in failures:
            print("  " + f)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
