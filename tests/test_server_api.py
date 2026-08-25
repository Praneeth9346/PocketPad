import inspect

from server import GamepadServer


def test_no_dead_telemetry_broadcast_methods():
    assert not hasattr(
        GamepadServer,
        "broadcast_telemetry",
    )

    assert not hasattr(
        GamepadServer,
        "_do_broadcast_telemetry",
    )


def test_server_has_only_expected_broadcast_methods():
    methods = {
        name
        for name, value in inspect.getmembers(
            GamepadServer,
            inspect.isfunction,
        )
        if name.startswith("broadcast")
    }

    assert methods == {
        "broadcast_rumble",
        "broadcast_device_status",
    }
