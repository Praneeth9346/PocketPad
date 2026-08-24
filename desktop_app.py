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
        sys.stdout = open(os.devnull, "w", encoding='utf-8')
    if sys.stderr is None:
        sys.stderr = open(os.devnull, "w", encoding='utf-8')
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import pystray
from PIL import Image, ImageDraw
import webview

import server
from server import HTTP_PORT, EXPECTED_TOKEN
from server import main as run_server
from usb_setup import setup_usb_reverse_forwarding

# Keep a reference to the main window
main_window = None

def create_image():
    # Generate a simple icon for the system tray
    image = Image.new('RGB', (64, 64), color = (7, 10, 20))
    dc = ImageDraw.Draw(image)
    dc.rectangle(
        (16, 16, 48, 48),
        fill=(255, 91, 0) # ForzaOrange
    )
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
        except:
            return False

    def toggle_autostart(self, enable):
        """Enable or disable Auto-start via Windows Startup folder."""
        try:
            startup_dir = Path(os.getenv('APPDATA')) / "Microsoft/Windows/Start Menu/Programs/Startup"
            vbs_path = startup_dir / "PocketPadHost.vbs"
            if enable:
                # Create a VBS script to run the exe silently if we are compiled, or python if not
                exe_path = sys.executable if not getattr(sys, 'frozen', False) else sys.executable
                script_path = os.path.abspath(sys.argv[0])
                
                vbs_content = f'Set WshShell = CreateObject("WScript.Shell")\n'
                if getattr(sys, 'frozen', False):
                    vbs_content += f'WshShell.Run chr(34) & "{exe_path}" & chr(34), 0\n'
                else:
                    vbs_content += f'WshShell.Run chr(34) & "{exe_path}" & chr(34) & " " & chr(34) & "{script_path}" & chr(34), 0\n'
                
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
    # return False cancels the close event
    return False

def show_window(icon, item):
    if main_window:
        main_window.show()

def exit_app(icon, item):
    icon.stop()
    if main_window:
        main_window.destroy()
    if server.GLOBAL_SERVER:
        server.GLOBAL_SERVER.bridge.shutdown()
        if server.GLOBAL_SERVER.loop:
            server.GLOBAL_SERVER.loop.call_soon_threadsafe(server.GLOBAL_SERVER.loop.stop)

def setup_tray():
    icon = pystray.Icon("PocketPad", create_image(), "PocketPad Pro", menu=pystray.Menu(
        pystray.MenuItem("Show Window", show_window, default=True),
        pystray.MenuItem("Exit", exit_app)
    ))
    icon.run()

def start_background_server():
    """Run asyncio Gamepad and Telemetry Server in background thread."""
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    try:
        loop.run_until_complete(run_server())
    except Exception as e:
        print(f"[Server] Error: {e}")

def main():
    global main_window
    
    # Check if user requested headless CLI mode
    if "--cli" in sys.argv or "--headless" in sys.argv:
        print("[PocketPad] Starting in headless CLI mode...")
        asyncio.run(run_server())
        return

    # 1. Start Server in Background Thread
    server_thread = threading.Thread(target=start_background_server, daemon=True)
    server_thread.start()

    # Wait for HTTP server to become ready by polling the port
    import socket
    start_time = time.time()
    while time.time() - start_time < 5.0:
        try:
            with socket.create_connection(("127.0.0.1", HTTP_PORT), timeout=0.1):
                break
        except OSError:
            time.sleep(0.1)

    # 2. Start Tray Icon in Background Thread
    tray_thread = threading.Thread(target=setup_tray, daemon=True)
    tray_thread.start()

    # 3. Launch Native Windows 11 Desktop Control Center
    api = DesktopAPI()
    desktop_url = f"http://127.0.0.1:{HTTP_PORT}/desktop.html?token={EXPECTED_TOKEN}"

    main_window = webview.create_window(
        title="PocketPad - Forza Racing Control Center",
        url=desktop_url,
        js_api=api,
        width=1060,
        height=660,
        resizable=True,
        min_size=(960, 600),
        background_color="#070a14",
    )
    
    main_window.events.closing += on_closing

    webview.start(gui="edgechromium", debug=False)

if __name__ == "__main__":
    main()
