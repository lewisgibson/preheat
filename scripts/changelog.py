#!/usr/bin/env python3
"""Checks CHANGELOG.md against the changelog format, and lists the checked versions that moved between releases.

Every version after the first has a "## <version>" section holding one paragraph. It starts "This release", lists
what changed as a comma list with "and" before the last item, names a checked version that moved with both sides
("Sodium 0.9.2 to 0.9.3 on Fabric 26.3"), and has no colon and no full stop at the end. A short note may follow as a
second sentence. The first release has no section, because nothing came before it. docs/releasing.md has the format
with examples.

The checked versions are the Iris, Sodium and Distant Horizons jars in deps.json's compatibility matrix: what a
player installs beside Preheat, and what each release says it was tested with.

Usage: changelog.py check
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

LOADERS = {"fabric": "Fabric", "neoforge": "NeoForge"}

# A matrix jar's file name, to the name the changelog uses for it and its version.
JARS = [
    (re.compile(r"iris-(?:fabric|neoforge)-([^+]+)\+"), "Iris"),
    (re.compile(r"sodium-(?:fabric|neoforge)-([^+]+)\+"), "Sodium"),
    (re.compile(r"DistantHorizons-([\d.]+)-"), "Distant Horizons"),
]

BANNED = {
    "\u2014": "an em dash",
    "\u2013": "an en dash",
    "\u2018": "a curly quote",
    "\u2019": "a curly quote",
    "\u201c": "a curly quote",
    "\u201d": "a curly quote",
    "\u2026": "an ellipsis character",
}


def sections(text: str) -> dict[str, str]:
    """Each "## <version>" heading and the text under it, in file order."""
    found: dict[str, str] = {}
    for match in re.finditer(r"^## (\S+)[ \t]*\n(.*?)(?=^## |\Z)", text, re.M | re.S):
        found[match.group(1)] = match.group(2).strip()
    return found


def problems(entry: str) -> list[str]:
    """What is wrong with one entry, in the format's own words; empty when it is right."""
    found = []
    if not entry.startswith("This release "):
        found.append('does not start "This release"')
    if "\n" in entry:
        found.append("is not one paragraph on one line")
    if ":" in entry:
        found.append("has a colon")
    if entry.endswith("."):
        found.append("ends in a full stop")
    found.extend(f"has {what}" for char, what in BANNED.items() if char in entry)
    return found


def check(text: str) -> list[str]:
    """Every problem in the file, each naming its version."""
    return [f"{version}: {problem}" for version, entry in sections(text).items() for problem in problems(entry)]


def checked(deps: str) -> dict[tuple[str, str], set[str]]:
    """Each mod and where it was checked ("Iris", "Fabric 26.3"), to the versions of it the matrix checks there.
    Distant Horizons is one jar for both loaders, so its place is the Minecraft version alone."""
    found: dict[tuple[str, str], set[str]] = {}
    for row in json.loads(deps)["matrix"]:
        for jar in row["jars"]:
            for pattern, name in JARS:
                match = pattern.match(jar["file"])
                if match:
                    place = row["minecraft"] if name == "Distant Horizons" else f"{LOADERS[row['loader']]} {row['minecraft']}"
                    found.setdefault((name, place), set()).add(match.group(1))
    return found


def moved(old_deps: str, new_deps: str) -> list[str]:
    """Each checked version that changed between two copies of deps.json: "Sodium 0.9.2 to 0.9.3 on Fabric 26.3" when
    one version replaced another, otherwise one "Iris 1.11.8 on Fabric 26.3" line per version added or removed."""
    old, new = checked(old_deps), checked(new_deps)
    lines = []
    for name, place in sorted(old.keys() | new.keys()):
        before, after = old.get((name, place), set()), new.get((name, place), set())
        gone, came = sorted(before - after), sorted(after - before)
        if len(gone) == 1 and len(came) == 1:
            lines.append(f"{name} {gone[0]} to {came[0]} on {place}")
        else:
            lines.extend(f"{name} {version} on {place}" for version in gone + came)
    return lines


def unnamed(entry: str, old_deps: str, new_deps: str) -> list[str]:
    """The moved versions an entry does not name."""
    return [line for line in moved(old_deps, new_deps) if line not in entry]


def main(argv: list[str], root: Path = ROOT) -> int:
    if argv[1:] != ["check"]:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    found = check((root / "CHANGELOG.md").read_text(encoding="utf-8"))
    for problem in found:
        print(f"CHANGELOG.md {problem}", file=sys.stderr)
    return 1 if found else 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
