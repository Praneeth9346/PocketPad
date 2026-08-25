import asyncio
import json
from unittest.mock import patch

import pytest
import websockets

import controller_bridge
from server import EXPECTED_TOKEN, PROTOCOL_VERSION, GamepadServer


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
async def test_invalid_token(server_fixture):
    async with websockets.connect(server_fixture.ws_uri) as websocket:

        await websocket.send(
            json.dumps(
                {
                    "type": "hello",
                    "token": "invalid-token",
                }
            )
        )

        with pytest.raises(websockets.ConnectionClosed):
            await websocket.recv()


@pytest.mark.asyncio
async def test_missing_handshake(server_fixture):
    with pytest.raises(websockets.exceptions.ConnectionClosed):
        async with websockets.connect(server_fixture.ws_uri) as ws:
            await ws.send(
                json.dumps(
                    {
                        "type": "wrong_message",
                    }
                )
            )
            await asyncio.wait_for(ws.recv(), timeout=2.0)


@pytest.mark.asyncio
async def test_missing_hello_type(server_fixture):
    async with websockets.connect(server_fixture.ws_uri) as websocket:
        await websocket.send(
            json.dumps(
                {
                    "token": server_fixture.token,
                }
            )
        )
        with pytest.raises(websockets.ConnectionClosed):
            await websocket.recv()


@pytest.mark.asyncio
async def test_binary_before_auth(server_fixture):
    with pytest.raises(websockets.exceptions.ConnectionClosed):
        async with websockets.connect(server_fixture.ws_uri) as ws:
            await ws.send(b"\x01\x00\x00")
            await asyncio.wait_for(ws.recv(), timeout=2.0)


@pytest.mark.asyncio
async def test_binary_before_authentication(server_fixture):
    await test_binary_before_auth(server_fixture)


@pytest.mark.asyncio
async def test_heartbeat_timeout_resets_controller(server_fixture):
    import time
    from unittest.mock import MagicMock

    from server import CLIENT_HEARTBEAT_TIMEOUT

    mock_bridge = MagicMock()
    mock_bridge.controller_available = True
    server_fixture.bridge = mock_bridge

    # Authenticate a phone client
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
        assert json.loads(raw_resp)["type"] == "hello_ack"

        # Send binary steering to set confirmed phone state
        await ws.send(b"\x01\x00\x00")
        await asyncio.sleep(0.05)

        # Force simulate past heartbeat timeout
        for client_ws in list(server_fixture.connected_clients):
            server_fixture.client_last_activity[client_ws] = time.monotonic() - CLIENT_HEARTBEAT_TIMEOUT - 1.0

        await server_fixture.check_heartbeats()
        mock_bridge.reset.assert_called()


def test_protocol_version():
    from server import PROTOCOL_VERSION

    assert PROTOCOL_VERSION == 1


def test_protocol_version_is_supported():
    assert PROTOCOL_VERSION == 1
