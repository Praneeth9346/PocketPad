import asyncio
import atexit
import io
import json
import logging
import os
import secrets
import socket
import subprocess
import sys
import threading
import time
import urllib.parse
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

import qrcode
import websockets

from controller_bridge import GamepadBridge
from paths import BASE_DIR, get_base_dir
from ssl_helper import get_all_local_ips, get_ssl_context
from usb_setup import setup_usb_reverse_forwarding

__all__ = ["get_base_dir", "GamepadServer", "EXPECTED_TOKEN", "PROTOCOL_VERSION", "is_usb_client"]

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s", datefmt="%H:%M:%S")
logger = logging.getLogger("PocketPad")

# Configurable Network & Protocol Constants
BIND_HOST = os.environ.get("POCKETPAD_BIND_HOST", "0.0.0.0")
HTTP_PORT = int(os.environ.get("POCKETPAD_HTTP_PORT", "8000"))
HTTPS_PORT = int(os.environ.get("POCKETPAD_HTTPS_PORT", "8443"))
WS_PORT = int(os.environ.get("POCKETPAD_WS_PORT", "8765"))
WSS_PORT = int(os.environ.get("POCKETPAD_WSS_PORT", "8766"))
PROTOCOL_VERSION = 1

# Security constants
PAIRING_PATH = "/pair"
WS_LOOPBACK_HOST = "127.0.0.1"   # plain WS is loopback-only
WSS_BIND_HOST = BIND_HOST         # WSS serves the full LAN
CLIENT_HEARTBEAT_TIMEOUT = 8.0  # seconds without any packet → reset controller
MAX_WS_MESSAGE_SIZE = 64 * 1024  # 64 KB

ALLOWED_CORS_ORIGINS = {
    "http://localhost",
    "http://127.0.0.1",
    "https://localhost",
    "https://127.0.0.1",
}

TOKEN_FILE = BASE_DIR / ".pocketpad_token"


def write_token_securely(token: str):
    """Write authentication token to disk with restricted permissions."""
    TOKEN_FILE.write_text(token, encoding="utf-8")
    try:
        os.chmod(TOKEN_FILE, 0o600)
    except OSError:
        pass


def get_or_create_token() -> str:
    """Auto-generate persistent token on first run."""
    env_token = os.environ.get("POCKETPAD_TOKEN", "")
    if env_token:
        return env_token

    try:
        if TOKEN_FILE.exists():
            stored = TOKEN_FILE.read_text(encoding="utf-8").strip()
            if stored:
                return stored
    except Exception:
        pass

    token = secrets.token_urlsafe(32)
    try:
        write_token_securely(token)
        logger.info("[Auth] Generated new authentication token.")
        logger.info("[Auth] Token saved to secure local storage.")
    except Exception:
        pass
    return token


EXPECTED_TOKEN = get_or_create_token()


def rotate_token() -> str:
    """Rotate security token programmatically."""
    global EXPECTED_TOKEN
    token = secrets.token_urlsafe(32)
    write_token_securely(token)
    EXPECTED_TOKEN = token
    logger.info("[Auth] Authentication token rotated successfully.")
    return token


def _token_matches(candidate) -> bool:
    if not isinstance(candidate, str):
        return False
    return secrets.compare_digest(candidate, EXPECTED_TOKEN)


# Global server instance reference for REST API
GLOBAL_SERVER = None


@atexit.register
def emergency_reset():
    """Crash-safe emergency controller reset on process termination."""
    try:
        if GLOBAL_SERVER and GLOBAL_SERVER.bridge:
            GLOBAL_SERVER.bridge.reset()
    except Exception:
        pass


def get_web_dir() -> Path:
    if getattr(sys, "frozen", False):
        exe_dir = Path(sys.executable).parent
        local_web = exe_dir / "web"
        if local_web.exists():
            return local_web
        if hasattr(sys, "_MEIPASS"):
            meipass_web = Path(sys._MEIPASS) / "web"
            if meipass_web.exists():
                return meipass_web
    return Path(__file__).parent / "web"


WEB_DIR = get_web_dir()


