import os

import pytest


@pytest.fixture(autouse=True)
def configure_test_environment(request):
    """Automatically isolate unit tests from hardware driver collisions unless marked hardware."""
    if "hardware" in request.keywords:
        os.environ.pop("POCKETPAD_DISABLE_VGAMEPAD", None)
    else:
        os.environ["POCKETPAD_DISABLE_VGAMEPAD"] = "1"
    yield
