import sys
from pathlib import Path


def get_base_dir() -> Path:
    """Return the application root directory.

    Frozen executable: directory containing the executable.
    Source execution: directory containing this source tree.
    """
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


BASE_DIR = get_base_dir()