def get_primary_ip():
    """Detect primary LAN or USB IP address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip


USB_LOOPBACK_HOSTS = {
    "127.0.0.1",
    "::1",
    "localhost",
}


def is_usb_client(client_ip: str) -> bool:
    """Check if the client IP is a USB loopback or tethering address."""
    return client_ip in USB_LOOPBACK_HOSTS or client_ip.startswith(("10.18.", "192.168.42.", "172.20."))


def get_connection_endpoints() -> dict:
    """Return explicit connection endpoint configurations for USB and Wi-Fi."""
    primary_ip = get_primary_ip()
    return {
        "usb": {
            "host": "127.0.0.1",
            "https_port": HTTPS_PORT,
            "url": f"https://127.0.0.1:{HTTPS_PORT}",
        },
        "wifi": {
            "host": primary_ip,
            "https_port": HTTPS_PORT,
            "url": f"https://{primary_ip}:{HTTPS_PORT}",
        },
    }


PAIRING_TTL = 120.0
PAIRING_TOKENS: dict[str, float] = {}


def create_pairing_token() -> str:
    """Generate a short-lived, single-use pairing token for QR code exchange."""
    token = secrets.token_urlsafe(24)
    PAIRING_TOKENS[token] = time.monotonic() + PAIRING_TTL
    return token


def consume_pairing_token(token: str) -> bool:
    """Validate and immediately invalidate a single-use pairing token."""
    expiry = PAIRING_TOKENS.pop(token, None)
    if expiry is None:
        return False
    return time.monotonic() < expiry


def send_security_headers(handler: SimpleHTTPRequestHandler):
    """Inject standard security response headers."""
    handler.send_header("X-Content-Type-Options", "nosniff")
    handler.send_header("X-Frame-Options", "DENY")
    handler.send_header("Referrer-Policy", "no-referrer")
    handler.send_header("Cache-Control", "no-store")


def require_auth(handler: SimpleHTTPRequestHandler) -> bool:
    """Validate Bearer token or URL token; send 401 if unauthorized."""
    if handler._authorized():
        return True

    handler.send_response(401)
    send_security_headers(handler)
    handler.send_header("Content-Type", "application/json")
    handler.end_headers()
    handler.wfile.write(b'{"error":"unauthorized"}')
    return False


class CustomHTTPHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def _is_secure_request(self) -> bool:
        """
        Return True only when this request was received by the HTTPS server.

        HTTP and HTTPS use the same handler class, so the server instance
        marks the handler with `is_https`.
        """
        return bool(
            getattr(self.server, "is_https", False)
        )

    def _is_loopback_client(self) -> bool:
        client_ip = self.client_address[0]
        return client_ip in {"127.0.0.1", "::1"}

    def send_json(self, data: dict, status: int = 200) -> None:
        """Helper to write a JSON response with security headers."""
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        send_security_headers(self)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self) -> bool:
        # Check Authorization header (preferred)
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer ") and _token_matches(auth[7:].strip()):
            return True

        # Check query string (for QR bootstrap only)
        if "?" in self.path:
            query = self.path.split("?", 1)[1]
            for param in query.split("&"):
                if param.startswith("token=") and _token_matches(param[6:].strip()):
                    return True
        return False

    def do_GET(self):
        # 1. Status API (Sanitized for unauthenticated, full for authenticated)
        if self.path.startswith("/api/status"):
            is_auth = self._authorized()
            active_clients = []
            if GLOBAL_SERVER:
                for c_info in GLOBAL_SERVER.client_infos.values():
                    if c_info.get("confirmed", False) and not c_info.get("is_desktop", False):
                        if is_auth:
                            active_clients.append(c_info)
                        else:
                            active_clients.append({"label": c_info.get("label", "Client")})

            self.send_response(200)
            send_security_headers(self)
            self.send_header("Content-Type", "application/json")

            origin = self.headers.get("Origin", "")
            if any(origin.startswith(allowed) for allowed in ALLOWED_CORS_ORIGINS):
                self.send_header("Access-Control-Allow-Origin", origin)

            self.end_headers()

            if is_auth:
                data = {
                    "primary_ip": get_primary_ip(),
                    "all_ips": get_all_local_ips(),
                    "https_port": HTTPS_PORT,
                    "http_port": HTTP_PORT,
                    "ws_port": WS_PORT,
                    "wss_port": WSS_PORT,
                    "connected_count": len(active_clients),
                    "clients": active_clients,
                    "controller_available": GLOBAL_SERVER.bridge.controller_available if GLOBAL_SERVER else False,
                    "controller_error": (
                        GLOBAL_SERVER.bridge.controller_error if GLOBAL_SERVER else "Server not started"
                    ),
                    "auth_required": bool(EXPECTED_TOKEN),
                    "protocol_version": PROTOCOL_VERSION,
                }
            else:
                data = {
                    "connected_count": len(active_clients),
                    "controller_available": GLOBAL_SERVER.bridge.controller_available if GLOBAL_SERVER else False,
                    "auth_required": True,
                    "protocol_version": PROTOCOL_VERSION,
                }

            self.wfile.write(json.dumps(data).encode("utf-8"))
            return

        # 2. Joystick Control Panel API (Protected)
        elif self.path.startswith("/api/joy_cpl"):
            if not require_auth(self):
                return
            try:
                subprocess.Popen(["control", "joy.cpl"])
                res = {"ok": True}
            except Exception as e:
                res = {"ok": False, "error": str(e)}
            self.send_response(200)
            send_security_headers(self)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode("utf-8"))
            return

        # 3. Restart ADB Reverse Port Forwarding API (Protected)
        elif self.path.startswith("/api/restart_adb"):
            if not require_auth(self):
                return
            try:
                adb_ok, adb_msg = setup_usb_reverse_forwarding()
                res = {"ok": adb_ok, "msg": adb_msg}
            except Exception as e:
                res = {"ok": False, "msg": str(e)}
            self.send_response(200)
            send_security_headers(self)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode("utf-8"))
            return

        # 4. Token Rotation API (Protected)
        elif self.path.startswith("/api/rotate_token"):
            if not require_auth(self):
                return
            rotate_token()
            res = {"ok": True, "rotated": True}
            self.send_response(200)
            send_security_headers(self)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode("utf-8"))
            return

        # 5. Pairing Code Exchange API — HTTPS only
        elif self.path.startswith(PAIRING_PATH):
            # Pairing MUST only happen over HTTPS.
            if not self._is_secure_request():
                self.send_error(
                    403,
                    "Pairing requires HTTPS.",
                )
                return

            parsed = urllib.parse.urlparse(self.path)
            params = urllib.parse.parse_qs(parsed.query)

            code = params.get("code", [None])[0]

            if not code:
                self.send_json(
                    {"ok": False, "error": "missing_pairing_code"},
                    status=400,
                )
                return

            if not consume_pairing_token(code):
                self.send_json(
                    {"ok": False, "error": "invalid_or_expired_pairing_code"},
                    status=401,
                )
                return

            self.send_json({"ok": True, "token": EXPECTED_TOKEN})
            return

        # 6. Connection QR Code Image (Protected)
        elif self.path.startswith("/api/qr"):
            if not require_auth(self):
                return
            primary_ip = get_primary_ip()
            pairing_code = create_pairing_token()
            qr_url = f"https://{primary_ip}:{HTTPS_PORT}/pair?code={pairing_code}"
            qr = qrcode.QRCode(border=1)
            qr.add_data(qr_url)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            png_bytes = buf.getvalue()

            self.send_response(200)
            send_security_headers(self)
            self.send_header("Content-Type", "image/png")
            self.end_headers()
            self.wfile.write(png_bytes)
            return

        # 7. Static Web Assets
        super().do_GET()

    def log_message(self, format, *args):
        pass


def run_http_server():
    """Run standard HTTP server on port 8000 (loopback and LAN, no pairing)."""
    try:
        server = HTTPServer((BIND_HOST, HTTP_PORT), CustomHTTPHandler)
        server.is_https = False
        server.serve_forever()
    except Exception as e:
        logger.error(f"[HTTP] Server error: {e}")


def run_https_server(ssl_ctx):
    """Run secure HTTPS server on port 8443 (full LAN, pairing allowed)."""
    try:
        server = HTTPServer((BIND_HOST, HTTPS_PORT), CustomHTTPHandler)
        server.socket = ssl_ctx.wrap_socket(server.socket, server_side=True)
        server.is_https = True
        server.serve_forever()
    except Exception as e:
        logger.error(f"[HTTPS] Server error: {e}")


def print_banner(primary_ip: str, all_ips: list, usb_status: str, adb_active: bool):
    """Print ASCII QR Code and direct connection links."""
    endpoints = get_connection_endpoints()

    print("\n" + "=" * 68)
    print("   🏎️  POCKETPAD - NATIVE C-SPEED FORZA RACING SERVER 🎮")
    print("=" * 68)

    print("\n[ ⚡ METHOD B: USB CABLE DIRECT MODE (RECOMMENDED) ]")
    if adb_active:
        print("  ★ ADB Reverse USB Forwarding: ACTIVE!")
        print(f"  👉 Open on phone:  https://localhost:{HTTPS_PORT}  (or http://localhost:{HTTP_PORT})")
    else:
        print(f"  ★ USB Status: {usb_status}")
    print(f"  👉 USB:                                     {endpoints['usb']['url']}")
    for ip in all_ips:
        if ip != "127.0.0.1" and ip.startswith(("10.18.", "192.168.42.", "172.20.")):
            print(f"  👉 USB Tethering Direct URL:                https://{ip}:{HTTPS_PORT}")

    print("\n[ 📶 METHOD A: WIRELESS 5GHz WI-FI MODE (WMM QoS) ]")
    print(f"  👉 Wi-Fi:                                   {endpoints['wifi']['url']}")
    for ip in all_ips:
        if ip != "127.0.0.1" and not ip.startswith(("10.18.", "192.168.42.", "172.20.")) and ip != primary_ip:
            print(f"  👉 Additional Wi-Fi URL:                    https://{ip}:{HTTPS_PORT}")

    print("\n--- Scan QR Code with Phone for Wireless Connection ---")
    try:
        qr = qrcode.QRCode(border=1)
        qr.add_data(endpoints["wifi"]["url"])
        qr.make(fit=True)
        f = io.StringIO()
        qr.print_ascii(out=f, invert=True)
        print(f.getvalue())
    except Exception as e:
        print(f"(Could not render ASCII QR code: {e})")

    print("=" * 68)
    print("⚡ C-SPEED BINARY PROTOCOL + WMM VOICE QoS (AC_VO) RUNNING\n")


class GamepadServer:
    def __init__(self):
        global GLOBAL_SERVER
        self.bridge = GamepadBridge(rumble_callback=self.broadcast_rumble)
        self.connected_clients = set()
        self.client_infos = {}
        self.client_last_activity = {}
        self.auth_failures = {}
        self.loop = None
        self.heartbeat_task = None
        GLOBAL_SERVER = self

    def broadcast_rumble(self, large_motor: int, small_motor: int):
        """Push binary rumble event to all active mobile clients."""
        if not self.connected_clients or not self.loop or not self.loop.is_running():
            return

        packet = bytes([0x0B, large_motor, small_motor])
        self.loop.call_soon_threadsafe(self._do_broadcast_rumble, packet)

    def _do_broadcast_rumble(self, packet: bytes):
        try:
            websockets.broadcast(self.connected_clients, packet)
        except Exception:
            for ws in list(self.connected_clients):
                try:
                    self.loop.create_task(ws.send(packet))
                except Exception:
                    pass

    def broadcast_device_status(self):
        """Broadcast connected phone count and details to all desktop listeners."""
        phone_clients = [
            info
            for info in self.client_infos.values()
            if info.get("confirmed", False) and not info.get("is_desktop", False)
        ]
        latest_phone = phone_clients[-1] if phone_clients else None

        msg = json.dumps(
            {
                "type": "device_status",
                "connected": bool(latest_phone),
                "client_ip": latest_phone["ip"] if latest_phone else "",
                "is_usb": latest_phone["is_usb"] if latest_phone else False,
                "label": latest_phone["label"] if latest_phone else "Disconnected",
                "phone_count": len(phone_clients),
            }
        )

        desktop_clients = [ws for ws, info in self.client_infos.items() if info.get("is_desktop", False)]
        for ws in desktop_clients:
            try:
                self.loop.create_task(ws.send(msg))
            except Exception:
                pass

    async def shutdown(self):
        """Gracefully terminate background tasks and close active client sockets."""
        if self.heartbeat_task:
            self.heartbeat_task.cancel()

        for ws in list(self.connected_clients):
            try:
                await ws.close(code=1001, reason="Server shutting down")
            except Exception:
                pass

        self.bridge.reset()

    async def handle_client(self, websocket):
        """Handle incoming WebSocket connections."""
        client_address = websocket.remote_address
        client_ip = client_address[0] if client_address else "unknown"

        # 1. Rate-limit brute-force attempts per IP
        if self.auth_failures.get(client_ip, 0) >= 10:
            logger.warning("Auth blocked: too many failed attempts from %s", client_ip)
            await websocket.close(code=4003, reason="Too many auth failures")
            return

        # 2. Enforce Authentication Handshake via first message
        if EXPECTED_TOKEN:
            try:
                raw = await asyncio.wait_for(websocket.recv(), timeout=5.0)
                if isinstance(raw, str):
                    hello = json.loads(raw)
                else:
                    await websocket.close(code=4000, reason="Expected JSON hello")
                    return

                if hello.get("type") != "hello":
                    await websocket.close(code=4000, reason="Expected hello message")
                    return

                if not _token_matches(hello.get("token", "")):
                    self.auth_failures[client_ip] = self.auth_failures.get(client_ip, 0) + 1
                    logger.warning("Authentication rejected from %s: invalid token", client_ip)
                    await websocket.close(code=4001, reason="Invalid token")
                    return

                # Auth succeeded, reset failure count
                self.auth_failures.pop(client_ip, None)

                # Send hello_ack with protocol version
                await websocket.send(
                    json.dumps(
                        {
                            "type": "hello_ack",
                            "version": PROTOCOL_VERSION,
                            "server": "PocketPad",
                            "controller_available": self.bridge.controller_available,
                        }
                    )
                )
                logger.info("Client authenticated successfully from %s", client_ip)

            except TimeoutError:
                await websocket.close(code=4002, reason="Handshake timeout")
                return
            except Exception as e:
                logger.warning("Handshake error from %s: %s", client_ip, e)
                await websocket.close(code=4002, reason="Handshake failed")
                return

        # 3. Connection State Registration
        self.connected_clients.add(websocket)
        self.client_infos[websocket] = {
            "ip": client_ip,
            "is_usb": False,
            "label": "Pending",
            "is_desktop": False,
            "confirmed": False,
        }
        self.client_last_activity[websocket] = time.monotonic()

        # High-Priority Kernel Socket Options (TCP_NODELAY + IP_TOS 0xB8 Voice QoS + Anti-Bufferbloat)
        try:
            sock = websocket.transport.get_extra_info("socket")
            if sock:
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4096)
                try:
                    sock.setsockopt(socket.IPPROTO_IP, socket.IP_TOS, 0xB8)  # DSCP EF (WMM Voice Priority AC_VO)
                except Exception:
                    pass
        except Exception:
            pass

        def confirm_as_phone():
            """Upgrade a pending connection to a confirmed phone client."""
            info = self.client_infos.get(websocket)
            if info and not info["confirmed"]:
                is_usb = is_usb_client(client_ip)
                info["is_usb"] = is_usb
                info["label"] = "USB Wired" if is_usb else "Wireless Wi-Fi (WMM QoS)"
                info["confirmed"] = True
                logger.info("Phone Gamepad Connected: %s from %s", info["label"], client_ip)
                self.broadcast_device_status()

        try:
            async for message in websocket:
                # FAST PATH: binary packet dispatch
                if isinstance(message, bytes):
                    confirm_as_phone()
                    self.client_last_activity[websocket] = time.monotonic()

                    resp = self.bridge.handle_binary_packet(message)
                    if resp:
                        await websocket.send(resp)
                    continue

                # FALLBACK PATH: JSON Text
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")

                    if msg_type == "desktop_init":
                        if websocket in self.client_infos:
                            self.client_infos[websocket]["is_desktop"] = True
                            self.client_infos[websocket]["confirmed"] = True
                            self.client_infos[websocket]["label"] = "Desktop UI Monitor"
                        self.broadcast_device_status()
                        continue

                    confirm_as_phone()
                    self.client_last_activity[websocket] = time.monotonic()

                    if msg_type == "button":
                        btn_name = data.get("name", "")
                        pressed = data.get("pressed", False)
                        if isinstance(btn_name, str) and isinstance(pressed, bool):
                            self.bridge.handle_button(btn_name, pressed)

                    elif msg_type == "trigger":
                        trig_name = data.get("name", "")
                        val = data.get("value", 0.0)
                        if isinstance(trig_name, str) and isinstance(val, (int, float)):
                            trig_name = trig_name.upper()
                            val = float(val)
                            if trig_name == "LT":
                                self.bridge.handle_left_trigger(val)
                            elif trig_name == "RT":
                                self.bridge.handle_right_trigger(val)

                    elif msg_type == "joystick":
                        side = data.get("side", "LEFT")
                        x = data.get("x", 0.0)
                        y = data.get("y", 0.0)
                        if isinstance(side, str) and isinstance(x, (int, float)) and isinstance(y, (int, float)):
                            side = side.upper()
                            if side == "LEFT":
                                self.bridge.handle_left_joystick(float(x), float(y))
                            elif side == "RIGHT":
                                self.bridge.handle_right_joystick(float(x), float(y))

                    elif msg_type == "ping":
                        t = data.get("t", 0)
                        await websocket.send(json.dumps({"type": "pong", "t": t}))

                except json.JSONDecodeError:
                    logger.debug("Invalid JSON from %s", client_ip)
                except Exception as parse_err:
                    logger.warning("Error processing packet from %s: %s", client_ip, parse_err)

        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            was_phone = self.client_infos.get(websocket, {}).get("confirmed", False) and not self.client_infos.get(
                websocket, {}
            ).get("is_desktop", False)
            if was_phone:
                logger.info("Phone %s disconnected. Resetting controller inputs.", client_ip)
            self.connected_clients.discard(websocket)
            self.client_infos.pop(websocket, None)
            self.client_last_activity.pop(websocket, None)
            self.broadcast_device_status()
            if was_phone:
                self.bridge.reset()

    async def check_heartbeats(self):
        """Check client heartbeats and reset controller if a phone client goes silent."""
        now = time.monotonic()
        for ws in list(self.connected_clients):
            info = self.client_infos.get(ws)
            if not info or not info.get("confirmed") or info.get("is_desktop"):
                continue
            last = self.client_last_activity.get(ws, now)
            if now - last > CLIENT_HEARTBEAT_TIMEOUT:
                logger.warning("Client %s heartbeat timeout. Resetting controller.", info.get("ip", "?"))
                self.bridge.reset()
                try:
                    await ws.close(code=4003, reason="Heartbeat timeout")
                except Exception:
                    pass

    async def _heartbeat_monitor(self):
        """Monitor client heartbeats and reset controller if a phone client goes silent."""
        while True:
            await asyncio.sleep(2.0)
            await self.check_heartbeats()


async def main():
    loop = asyncio.get_running_loop()
    primary_ip = get_primary_ip()
    all_ips = get_all_local_ips()
    ssl_ctx = get_ssl_context(primary_ip)

    # 1. Setup ADB Reverse Port Forwarding
    adb_ok, adb_msg = setup_usb_reverse_forwarding()

    # 2. Start HTTP & HTTPS Servers in background threads
    http_thread = threading.Thread(target=run_http_server, daemon=True)
    http_thread.start()

    https_thread = threading.Thread(target=run_https_server, args=(ssl_ctx,), daemon=True)
    https_thread.start()

    # 3. Start Gamepad Server
    server_instance = GamepadServer()
    server_instance.loop = loop

    # Start heartbeat monitor
    server_instance.heartbeat_task = loop.create_task(server_instance._heartbeat_monitor())

    # 4. Print Unified Dashboard
    print_banner(primary_ip, all_ips, adb_msg, adb_ok)

    # 5. Start Dual WebSocket Servers
    #    WS  8765 → loopback only (USB / local dev)
    #    WSS 8766 → LAN (production Wi-Fi path)
    ws_server = websockets.serve(
        server_instance.handle_client,
        WS_LOOPBACK_HOST,
        WS_PORT,
        max_size=MAX_WS_MESSAGE_SIZE,
        max_queue=64,
        compression=None,
        ping_interval=None,
        ping_timeout=None,
    )
    wss_server = websockets.serve(
        server_instance.handle_client,
        WSS_BIND_HOST,
        WSS_PORT,
        ssl=ssl_ctx,
        max_size=MAX_WS_MESSAGE_SIZE,
        max_queue=64,
        compression=None,
        ping_interval=None,
        ping_timeout=None,
    )

    await asyncio.gather(ws_server, wss_server)
    await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Server shutting down.")
        if GLOBAL_SERVER:
            GLOBAL_SERVER.bridge.reset()
