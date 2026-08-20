import asyncio
import io
import json
import os
import socket
import sys
import threading
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

import qrcode
import websockets
from controller_bridge import GamepadBridge
from ssl_helper import get_ssl_context, get_all_local_ips
from usb_setup import setup_usb_reverse_forwarding
from forza_telemetry import ForzaTelemetryServer

HTTP_PORT = 8000
HTTPS_PORT = 8443
WS_PORT = 8765
WSS_PORT = 8766
FORZA_UDP_PORT = 5300

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

    def do_GET(self):
        if self.path.startswith("/api/status"):
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            active_clients = []
            if GLOBAL_SERVER:
                for c_info in GLOBAL_SERVER.client_infos.values():
                    active_clients.append(c_info)

            data = {
                "primary_ip": get_primary_ip(),
                "all_ips": get_all_local_ips(),
                "https_port": HTTPS_PORT,
                "http_port": HTTP_PORT,
                "ws_port": WS_PORT,
                "telemetry_port": FORZA_UDP_PORT,
                "connected_count": len(active_clients),
                "clients": active_clients
            }
            self.wfile.write(json.dumps(data).encode('utf-8'))
            return

        elif self.path.startswith("/api/joy_cpl"):
            try:
                import subprocess
                subprocess.Popen("joy.cpl", shell=True)
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
            qr_url = f"https://{primary_ip}:{HTTPS_PORT}"
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
        print(f"  ★ ADB Reverse USB Forwarding: ACTIVE!")
        print(f"  👉 Open on phone:  https://localhost:{HTTPS_PORT}  (or http://localhost:{HTTP_PORT})")
    else:
        print(f"  ★ USB Status: {usb_status}")
    
    for ip in all_ips:
        if ip != "127.0.0.1" and (ip.startswith("10.18.") or ip.startswith("192.168.42.") or ip.startswith("172.20.")):
            print(f"  👉 USB Tethering Direct URL:                https://{ip}:{HTTPS_PORT}")

    print("\n[ 📶 METHOD A: WIRELESS 5GHz WI-FI MODE (WMM QoS AC_VO) ]")
    for ip in all_ips:
        if ip != "127.0.0.1" and not (ip.startswith("10.18.") or ip.startswith("192.168.42.") or ip.startswith("172.20.")):
            print(f"  👉 Wi-Fi URL:                               https://{ip}:{HTTPS_PORT}")

    print("\n[ 🏎️ FORZA HORIZON LIVE TELEMETRY DASHBOARD ]")
    print("  ★ Telemetry UDP Port: 5300 (Active)")
    print("  👉 In Forza: Settings ➔ HUD ➔ Data Out = ON, IP = 127.0.0.1, Port = 5300")

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
        self.bridge = GamepadBridge()
        self.connected_clients = set()
        self.client_infos = {}
        self.loop = None
        self.telemetry = ForzaTelemetryServer(port=FORZA_UDP_PORT, broadcast_callback=self.broadcast_telemetry)
        GLOBAL_SERVER = self

    def broadcast_telemetry(self, packet_bytes: bytes):
        """Push binary telemetry frame to all active mobile clients (non-blocking)."""
        if not self.connected_clients:
            return

        try:
            websockets.broadcast(self.connected_clients, packet_bytes)
        except Exception:
            for ws in list(self.connected_clients):
                try:
                    if self.loop and self.loop.is_running():
                        self.loop.create_task(ws.send(packet_bytes))
                except Exception:
                    pass

    def broadcast_device_status(self):
        """Broadcast connected phone count and details to all desktop listeners."""
        phone_clients = [info for info in self.client_infos.values() if not info.get("is_desktop", False)]
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
        is_usb = client_address[0] in ("127.0.0.1", "::1", "localhost") or client_address[0].startswith("10.18.") or client_address[0].startswith("192.168.42.")
        conn_label = "USB Wired (0.2ms)" if is_usb else "Wireless Wi-Fi (WMM QoS)"
        print(f"[+] Client connected: {conn_label} from {client_address}")
        
        self.connected_clients.add(websocket)
        self.client_infos[websocket] = {
            "ip": client_address[0],
            "is_usb": is_usb,
            "label": conn_label,
            "is_desktop": False
        }

        # Broadcast live connection to Desktop Control Center
        self.broadcast_device_status()

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

        try:
            async for message in websocket:
                # FAST PATH: Binary Packet (< 0.0001 ms)
                if isinstance(message, bytes):
                    if len(message) > 0 and message[0] == 0x11: # Opcode 0x11: Toggle Demo Telemetry
                        is_demo = self.telemetry.toggle_demo_mode()
                        await websocket.send(json.dumps({"type": "telemetry_mode", "demo": is_demo}))
                        continue

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
                        self.broadcast_device_status()
                        continue

                    elif msg_type == "button":
                        self.bridge.handle_button(data.get("name", ""), bool(data.get("pressed", False)))

                    elif msg_type == "trigger":
                        trig_name = data.get("name", "").upper()
                        val = float(data.get("value", 0.0))
                        if trig_name == "LT":
                            self.bridge.handle_left_trigger(val)
                        elif trig_name == "RT":
                            self.bridge.handle_right_trigger(val)

                    elif msg_type == "joystick":
                        side = data.get("side", "LEFT").upper()
                        x = float(data.get("x", 0.0))
                        y = float(data.get("y", 0.0))
                        if side == "LEFT":
                            self.bridge.handle_left_joystick(x, y)
                        elif side == "RIGHT":
                            self.bridge.handle_right_joystick(x, y)

                    elif msg_type == "toggle_demo_telemetry":
                        is_demo = self.telemetry.toggle_demo_mode()
                        await websocket.send(json.dumps({"type": "telemetry_mode", "demo": is_demo}))

                    elif msg_type == "ping":
                        await websocket.send(json.dumps({"type": "pong", "t": data.get("t", 0)}))

                except Exception as parse_err:
                    print(f"[!] Error processing packet: {parse_err}")

        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            print(f"[-] Client {client_address} disconnected. Resetting controller inputs.")
            self.connected_clients.discard(websocket)
            self.client_infos.pop(websocket, None)
            self.broadcast_device_status()
            self.bridge.reset()

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

    # 3. Start Gamepad Server & Forza Telemetry UDP Listener
    server_instance = GamepadServer()
    server_instance.loop = loop
    server_instance.telemetry.start_udp_listener(loop)

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
        max_queue=4,
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
        max_queue=4,
        compression=None
    )

    await asyncio.gather(ws_server, wss_server)
    await asyncio.Future()

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n[!] Server shutting down.")
