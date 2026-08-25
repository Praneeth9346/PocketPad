import asyncio
import io
import json
import time
from unittest.mock import AsyncMock, MagicMock

import pytest

import server
from server import (
    CustomHTTPHandler,
    GamepadServer,
    _desktop_token_matches,
    _token_matches,
    consume_desktop_session,
    create_desktop_access_token,
    create_desktop_session,
)


def _make_dummy_handler(
    path: str,
    headers: dict | None = None,
    is_https: bool = False,
    client_ip: str = "127.0.0.1",
) -> CustomHTTPHandler:
    """Create a CustomHTTPHandler instance with mocked socket and server state."""
    handler = CustomHTTPHandler.__new__(CustomHTTPHandler)
    handler.path = path
    handler.requestline = f"GET {path} HTTP/1.1"
    handler.command = "GET"
    handler.request_version = "HTTP/1.1"
    handler.default_request_version = "HTTP/1.1"
    handler.client_address = (client_ip, 54321)
    handler.headers = headers or {}
    handler.rfile = io.BytesIO()
    handler.wfile = io.BytesIO()

    mock_server = MagicMock()
    mock_server.is_https = is_https
    handler.server = mock_server

    return handler


def test_desktop_session_is_one_time():
    """Verify that a desktop bootstrap session can only be consumed once."""
    session = create_desktop_session()
    assert consume_desktop_session(session) is True
    assert consume_desktop_session(session) is False


def test_desktop_session_expires(monkeypatch):
    """Verify that a desktop bootstrap session expires after TTL."""
    current_time = [time.monotonic()]
    monkeypatch.setattr(time, "monotonic", lambda: current_time[0])

    session = create_desktop_session()

    # Advance time past 300s TTL
    current_time[0] += 301.0
    assert consume_desktop_session(session) is False


def test_desktop_access_token():
    """Verify that generated short-lived desktop access tokens match."""
    token = create_desktop_access_token()
    assert _desktop_token_matches(token) is True


def test_invalid_desktop_access_token():
    """Verify that invalid tokens are rejected."""
    assert _desktop_token_matches("invalid-token-12345") is False
    assert _desktop_token_matches(12345) is False
    assert _desktop_token_matches(None) is False


def test_desktop_access_token_expires(monkeypatch):
    """Verify that desktop access tokens expire after TTL."""
    current_time = [time.monotonic()]
    monkeypatch.setattr(time, "monotonic", lambda: current_time[0])

    token = create_desktop_access_token()
    assert _desktop_token_matches(token) is True

    # Advance time past 900s TTL
    current_time[0] += 901.0
    assert _desktop_token_matches(token) is False


@pytest.mark.asyncio
async def test_end_to_end_desktop_authentication_flow():
    """Verify end-to-end desktop authentication flow:

    1. desktop session created
    2. /desktop-session exchange produces short-lived desktop token
    3. HTTP APIs authorized with Bearer <desktop_token>
    4. WebSocket hello authenticated with desktop_token
    """
    # 1. Desktop WebView bootstraps with session
    session = create_desktop_session()

    # 2. Exchange session at /desktop-session
    exchange_handler = _make_dummy_handler(f"/desktop-session?session={session}", client_ip="127.0.0.1")
    exchange_handler.do_GET()

    response_bytes = exchange_handler.wfile.getvalue()
    header_end = response_bytes.find(b"\r\n\r\n")
    assert header_end != -1, "Missing HTTP header separator in response"
    body_json = json.loads(response_bytes[header_end + 4 :].decode("utf-8"))

    assert body_json.get("ok") is True
    assert body_json.get("authenticated") is True
    desktop_token = body_json.get("token")
    assert desktop_token and isinstance(desktop_token, str)

    # 3. HTTP API /api/status request with Bearer <desktop_token>
    api_handler = _make_dummy_handler(
        "/api/status",
        headers={"Authorization": f"Bearer {desktop_token}"},
        client_ip="127.0.0.1",
    )
    api_handler.do_GET()

    api_resp_bytes = api_handler.wfile.getvalue()
    api_header_end = api_resp_bytes.find(b"\r\n\r\n")
    assert api_header_end != -1
    api_data = json.loads(api_resp_bytes[api_header_end + 4 :].decode("utf-8"))

    # When authenticated, full status payload is returned (including primary_ip, ports, clients)
    assert "primary_ip" in api_data
    assert "https_port" in api_data
    assert "clients" in api_data

    # 4. WebSocket hello with desktop_token
    server_instance = GamepadServer()
    mock_ws = AsyncMock()
    mock_ws.remote_address = ("127.0.0.1", 54321)
    mock_ws.transport = MagicMock()
    mock_ws.transport.get_extra_info.return_value = None
    mock_ws.recv = AsyncMock(
        return_value=json.dumps({"type": "hello", "token": desktop_token})
    )
    mock_ws.send = AsyncMock()
    mock_ws.close = AsyncMock()

    class EmptyAsyncIterator:
        def __aiter__(self):
            return self

        async def __anext__(self):
            raise StopAsyncIteration

    mock_ws.__aiter__.side_effect = lambda: EmptyAsyncIterator()

    await server_instance.handle_client(mock_ws)

    # Verify hello_ack was sent
    mock_ws.send.assert_called_once()
    sent_msg = json.loads(mock_ws.send.call_args[0][0])
    assert sent_msg.get("type") == "hello_ack"
    assert sent_msg.get("server") == "PocketPad"
