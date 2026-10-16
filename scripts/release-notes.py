#!/usr/bin/env python3
"""Writes the notes for a release: the GitHub release body and the changelog Modrinth shows.

The changes are the version's entry in CHANGELOG.md. Every release after the first must have one, in the changelog
format, naming every checked version in deps.json that moved since the previous release tag (scripts/changelog.py).
The first release has nothing before it, so it has no entry and its notes have no changes section.

The GitHub body is .github/release-body-template.md with the version and the date filled in, then the changes, then
the commit it was built from. Modrinth shows its own download button, so its changelog is the entry alone.

Usage: release-notes.py <version> <github-notes-out> <modrinth-notes-out>
"""

from __future__ import annotations

import datetime
import re
import subprocess
import sys
from collections.abc import Callable
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import changelog  # noqa: E402 (after the path it is found on)

ROOT = Path(__file__).resolve().parent.parent
DEPS = "deps.json"

Git = Callable[[list[str]], str]


def run_git(args: list[str]) -> str:
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def previous_tag(git: Git, version: str) -> str | None:
    """The newest release tag other than this version's own."""
    tags = [tag for tag in git(["tag", "--list", "v*", "--sort=-version:refname"]).split() if tag != f"v{version}"]
    return tags[0] if tags else None


def changes(version: str, text: str, deps: str, git: Git) -> str | None:
    """This version's checked entry, or None for the first release."""
    tag = previous_tag(git, version)
    entry = changelog.sections(text).get(version)
    if tag is None:
        if entry is not None:
            raise RuntimeError(f"{version} is the first release, so CHANGELOG.md should have no entry for it")
        return None
    if entry is None:
        raise RuntimeError(f"CHANGELOG.md has no entry for {version}")
    found = changelog.problems(entry)
    found += [f"does not name {line}" for line in changelog.unnamed(entry, git(["show", f"{tag}:{DEPS}"]), deps)]
    if found:
        raise RuntimeError(f"CHANGELOG.md {version}: " + "; ".join(found))
    return entry


def github_notes(template: str, version: str, date: str, entry: str | None, sha: str) -> str:
    head = template.replace("{{VERSION}}", version).replace("{{DATE}}", date)
    body = f"\n\n## What's changed\n\n{entry}" if entry else ""
    return f"{head.rstrip()}{body}\n\n---\nBuilt from commit `{sha}`.\n"


def main(
    argv: list[str],
    git: Git = run_git,
    today: Callable[[], datetime.date] = lambda: datetime.datetime.now(datetime.UTC).date(),
    root: Path = ROOT,
) -> int:
    if len(argv) != 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    version, github_out, modrinth_out = argv[1], Path(argv[2]), Path(argv[3])
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        print(f"{version} is not a <major>.<minor>.<patch> version", file=sys.stderr)
        return 2
    entry = changes(
        version,
        (root / "CHANGELOG.md").read_text(encoding="utf-8"),
        (root / DEPS).read_text(encoding="utf-8"),
        git,
    )
    template = (root / ".github" / "release-body-template.md").read_text(encoding="utf-8")
    sha = git(["rev-parse", "HEAD"]).strip()
    github_out.write_text(github_notes(template, version, today().isoformat(), entry, sha), encoding="utf-8")
    modrinth_out.write_text((entry or "") + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv))
