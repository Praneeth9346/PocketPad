import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path


def get_base_dir() -> Path:
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent

BASE_DIR = get_base_dir()
ADB_DIR = BASE_DIR / "adb_tools"
ADB_EXE = ADB_DIR / "platform-tools" / "adb.exe"

def get_adb_path():
    """Find adb in PATH, in local adb_tools, or in standard Android SDK dirs."""
    if ADB_EXE.exists():
        return str(ADB_EXE)

    system_adb = shutil.which("adb")
    if system_adb:
        return system_adb

    # Check Android SDK in AppData
    appdata = os.environ.get("LOCALAPPDATA", "")
    if appdata:
        sdk_adb = Path(appdata) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
        if sdk_adb.exists():
            return str(sdk_adb)

    return None

def download_adb():
    """Download lightweight official Google Platform Tools if missing."""
    adb_path = get_adb_path()
    if adb_path:
        return adb_path

    print("[USB] Downloading official Google ADB tools for 0.2ms USB wire speed...")
    ADB_DIR.mkdir(exist_ok=True)
    zip_dest = ADB_DIR / "platform-tools.zip"

    url = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    try:
        urllib.request.urlretrieve(url, zip_dest)
        with zipfile.ZipFile(zip_dest, "r") as zf:
            zf.extractall(ADB_DIR)
        zip_dest.unlink(missing_ok=True)
        print("[USB] ADB tools installed successfully!")
        return str(ADB_EXE)
    except Exception as e:
        print(f"[USB] Could not auto-download ADB: {e}")
        return None

def setup_usb_reverse_forwarding():
    """Configure adb reverse port forwarding for 0.2ms USB wire speed."""
    adb = get_adb_path()
    if not adb:
        adb = download_adb()

    if not adb or not os.path.exists(adb):
        return False, "ADB not found"

    try:
        # Check connected devices
        out = subprocess.check_output([adb, "devices"], text=True, stderr=subprocess.STDOUT)
        lines = [line.strip() for line in out.strip().splitlines() if line.strip() and not line.startswith("List")]
        devices = [line.split()[0] for line in lines if "device" in line]

        if not devices:
            return False, "No USB device detected in USB Debugging mode"

        # Reverse forward ports 8443, 8000, 8766, 8765
        ports = [8443, 8000, 8766, 8765]
        for p in ports:
            subprocess.run([adb, "reverse", f"tcp:{p}", f"tcp:{p}"], check=True, capture_output=True)

        return True, f"Forwarded {len(ports)} ports to USB device: {devices[0]}"
    except Exception as e:
        return False, str(e)

if __name__ == "__main__":
    success, msg = setup_usb_reverse_forwarding()
    print(f"Status: {success} -> {msg}")
