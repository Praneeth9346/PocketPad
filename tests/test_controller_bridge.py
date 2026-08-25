import struct

import pytest

from controller_bridge import (
    OP_BUTTON,
    OP_LATENCY_PROBE,
    OP_LEFT_STICK,
    OP_MEDIA,
    OP_MOUSE,
    OP_PEDALS,
    OP_PING,
    OP_RIGHT_STICK,
    OP_STEER,
    GamepadBridge,
    validate_packet,
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
        b"\x03",
        b"\x05",
        b"\xff",
        b"\xff" * 64,
    ],
)
def test_invalid_packet_lengths(packet):
    assert not validate_packet(packet)


def test_disconnect_resets_controller(bridge):
    bridge.set_steering(32767)
    bridge.set_trigger(255)

    bridge.reset()

    assert bridge.steering == 0
    assert bridge.throttle == 0
    assert bridge.brake == 0


def test_latency_probe_echo(bridge):
    seq = 42
    client_time_ns = 123456789012345
    pkt = struct.pack("<BIq", OP_LATENCY_PROBE, seq, client_time_ns)
    resp = bridge.handle_binary_packet(pkt)
    assert resp is not None
    assert resp[0] == OP_LATENCY_PROBE
    unpacked_seq, unpacked_ts = struct.unpack("<Iq", resp[1:13])
    assert unpacked_seq == seq
    assert unpacked_ts == client_time_ns


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


@pytest.mark.hardware
def test_real_vigem_controller():
    b = GamepadBridge()
    if b.controller_available:
        b.set_steering(1000)
        assert b.steering == 1000
        b.reset()
        assert b.steering == 0
    b.shutdown()
