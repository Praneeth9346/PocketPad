import asyncio
import os
import subprocess
import sys
import threading
import time
import webbrowser
from pathlib import Path

# Fix Windows console UTF-8 output & GUI detached stdio
if sys.platform == "win32":
    if sys.stdout is None:
        sys.stdout = open(os.devnull, "w", encoding="utf-8")
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding="utf-8")
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

import logging
import pystray
import webview
from PIL import Image, ImageDraw

import server
from server import EXPECTED_TOKEN, HTTP_PORT, create_desktop_session
from server import main as run_server
from usb_setup import setup_usb_reverse_forwarding

logger = logging.getLogger("PocketPadDesktop")

# Keep a reference to the main window
main_window = None


def create_image():
    # Generate a simple icon for the system tray
    image = Image.new("RGB", (64, 64), color=(7, 10, 20))
    dc = ImageDraw.Draw(image)
    dc.rectangle((16, 16, 48, 48), fill=(255, 91, 0))  # ForzaOrange
    return image


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

    def repair_driver(self):
        """Open ViGEmBus release page."""
        try:
            webbrowser.open("https://github.com/nefarius/ViGEmBus/releases/latest")
            return True
        except Exception:
            return False

    def toggle_autostart(self, enable):
        """Enable or disable Auto-start via Windows Startup folder."""
        try:
            appdata = os.getenv("APPDATA") or ""
            startup_dir = Path(appdata) / "Microsoft/Windows/Start Menu/Programs/Startup"
            vbs_path = startup_dir / "PocketPadHost.vbs"
            if enable:
                exe_path = sys.executable if not getattr(sys, "frozen", False) else sys.executable
                script_path = os.path.abspath(sys.argv[0])

                vbs_content = 'Set WshShell = CreateObject("WScript.Shell")\n'
                if getattr(sys, "frozen", False):
                    vbs_content += f'WshShell.Run chr(34) & "{exe_path}" & chr(34), 0\n'
                else:
                    vbs_content += (
                        f'WshShell.Run chr(34) & "{exe_path}" & chr(34) & " " & '
                        f'chr(34) & "{script_path}" & chr(34), 0\n'
                    )

                with open(vbs_path, "w") as f:
                    f.write(vbs_content)
                return True
            else:
                if vbs_path.exists():
                    os.remove(vbs_path)
                return True
        except Exception as e:
            print(f"Auto-start error: {e}")
            return False


def on_closing():
    # Hide window instead of closing
    if main_window:
        main_window.hide()
    return False


def show_window(icon, item):
    if main_window:
        main_window.show()
        main_window.restore()


def hide_window(icon, item):
    if main_window:
        main_window.hide()


def exit_app(icon=None, item=None):
    try:
        if icon is not None:
            icon.stop()
    except Exception:
        pass

    try:
        if server.GLOBAL_SERVER is not None:
            server.GLOBAL_SERVER.shutdown_sync()
    except Exception:
        logger.exception("Failed to shutdown server cleanly")

    try:
        if main_window is not None:
            main_window.destroy()
    except Exception:
        pass


def setup_tray():
    menu = pystray.Menu(
        pystray.MenuItem("Open Control Center", show_window, default=True),
        pystray.MenuItem("Hide to Tray", hide_window),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Exit PocketPad", exit_app),
    )
    icon = pystray.Icon("PocketPad", create_image(), "PocketPad Host Server", menu)
    icon.run()


def start_webview():
    global main_window
    api = DesktopAPI()
    # Generate a short-lived, one-time session token — never expose the master token in a URL.
    desktop_session = create_desktop_session()
    url = f"http://127.0.0.1:{HTTP_PORT}/desktop.html?session={desktop_session}"

    main_window = webview.create_window(
        title="PocketPad Host & Control Center",
        url=url,
        js_api=api,
        width=1000,
        height=680,
        resizable=True,
        min_size=(800, 550),
        background_color="#0b0e14",
    )

    main_window.events.closing += on_closing
    webview.start(debug=False)


def main():
    print("[*] Launching PocketPad Standalone Host...")

    # Start Asyncio WebSocket + HTTP Web Server in background thread
    server_thread = threading.Thread(
        target=lambda: asyncio.run(run_server()),
        daemon=True,
    )
    server_thread.start()

    # USB ADB Port Forwarding
    try:
        usb_ok, usb_msg = setup_usb_reverse_forwarding()
        if usb_ok:
            print(f"[USB] {usb_msg}")
    except Exception as e:
        print(f"[USB] Note: {e}")

    # Start System Tray in background thread
    tray_thread = threading.Thread(target=setup_tray, daemon=True)
    tray_thread.start()

    # Allow server 1 second to spin up
    time.sleep(1.0)

    # Launch PyWebView Window on Main GUI Thread (required for Windows)
    start_webview()


if __name__ == "__main__":
    main()
