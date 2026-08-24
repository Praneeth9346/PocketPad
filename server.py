import asyncio
import io
import json
import logging
import os
import socket
import subprocess
import sys
import threading
import time
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

if sys.platform == "win32":
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w", encoding='utf-8')
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding='utf-8')
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

# Structured logging
logging.basicConfig(
    level=logging.INFO,
    format='[%(levelname)s] %(name)s: %(message)s'
)
logger = logging.getLogger("PocketPad")

import qrcode
import websockets

from controller_bridge import GamepadBridge
from ssl_helper import get_all_local_ips, get_ssl_context
from usb_setup import setup_usb_reverse_forwarding

HTTP_PORT = 8000
HTTPS_PORT = 8443
WS_PORT = 8765
WSS_PORT = 8766
CLIENT_HEARTBEAT_TIMEOUT = 8.0  # seconds without any packet → reset controller

import secrets

def get_base_dir() -> Path:
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent

BASE_DIR = get_base_dir()
TOKEN_FILE = BASE_DIR / ".pocketpad_token"

def get_or_create_token() -> str:
    """Auto-generate persistent token on first run."""
    env_token = os.environ.get("POCKETPAD_TOKEN", "")
    if env_token:
        return env_token
    
    try:
        if TOKEN_FILE.exists():
            stored = TOKEN_FILE.read_text().strip()
            if stored:
                return stored
    except Exception:
        pass
    
    token = secrets.token_urlsafe(24)
    try:
        TOKEN_FILE.write_text(token)
        print(f"[Auth] Generated new token: {token}")
        print(f"[Auth] Saved to {TOKEN_FILE}")
    except Exception:
        pass
    return token

EXPECTED_TOKEN = get_or_create_token()

# Global server instance reference for REST API
GLOBAL_SERVER = None

def get_base_dir() -> Path:
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent

def get_web_dir() -> Path:
    if getattr(sys, 'frozen', False):
        exe_dir = Path(sys.executable).parent
        local_web = exe_dir / "web"
        if local_web.exists():
            return local_web
        if hasattr(sys, '_MEIPASS'):
            meipass_web = Path(sys._MEIPASS) / "web"
            if meipass_web.exists():
                return meipass_web
    return Path(__file__).parent / "web"

WEB_DIR = get_web_dir()

