import io
import json
import time
from unittest.mock import MagicMock

import pytest

import server
from server import (
    BIND_HOST,
    CustomHTTPHandler,
    DESKTOP_SESSIONS,
    EXPECTED_TOKEN,
    GamepadServer,
    WS_LOOPBACK_HOST,
    WSS_BIND_HOST,
    consume_desktop_session,
    create_desktop_session,
    create_pairing_token,
)


def _make_dummy_handler(path: str, is_https: bool = False, client_ip: str = "127.0.0.1") -> CustomHTTPHandler:
    """Create a CustomHTTPHandler instance with mocked socket and server state."""
    handler = CustomHTTPHandler.__new__(CustomHTTPHandler)
    handler.path = path
    handler.requestline = f"GET {path} HTTP/1.1"
    handler.command = "GET"
    handler.request_version = "HTTP/1.1"
    handler.default_request_version = "HTTP/1.1"
    handler.client_address = (client_ip, 54321)
    handler.headers = {}
    handler.rfile = io.BytesIO()
    handler.wfile = io.BytesIO()

    mock_server = MagicMock()
    mock_server.is_https = is_https
    handler.server = mock_server

    return handler


def test_http_pairing_is_rejected():
    """Verify that pairing requests over plain HTTP are strictly rejected with 403 Forbidden."""
    pairing_code = create_pairing_token()
    handler = _make_dummy_handler(f"/pair?code={pairing_code}", is_https=False)

    # Calling do_GET should trigger send_error(403)
    handler.send_error = MagicMock()
    handler.do_GET()

    handler.send_error.assert_called_once()
    status_code, message = handler.send_error.call_args[0][:2]
    assert status_code == 403
    assert "HTTPS" in message


def test_https_pairing_is_allowed_with_valid_code():
    """Verify that pairing requests over HTTPS with a valid code succeed."""
    pairing_code = create_pairing_token()
    handler = _make_dummy_handler(f"/pair?code={pairing_code}", is_https=True)

    handler.do_GET()

    response_bytes = handler.wfile.getvalue()
    # Response contains header and json body
    assert b"200" in response_bytes or b'"ok": true' in response_bytes or b'"ok":true' in response_bytes
    assert EXPECTED_TOKEN.encode("utf-8") in response_bytes


def test_plain_ws_is_loopback_only():
    """Verify plain WebSocket host configuration is restricted to loopback."""
    assert WS_LOOPBACK_HOST == "127.0.0.1"
    assert WSS_BIND_HOST == BIND_HOST


def test_desktop_session_expires():
    """Verify that expired desktop session tokens are rejected."""
    session = create_desktop_session()
    assert session in DESKTOP_SESSIONS

    # Manually expire the session
    DESKTOP_SESSIONS[session] = time.monotonic() - 10.0

    assert not consume_desktop_session(session)
    assert session not in DESKTOP_SESSIONS


def test_desktop_session_is_one_time():
    """Verify that desktop session tokens can only be consumed once."""
    session = create_desktop_session()
    assert session in DESKTOP_SESSIONS

    # First consumption succeeds
    assert consume_desktop_session(session) is True

    # Second consumption fails
    assert consume_desktop_session(session) is False


def test_desktop_session_is_loopback_only():
    """Verify that /desktop-session is blocked from non-loopback IPs."""
    # Loopback IP
    handler_local = _make_dummy_handler("/desktop-session", client_ip="127.0.0.1")
    assert handler_local._is_loopback_client() is True

    # IPv6 Loopback
    handler_ipv6 = _make_dummy_handler("/desktop-session", client_ip="::1")
    assert handler_ipv6._is_loopback_client() is True

    # External LAN IP
    handler_lan = _make_dummy_handler("/desktop-session", client_ip="192.168.1.100")
    assert handler_lan._is_loopback_client() is False

    # Calling do_GET on non-loopback sends 403 error
    handler_lan.send_error = MagicMock()
    handler_lan.do_GET()
    handler_lan.send_error.assert_called_once()
    assert handler_lan.send_error.call_args[0][0] == 403


def test_server_shutdown_sync_resets_controller():
    """Verify GamepadServer.shutdown_sync() safely resets the bridge and flags running=False."""
    server_instance = GamepadServer()
    server_instance.bridge = MagicMock()

    assert server_instance.running is True
    server_instance.shutdown_sync()

    server_instance.bridge.reset.assert_called_once()
    assert server_instance.running is False
