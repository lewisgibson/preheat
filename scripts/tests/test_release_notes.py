from __future__ import annotations

import contextlib
import datetime
import io
import json
import tempfile
import unittest
from pathlib import Path

from support import load

notes = load("release-notes")


def deps(sodium: str) -> str:
    jars = [{"file": "iris-fabric-1.11.7+mc26.3.jar"}, {"file": f"sodium-fabric-{sodium}+mc26.3.jar"}]
    return json.dumps({"matrix": [{"minecraft": "26.3", "loader": "fabric", "jars": jars}]})


DEPS, NEWER = deps("0.9.2"), deps("0.9.3")
TEMPLATE = "Preheat {{VERSION}}, released {{DATE}}\n\nFor Iris.\n"
ENTRY = "This release updates Sodium 0.9.2 to 0.9.3 on Fabric 26.3 and fixes the End being let go too soon"


class FakeGit:
    """Answers the git commands the script runs from a fixed set of tags and an old deps.json."""

    def __init__(self, tags: list[str], old: str = DEPS) -> None:
        self.tags = tags
        self.old = old
        self.calls: list[list[str]] = []

    def __call__(self, args: list[str]) -> str:
        self.calls.append(args)
        if args[0] == "tag":
            return "\n".join(self.tags) + "\n"
        if args[0] == "show":
            return self.old
        return "abc123\n"


class PreviousTagTest(unittest.TestCase):
    def test_the_newest_tag_other_than_this_version(self) -> None:
        git = FakeGit(["v1.0.1", "v1.0.0"])
        self.assertEqual(notes.previous_tag(git, "1.0.1"), "v1.0.0")
        self.assertEqual(notes.previous_tag(git, "1.0.2"), "v1.0.1")

    def test_none_before_the_first_release(self) -> None:
        self.assertIsNone(notes.previous_tag(FakeGit([]), "1.0.0"))
        self.assertIsNone(notes.previous_tag(FakeGit(["v1.0.0"]), "1.0.0"))


class ChangesTest(unittest.TestCase):
    def test_the_first_release_has_no_changes(self) -> None:
        self.assertIsNone(notes.changes("1.0.0", "# Changelog\n", DEPS, FakeGit([])))

    def test_the_first_release_must_not_have_an_entry(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "first release"):
            notes.changes("1.0.0", f"## 1.0.0\n\n{ENTRY}\n", DEPS, FakeGit([]))

    def test_a_later_release_needs_an_entry(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "no entry for 1.0.1"):
            notes.changes("1.0.1", "# Changelog\n", DEPS, FakeGit(["v1.0.0"]))

    def test_returns_the_entry_checked_against_the_previous_release(self) -> None:
        git = FakeGit(["v1.0.0"])
        self.assertEqual(notes.changes("1.0.1", f"## 1.0.1\n\n{ENTRY}\n", NEWER, git), ENTRY)
        self.assertIn(["show", "v1.0.0:deps.json"], git.calls)

    def test_refuses_an_entry_in_the_wrong_format_or_missing_a_moved_version(self) -> None:
        with self.assertRaisesRegex(
            RuntimeError, "1.0.1: ends in a full stop; does not name Sodium 0.9.2 to 0.9.3 on Fabric 26.3"
        ):
            notes.changes("1.0.1", "## 1.0.1\n\nThis release fixes it.\n", NEWER, FakeGit(["v1.0.0"]))


class GithubNotesTest(unittest.TestCase):
    def test_fills_the_template_and_adds_the_changes_and_the_commit(self) -> None:
        self.assertEqual(
            notes.github_notes(TEMPLATE, "1.0.1", "2026-10-16", ENTRY, "abc"),
            "Preheat 1.0.1, released 2026-10-16\n\nFor Iris."
            f"\n\n## What's changed\n\n{ENTRY}\n\n---\nBuilt from commit `abc`.\n",
        )

    def test_the_first_release_has_no_changes_section(self) -> None:
        body = notes.github_notes(TEMPLATE, "1.0.0", "2026-10-16", None, "abc")
        self.assertNotIn("What's changed", body)
        self.assertTrue(body.endswith("For Iris.\n\n---\nBuilt from commit `abc`.\n"))


class MainTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / ".github").mkdir()
        (self.root / ".github" / "release-body-template.md").write_text(TEMPLATE)
        (self.root / "deps.json").write_text(NEWER)
        (self.root / "CHANGELOG.md").write_text(f"# Changelog\n\n## 1.0.1\n\n{ENTRY}\n")

    def run_main(self, version: str, git: FakeGit) -> tuple[int, str, str]:
        github, modrinth = self.root / "github.md", self.root / "modrinth.md"
        code = notes.main(
            ["release-notes.py", version, str(github), str(modrinth)], git, lambda: datetime.date(2026, 10, 16), self.root
        )
        return code, github.read_text(), modrinth.read_text()

    def test_writes_both_notes(self) -> None:
        code, github, modrinth = self.run_main("1.0.1", FakeGit(["v1.0.0"]))
        self.assertEqual(code, 0)
        self.assertIn("released 2026-10-16", github)
        self.assertIn(f"## What's changed\n\n{ENTRY}", github)
        self.assertIn("Built from commit `abc123`", github)
        self.assertEqual(modrinth, ENTRY + "\n")

    def test_the_first_release_has_an_empty_modrinth_changelog(self) -> None:
        (self.root / "CHANGELOG.md").write_text("# Changelog\n")
        code, github, modrinth = self.run_main("1.0.0", FakeGit([]))
        self.assertEqual((code, modrinth), (0, "\n"))
        self.assertNotIn("What's changed", github)

    def test_refuses_a_version_that_is_not_three_numbers(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(notes.main(["release-notes.py", "1.0.0+mc26.3", "a", "b"], FakeGit([])), 2)
        self.assertIn("not a <major>.<minor>.<patch> version", err.getvalue())

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(notes.main(["release-notes.py"]), 2)
        self.assertIn("Usage: release-notes.py", err.getvalue())

    def test_the_real_git_runs_in_the_repository(self) -> None:
        self.assertRegex(notes.run_git(["rev-parse", "HEAD"]).strip(), "^[0-9a-f]{40}$")

    def test_the_default_date_is_today_in_utc(self) -> None:
        github, modrinth = self.root / "g.md", self.root / "m.md"
        notes.main(["release-notes.py", "1.0.1", str(github), str(modrinth)], FakeGit(["v1.0.0"]), root=self.root)
        today = datetime.datetime.now(datetime.UTC).date().isoformat()
        self.assertIn(f"released {today}", github.read_text())

    def test_the_repository_notes_build_for_the_first_release(self) -> None:
        github, modrinth = self.root / "g.md", self.root / "m.md"
        self.assertEqual(notes.main(["release-notes.py", "1.0.0", str(github), str(modrinth)], FakeGit([])), 0)
        self.assertNotIn("{{", github.read_text())


if __name__ == "__main__":
    unittest.main()