def get_primary_ip():
    """Detect primary LAN or USB IP address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = "127.0.0.1"
    finally:
        s.close()
    return ip

class CustomHTTPHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB_DIR), **kwargs)

    def _authorized(self) -> bool:
        # Check Authorization header
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Bearer ") and auth[7:] == EXPECTED_TOKEN:
            return True
        
        # Check query string
        if "?" in self.path:
            query = self.path.split("?", 1)[1]
            for param in query.split("&"):
                if param == f"token={EXPECTED_TOKEN}":
                    return True
        return False
    
    def do_GET(self):
        # Enforce auth on all API endpoints except /api/status (needed for polling)
        if self.path.startswith("/api/") and self.path != "/api/status":
            if not self._authorized():
                self.send_response(401)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(b'{"error":"unauthorized"}')
                return

        if self.path.startswith("/api/status"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            active_clients = []
            if GLOBAL_SERVER:
                for c_info in GLOBAL_SERVER.client_infos.values():
                    if c_info.get("confirmed", False) and not c_info.get("is_desktop", False):
                        active_clients.append(c_info)

            data = {
                "primary_ip": get_primary_ip(),
                "all_ips": get_all_local_ips(),
                "https_port": HTTPS_PORT,
                "http_port": HTTP_PORT,
                "ws_port": WS_PORT,
                "connected_count": len(active_clients),
                "clients": active_clients,
                "controller_available": GLOBAL_SERVER.bridge.controller_available if GLOBAL_SERVER else False,
                "controller_error": GLOBAL_SERVER.bridge.controller_error if GLOBAL_SERVER else "Server not started",
                "auth_required": bool(EXPECTED_TOKEN)
            }
            self.wfile.write(json.dumps(data).encode('utf-8'))
            return

        elif self.path.startswith("/api/joy_cpl"):
            try:
                subprocess.Popen(["control", "joy.cpl"])
                res = {"ok": True}
            except Exception as e:
                res = {"ok": False, "error": str(e)}
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode('utf-8'))
            return

        elif self.path.startswith("/api/restart_adb"):
            try:
                adb_ok, adb_msg = setup_usb_reverse_forwarding()
                res = {"ok": adb_ok, "msg": adb_msg}
            except Exception as e:
                res = {"ok": False, "msg": str(e)}
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(res).encode('utf-8'))
            return

        elif self.path.startswith("/api/qr"):
            primary_ip = get_primary_ip()
            qr_url = f"https://{primary_ip}:{HTTPS_PORT}?token={EXPECTED_TOKEN}"
            qr = qrcode.QRCode(border=1)
            qr.add_data(qr_url)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            png_bytes = buf.getvalue()

            self.send_response(200)
            self.send_header("Content-Type", "image/png")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.wfile.write(png_bytes)
            return

        super().do_GET()

    def log_message(self, format, *args):
        pass

def run_http_server():
    """Run standard HTTP server on port 8000."""
    try:
        server = HTTPServer(('0.0.0.0', HTTP_PORT), CustomHTTPHandler)
        server.serve_forever()
    except Exception as e:
        print(f"[HTTP] Server error: {e}")

def run_https_server(ssl_ctx):
    """Run secure HTTPS server on port 8443."""
    try:
        server = HTTPServer(('0.0.0.0', HTTPS_PORT), CustomHTTPHandler)
        server.socket = ssl_ctx.wrap_socket(server.socket, server_side=True)
        server.serve_forever()
    except Exception as e:
        print(f"[HTTPS] Server error: {e}")

def print_banner(primary_ip: str, all_ips: list, usb_status: str, adb_active: bool):
    """Print ASCII QR Code and direct connection links."""
    https_primary = f"https://{primary_ip}:{HTTPS_PORT}"
    
    print("\n" + "=" * 68)
    print("   🏎️  POCKETPAD - NATIVE C-SPEED FORZA RACING SERVER 🎮")
    print("=" * 68)

    print("\n[ ⚡ METHOD B: USB CABLE DIRECT 0.2ms MODE (RECOMMENDED) ]")
    if adb_active:
        print("  ★ ADB Reverse USB Forwarding: ACTIVE!")
        print(f"  👉 Open on phone:  https://localhost:{HTTPS_PORT}  (or http://localhost:{HTTP_PORT})")
    else:
        print(f"  ★ USB Status: {usb_status}")
    
    for ip in all_ips:
        if ip != "127.0.0.1" and (ip.startswith(("10.18.", "192.168.42.", "172.20."))):
            print(f"  👉 USB Tethering Direct URL:                https://{ip}:{HTTPS_PORT}")

    print("\n[ 📶 METHOD A: WIRELESS 5GHz WI-FI MODE (WMM QoS AC_VO) ]")
    for ip in all_ips:
        if ip != "127.0.0.1" and not (ip.startswith(("10.18.", "192.168.42.", "172.20."))):
            print(f"  👉 Wi-Fi URL:                               https://{ip}:{HTTPS_PORT}")

    print("\n--- Scan QR Code with Phone for Wireless Connection ---")
    try:
        qr = qrcode.QRCode(border=1)
        qr.add_data(https_primary)
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
        self.loop = None
        self.heartbeat_task = None
        GLOBAL_SERVER = self

    def broadcast_telemetry(self, packet_bytes: bytes):
        """Push binary telemetry frame to all active mobile clients (non-blocking)."""
        if not self.connected_clients or not self.loop or not self.loop.is_running():
            return
            
        self.loop.call_soon_threadsafe(self._do_broadcast_telemetry, packet_bytes)

    def _do_broadcast_telemetry(self, packet_bytes: bytes):
        try:
            websockets.broadcast(self.connected_clients, packet_bytes)
        except Exception:
            for ws in list(self.connected_clients):
                try:
                    self.loop.create_task(ws.send(packet_bytes))
                except Exception:
                    pass

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
        phone_clients = [info for info in self.client_infos.values()
                         if info.get("confirmed", False) and not info.get("is_desktop", False)]
        latest_phone = phone_clients[-1] if phone_clients else None

        msg = json.dumps({
            "type": "device_status",
            "connected": bool(latest_phone),
            "client_ip": latest_phone["ip"] if latest_phone else "",
            "is_usb": latest_phone["is_usb"] if latest_phone else False,
            "conn_label": latest_phone["label"] if latest_phone else "Standby",
            "count": len(phone_clients)
        })

        try:
            websockets.broadcast(self.connected_clients, msg)
        except Exception:
            pass

    async def handle_client(self, websocket):
        client_address = websocket.remote_address

        # === TOKEN AUTHENTICATION HANDSHAKE ===
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

                if hello.get("token") != EXPECTED_TOKEN:
                    logger.warning(f"Auth rejected from {client_address}: invalid token")
                    await websocket.close(code=4001, reason="Invalid token")
                    return

                # Send hello_ack
                await websocket.send(json.dumps({
                    "type": "hello_ack",
                    "version": 1,
                    "server": "PocketPad",
                    "controller_available": self.bridge.controller_available
                }))
                logger.info(f"Client {client_address} authenticated successfully")

            except TimeoutError:
                await websocket.close(code=4002, reason="Handshake timeout")
                return
            except Exception as e:
                logger.warning(f"Handshake error from {client_address}: {e}")
                await websocket.close(code=4002, reason="Handshake failed")
                return

        # Start as pending — don't classify or broadcast until identity is confirmed
        self.connected_clients.add(websocket)
        self.client_infos[websocket] = {
            "ip": client_address[0],
            "is_usb": False,
            "label": "Pending",
            "is_desktop": False,
            "confirmed": False  # Not yet identified as phone or desktop
        }
        self.client_last_activity[websocket] = time.monotonic()

        # Apply High-Priority Kernel Socket Options (TCP_NODELAY + IP_TOS 0xB8 Voice QoS + Anti-Bufferbloat)
        try:
            sock = websocket.transport.get_extra_info('socket')
            if sock:
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 4096)
                try:
                    sock.setsockopt(socket.IPPROTO_IP, socket.IP_TOS, 0xB8) # DSCP EF (WMM Voice Priority AC_VO)
                except Exception:
                    pass
        except Exception:
            pass

        def confirm_as_phone():
            """Upgrade a pending connection to a confirmed phone client."""
            info = self.client_infos.get(websocket)
            if info and not info["confirmed"]:
                # ADB reverse connections arrive from loopback ONLY
                is_usb = client_address[0] in ("127.0.0.1", "::1")
                info["is_usb"] = is_usb
                info["label"] = "USB Wired (0.2ms)" if is_usb else "Wireless Wi-Fi (WMM QoS)"
                info["confirmed"] = True
                print(f"[+] Phone Gamepad Connected: {info['label']} from {client_address}")
                self.broadcast_device_status()

        try:
            async for message in websocket:
                # FAST PATH: Binary Packet (< 0.0001 ms)
                if isinstance(message, bytes):
                    # First binary packet confirms this is a phone gamepad
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

                    # Any other JSON message from phone confirms it as a phone
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
                    logger.debug(f"Invalid JSON from {client_address}")
                except Exception as parse_err:
                    logger.warning(f"Error processing packet from {client_address}: {parse_err}")

        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            was_phone = self.client_infos.get(websocket, {}).get("confirmed", False) and not self.client_infos.get(websocket, {}).get("is_desktop", False)
            if was_phone:
                logger.info(f"Phone {client_address} disconnected. Resetting controller inputs.")
            self.connected_clients.discard(websocket)
            self.client_infos.pop(websocket, None)
            self.client_last_activity.pop(websocket, None)
            self.broadcast_device_status()
            if was_phone:
                self.bridge.reset()

    async def _heartbeat_monitor(self):
        """Monitor client heartbeats and reset controller if a phone client goes silent."""
        while True:
            await asyncio.sleep(2.0)
            now = time.monotonic()
            for ws in list(self.connected_clients):
                info = self.client_infos.get(ws)
                if not info or not info.get("confirmed") or info.get("is_desktop"):
                    continue
                last = self.client_last_activity.get(ws, now)
                if now - last > CLIENT_HEARTBEAT_TIMEOUT:
                    logger.warning(f"Client {info.get('ip', '?')} heartbeat timeout. Resetting controller.")
                    self.bridge.reset()
                    try:
                        await ws.close(code=4003, reason="Heartbeat timeout")
                    except Exception:
                        pass

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

    # 5. Start Dual WebSocket Servers (WS 8765 + WSS 8766)
    ws_server = websockets.serve(
        server_instance.handle_client,
        "0.0.0.0",
        WS_PORT,
        ping_interval=None,
        ping_timeout=None,
        max_size=2048,
        max_queue=64,
        compression=None
    )
    wss_server = websockets.serve(
        server_instance.handle_client,
        "0.0.0.0",
        WSS_PORT,
        ssl=ssl_ctx,
        ping_interval=None,
        ping_timeout=None,
        max_size=2048,
        max_queue=64,
        compression=None
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
