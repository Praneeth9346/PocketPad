import asyncio
import os
import subprocess
import sys
import threading
import time
from pathlib import Path

# Fix Windows console UTF-8 output & GUI detached stdio
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

import webview
from server import main as run_server, HTTP_PORT, HTTPS_PORT
from usb_setup import setup_usb_reverse_forwarding

class DesktopAPI:
    """Python API exposed to the JavaScript desktop Control Center."""

    def open_joy_cpl(self):
        """Open Windows Game Controllers panel."""
        try:
            subprocess.Popen("joy.cpl", shell=True)
            return True
        except Exception as e:
            print(f"[Desktop] Could not open joy.cpl: {e}")
            return False

    def restart_adb(self):
        """Restart ADB Reverse Port Forwarding."""
        try:
            ok, msg = setup_usb_reverse_forwarding()
            return {"ok": ok, "msg": msg}
        except Exception as e:
            return {"ok": False, "msg": str(e)}

def start_background_server():
    """Run asyncio Gamepad and Telemetry Server in background thread."""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        loop.run_until_complete(run_server())
    except Exception as e:
        print(f"[Server] Error: {e}")

def main():
    # Check if user requested headless CLI mode
    if "--cli" in sys.argv or "--headless" in sys.argv:
        print("[PocketPad] Starting in headless CLI mode...")
        asyncio.run(run_server())
        return

    # 1. Start Server in Background Thread
    server_thread = threading.Thread(target=start_background_server, daemon=True)
    server_thread.start()

    # Wait for HTTP server to become ready
    time.sleep(0.8)

    # 2. Launch Native Windows 11 Desktop Control Center
    api = DesktopAPI()
    desktop_url = f"http://127.0.0.1:{HTTP_PORT}/desktop.html"

    window = webview.create_window(
        title="PocketPad - Forza Racing Control Center",
        url=desktop_url,
        js_api=api,
        width=980,
        height=660,
        resizable=True,
        min_size=(860, 580),
        background_color="#0a0d14",
    )

    webview.start(gui="edgechromium", debug=False)

if __name__ == "__main__":
    main()
