import struct

import pytest

from controller_bridge import (
    OP_BUTTON,
    OP_LEFT_STICK,
    OP_MEDIA,
    OP_MOUSE,
    OP_PEDALS,
    OP_PING,
    OP_RIGHT_STICK,
    OP_STEER,
    GamepadBridge,
)


@pytest.fixture(scope="module")
def bridge():
    b = GamepadBridge()
    yield b
    b.shutdown()


def test_steering_packet(bridge):
    pkt = struct.pack("<Bh", OP_STEER, 32767)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_steering_negative_packet(bridge):
    pkt = struct.pack("<Bh", OP_STEER, -32768)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_pedals_packet(bridge):
    pkt = struct.pack("<BBB", OP_PEDALS, 255, 128)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_button_packet(bridge):
    pkt = struct.pack("<BBB", OP_BUTTON, 0, 1)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_joystick_packets(bridge):
    pkt_ls = struct.pack("<Bhh", OP_LEFT_STICK, 10000, -10000)
    pkt_rs = struct.pack("<Bhh", OP_RIGHT_STICK, -20000, 20000)
    assert bridge.handle_binary_packet(pkt_ls) is None
    assert bridge.handle_binary_packet(pkt_rs) is None


def test_ping_pong_echo(bridge):
    pkt = struct.pack("<BI", OP_PING, 12345678)
    resp = bridge.handle_binary_packet(pkt)
    assert resp is not None
    assert resp[0] == 0x0A
    assert struct.unpack("<I", resp[1:5])[0] == 12345678


def test_bridge_mouse_opcode(bridge):
    pkt = struct.pack("<BhhB", OP_MOUSE, 100, -50, 0x03)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_bridge_media_opcode(bridge):
    pkt = struct.pack("<BB", OP_MEDIA, 0xAF)
    result = bridge.handle_binary_packet(pkt)
    assert result is None


def test_bridge_rumble_callback():
    rumble_triggered = False

    def test_callback(large, small):
        nonlocal rumble_triggered
        rumble_triggered = True

    b = GamepadBridge(rumble_callback=test_callback)
    assert b.rumble_callback == test_callback
    b.shutdown()


@pytest.mark.parametrize(
    "packet",
    [
        b"",
        b"\x01",
        b"\x01\x00",
        b"\x02",
        b"\x02\x00",
        b"\x03",
        b"\x03\x00",
        b"\x05\x00",
        b"\x06\x00",
        b"\x07\x00",
        b"\x08",
        b"\x09\x00",
        b"\xff",
        b"\xff" * 32,
    ],
)
def test_malformed_packets_do_not_crash(bridge, packet):
    try:
        bridge.handle_binary_packet(packet)
    except (ValueError, struct.error, IndexError) as e:
        pytest.fail(f"Malformed packet raised exception: {packet!r}, err: {e}")


def test_golden_vectors(bridge):
    import json
    from pathlib import Path

    vector_file = Path(__file__).parent / "protocol_vectors.json"
    if not vector_file.exists():
        return

    vectors = json.loads(vector_file.read_text())

    for name, data in vectors.items():
        raw_bytes = bytes.fromhex(data["hex"])
        result = bridge.handle_binary_packet(raw_bytes)
        if data["opcode"] == OP_PING:
            assert result is not None
            assert result[0] == 0x0A
        else:
            assert result is None
