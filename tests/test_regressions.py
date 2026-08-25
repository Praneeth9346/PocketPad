import inspect
from pathlib import Path

from server import GamepadServer


def test_single_server_base_dir():
    import server

    functions = [
        name
        for name, value in inspect.getmembers(
            server,
            inspect.isfunction,
        )
        if name == "get_base_dir"
    ]

    assert functions == ["get_base_dir"]


def test_no_telemetry_broadcast():
    assert not hasattr(
        GamepadServer,
        "broadcast_telemetry",
    )

    assert not hasattr(
        GamepadServer,
        "_do_broadcast_telemetry",
    )


def test_usb_loopback_mode():
    from server import is_usb_client

    assert is_usb_client("127.0.0.1")
    assert is_usb_client("::1")
    assert is_usb_client("localhost")


def test_readme_exists():
    root = Path(__file__).resolve().parents[1]
    assert (root / "README.md").exists()
