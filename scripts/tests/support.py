"""Loads a script from scripts/ by its path and file name, which have hyphens and folders and so cannot be imported
by name."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from types import ModuleType

SCRIPTS = Path(__file__).resolve().parent.parent


def load(name: str) -> ModuleType:
    """Loads scripts/<name>.py, where name may hold a folder: load("realgame/check")."""
    spec = importlib.util.spec_from_file_location(name.replace("-", "_").replace("/", "_"), SCRIPTS / f"{name}.py")
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module
