from __future__ import annotations

import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from support import load

changelog = load("changelog")

GOOD = "This release adds Iris 1.11.8 on Fabric 26.3, keeps the End ready after F3+T and fixes a pause on rejoining"


def deps(*rows: tuple[str, str, list[str]]) -> str:
    return json.dumps(
        {"matrix": [{"minecraft": mc, "loader": loader, "jars": [{"file": f} for f in files]} for mc, loader, files in rows]}
    )


OLD = deps(
    ("26.3", "fabric", ["iris-fabric-1.11.7+mc26.3.jar", "sodium-fabric-0.9.2+mc26.3.jar", "DistantHorizons-3.3.4-26.3-fabric-neoforge.jar"]),
    ("26.3", "neoforge", ["iris-neoforge-1.11.7+mc26.3.jar", "sodium-neoforge-0.9.2+mc26.3.jar", "DistantHorizons-3.3.4-26.3-fabric-neoforge.jar"]),
    ("26.3", "fabric", ["iris-fabric-1.11.6+mc26.3.jar", "sodium-fabric-0.9.2+mc26.3.jar", "minecraft-client.jar"]),
)


class SectionsTest(unittest.TestCase):
    def test_reads_each_version_in_file_order(self) -> None:
        text = "# Changelog\n\nIntro.\n\n## 1.1.0\n\nNewer\n\n## 1.0.1  \n\nOlder\n"
        self.assertEqual(changelog.sections(text), {"1.1.0": "Newer", "1.0.1": "Older"})

    def test_a_file_with_no_versions_has_none(self) -> None:
        self.assertEqual(changelog.sections("# Changelog\n\nNothing yet.\n"), {})


class ProblemsTest(unittest.TestCase):
    def test_a_good_entry_has_none(self) -> None:
        self.assertEqual(changelog.problems(GOOD), [])
        self.assertEqual(changelog.problems(GOOD + ". Iris 1.11.8 is a beta build"), [])

    def test_names_every_problem(self) -> None:
        self.assertEqual(
            changelog.problems("Updates: the driver.\nMore."),
            [
                'does not start "This release"',
                "is not one paragraph on one line",
                "has a colon",
                "ends in a full stop",
            ],
        )

    def test_banned_typography(self) -> None:
        self.assertEqual(
            changelog.problems("This release fixes \u2014 \u2013 \u2018 \u201c \u2026"),
            ["has an em dash", "has an en dash", "has a curly quote", "has a curly quote", "has an ellipsis character"],
        )

    def test_check_names_the_version(self) -> None:
        text = f"## 1.0.1\n\n{GOOD}\n\n## 1.0.2\n\nFixed things.\n"
        self.assertEqual(changelog.check(text), ['1.0.2: does not start "This release"', "1.0.2: ends in a full stop"])


class CheckedTest(unittest.TestCase):
    def test_groups_each_mod_by_where_it_was_checked(self) -> None:
        self.assertEqual(
            changelog.checked(OLD),
            {
                ("Iris", "Fabric 26.3"): {"1.11.6", "1.11.7"},
                ("Iris", "NeoForge 26.3"): {"1.11.7"},
                ("Sodium", "Fabric 26.3"): {"0.9.2"},
                ("Sodium", "NeoForge 26.3"): {"0.9.2"},
                ("Distant Horizons", "26.3"): {"3.3.4"},
            },
        )


class MovedTest(unittest.TestCase):
    def test_nothing_moved(self) -> None:
        self.assertEqual(changelog.moved(OLD, OLD), [])

    def test_one_version_replacing_another_reads_old_to_new(self) -> None:
        new = OLD.replace("DistantHorizons-3.3.4", "DistantHorizons-3.3.5").replace("sodium-neoforge-0.9.2", "sodium-neoforge-0.9.3")
        self.assertEqual(
            changelog.moved(OLD, new), ["Distant Horizons 3.3.4 to 3.3.5 on 26.3", "Sodium 0.9.2 to 0.9.3 on NeoForge 26.3"]
        )

    def test_added_and_removed_versions_are_named_one_by_one(self) -> None:
        new = OLD.replace("iris-fabric-1.11.6", "iris-fabric-1.11.8")
        self.assertEqual(changelog.moved(OLD, new), ["Iris 1.11.6 to 1.11.8 on Fabric 26.3"])
        added = deps(("26.4", "fabric", ["iris-fabric-1.12.0+mc26.4.jar"]))
        self.assertEqual(changelog.moved(deps(), added), ["Iris 1.12.0 on Fabric 26.4"])
        self.assertEqual(changelog.moved(added, deps()), ["Iris 1.12.0 on Fabric 26.4"])
        two = deps(("26.3", "fabric", ["iris-fabric-1.11.8+mc26.3.jar"]), ("26.3", "fabric", ["iris-fabric-1.11.9+mc26.3.jar"]))
        self.assertEqual(changelog.moved(deps(), two), ["Iris 1.11.8 on Fabric 26.3", "Iris 1.11.9 on Fabric 26.3"])

    def test_unnamed_lists_what_the_entry_leaves_out(self) -> None:
        new = OLD.replace("sodium-fabric-0.9.2+mc26.3.jar", "sodium-fabric-0.9.3+mc26.3.jar")
        self.assertEqual(changelog.unnamed(GOOD, OLD, new), ["Sodium 0.9.2 to 0.9.3 on Fabric 26.3"])
        self.assertEqual(changelog.unnamed(GOOD + ", and updates Sodium 0.9.2 to 0.9.3 on Fabric 26.3", OLD, new), [])


class MainTest(unittest.TestCase):
    def run_check(self, text: str) -> tuple[int, str]:
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "CHANGELOG.md").write_text(text, encoding="utf-8")
            err = io.StringIO()
            with contextlib.redirect_stderr(err):
                code = changelog.main(["changelog.py", "check"], Path(tmp))
        return code, err.getvalue()

    def test_a_good_file_passes(self) -> None:
        self.assertEqual(self.run_check(f"# Changelog\n\n## 1.0.1\n\n{GOOD}\n"), (0, ""))

    def test_a_bad_file_fails_with_each_problem(self) -> None:
        self.assertEqual(
            self.run_check("## 1.0.1\n\nThis release fixes it.\n"), (1, "CHANGELOG.md 1.0.1: ends in a full stop\n")
        )

    def test_wrong_arguments_print_the_usage(self) -> None:
        err = io.StringIO()
        with contextlib.redirect_stderr(err):
            self.assertEqual(changelog.main(["changelog.py"]), 2)
        self.assertIn("Usage: changelog.py check", err.getvalue())

    def test_the_repository_changelog_passes(self) -> None:
        self.assertEqual(changelog.main(["changelog.py", "check"]), 0)

    def test_the_repository_deps_has_checked_versions(self) -> None:
        found = changelog.checked((changelog.ROOT / "deps.json").read_text(encoding="utf-8"))
        self.assertIn(("Iris", "Fabric 26.3"), found)


if __name__ == "__main__":
    unittest.main()
