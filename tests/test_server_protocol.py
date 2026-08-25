import asyncio
import json
from unittest.mock import patch
import pytest
import websockets
import controller_bridge
from server import GamepadServer, EXPECTED_TOKEN, PROTOCOL_VERSION


@pytest.fixture
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.mark.asyncio
async def test_authentication_handshake():
    with patch.object(controller_bridge, "VGAMEPAD_AVAILABLE", False):
        server = GamepadServer()
        server.loop = asyncio.get_running_loop()

        ws_server = await websockets.serve(
            server.handle_client,
            "127.0.0.1",
            0,
        )
        port = ws_server.sockets[0].getsockname()[1]
        uri = f"ws://127.0.0.1:{port}"

        try:
            async with websockets.connect(uri) as ws:
                await ws.send(json.dumps({
                    "type": "hello",
                    "token": EXPECTED_TOKEN
                }))

                raw_resp = await ws.recv()
                response = json.loads(raw_resp)

                assert response["type"] == "hello_ack"
                assert response["version"] == PROTOCOL_VERSION
                assert response["server"] == "PocketPad"
        finally:
            ws_server.close()
            await ws_server.wait_closed()


@pytest.mark.asyncio
async def test_invalid_authentication():
    with patch.object(controller_bridge, "VGAMEPAD_AVAILABLE", False):
        server = GamepadServer()
        server.loop = asyncio.get_running_loop()

        ws_server = await websockets.serve(
            server.handle_client,
            "127.0.0.1",
            0,
        )
        port = ws_server.sockets[0].getsockname()[1]
        uri = f"ws://127.0.0.1:{port}"

        try:
            async with websockets.connect(uri) as ws:
                await ws.send(json.dumps({
                    "type": "hello",
                    "token": "invalid_wrong_token"
                }))

                with pytest.raises(websockets.exceptions.ConnectionClosed):
                    await ws.recv()
        finally:
            ws_server.close()
            await ws_server.wait_closed()
