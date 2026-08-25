import asyncio
import json
from unittest.mock import patch

import pytest
import websockets

import controller_bridge
from server import EXPECTED_TOKEN, PROTOCOL_VERSION, GamepadServer


@pytest.fixture
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
async def server_fixture():
    with patch.object(controller_bridge, "VGAMEPAD_AVAILABLE", False):
        server = GamepadServer()
        server.loop = asyncio.get_running_loop()

        ws_server = await websockets.serve(
            server.handle_client,
            "127.0.0.1",
            0,
            max_size=64 * 1024,
        )
        port = ws_server.sockets[0].getsockname()[1]
        server.ws_uri = f"ws://127.0.0.1:{port}"
        server.token = EXPECTED_TOKEN

        yield server

        ws_server.close()
        await ws_server.wait_closed()
        await server.shutdown()


@pytest.mark.asyncio
async def test_valid_handshake(server_fixture):
    async with websockets.connect(server_fixture.ws_uri) as ws:
        await ws.send(
            json.dumps(
                {
                    "type": "hello",
                    "token": server_fixture.token,
                }
            )
        )

        raw_resp = await asyncio.wait_for(ws.recv(), timeout=2.0)
        response = json.loads(raw_resp)

        assert response["type"] == "hello_ack"
        assert response["version"] == 1
        assert response["server"] == "PocketPad"


@pytest.mark.asyncio
async def test_invalid_handshake(server_fixture):
    with pytest.raises(websockets.exceptions.ConnectionClosed):
        async with websockets.connect(server_fixture.ws_uri) as ws:
            await ws.send(
                json.dumps(
                    {
                        "type": "hello",
                        "token": "invalid-token-12345",
                    }
                )
            )
            await asyncio.wait_for(ws.recv(), timeout=2.0)


@pytest.mark.asyncio
async def test_binary_before_authentication(server_fixture):
    with pytest.raises(websockets.exceptions.ConnectionClosed):
        async with websockets.connect(server_fixture.ws_uri) as ws:
            # Send steering packet before authenticating
            await ws.send(b"\x01\x00\x00")
            await asyncio.wait_for(ws.recv(), timeout=2.0)


def test_protocol_version_is_supported():
    assert PROTOCOL_VERSION == 1
