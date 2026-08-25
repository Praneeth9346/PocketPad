import json
import struct
from pathlib import Path

import pytest

from controller_bridge import (
    OP_BUTTON,
    OP_LEFT_STICK,
    OP_PEDALS,
    OP_PING,
    OP_RIGHT_STICK,
    OP_STEER,
    GamepadBridge,
    validate_packet,
)


@pytest.fixture
def bridge():
    b = GamepadBridge()
    yield b
    b.shutdown()


def test_protocol_vectors_from_file(bridge):
    vector_file = Path(__file__).parent / "protocol_vectors.json"
    assert vector_file.exists()

    with open(vector_file, "r") as f:
        vectors = json.load(f)

    for name, data in vectors.items():
        raw_bytes = bytes.fromhex(data["hex"])
        assert validate_packet(raw_bytes)
        resp = bridge.handle_binary_packet(raw_bytes)
        if data["opcode"] == OP_PING:
            assert resp is not None
            assert resp[0] == 0x0A


@pytest.mark.parametrize(
    "packet",
    [
        b"",
        b"\x01",
        b"\x01\x00",
        b"\x02\x00",
        b"\x03",
        b"\x99",
        b"\xff\xff\xff",
    ],
)
def test_invalid_packet_rejection(packet):
    assert not validate_packet(packet)


def test_steering_encoding(bridge):
    pkt_center = struct.pack("<Bh", OP_STEER, 0)
    pkt_right = struct.pack("<Bh", OP_STEER, 32767)
    pkt_left = struct.pack("<Bh", OP_STEER, -32768)

    assert validate_packet(pkt_center)
    assert validate_packet(pkt_right)
    assert validate_packet(pkt_left)

    assert bridge.handle_binary_packet(pkt_right) is None
    assert bridge.handle_binary_packet(pkt_left) is None


def test_pedals_encoding(bridge):
    pkt_idle = struct.pack("<BBB", OP_PEDALS, 0, 0)
    pkt_full = struct.pack("<BBB", OP_PEDALS, 255, 255)

    assert validate_packet(pkt_idle)
    assert validate_packet(pkt_full)

    assert bridge.handle_binary_packet(pkt_full) is None


def test_button_encoding(bridge):
    pkt_press = struct.pack("<BBB", OP_BUTTON, 0, 1)
    pkt_release = struct.pack("<BBB", OP_BUTTON, 0, 0)

    assert validate_packet(pkt_press)
    assert validate_packet(pkt_release)

    assert bridge.handle_binary_packet(pkt_press) is None


def test_stick_encoding(bridge):
    pkt_ls = struct.pack("<Bhh", OP_LEFT_STICK, 15000, -15000)
    pkt_rs = struct.pack("<Bhh", OP_RIGHT_STICK, -15000, 15000)

    assert validate_packet(pkt_ls)
    assert validate_packet(pkt_rs)

    assert bridge.handle_binary_packet(pkt_ls) is None
    assert bridge.handle_binary_packet(pkt_rs) is None
