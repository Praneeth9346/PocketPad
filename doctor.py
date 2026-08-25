"""
PocketPad Diagnostics & Environment Doctor
Verifies Python version, dependencies, ViGEmBus driver, ports, tokens, and ADB status.
"""
import os
import socket
import sys
from pathlib import Path


def check(name: str, passed: bool, detail: str = ""):
    status = "[OK]" if passed else "[FAIL]"
    msg = f"{status} {name}"
    if detail:
        msg += f" ({detail})"
    print(msg)
    return passed


def main():
    print("=" * 60)
    print(" [*] POCKETPAD SYSTEM DIAGNOSTICS & DOCTOR")
    print("=" * 60)

    all_passed = True

    # 1. Python Version
    py_ver = sys.version.split()[0]
    py_ok = sys.version_info >= (3, 10)
    all_passed &= check("Python Version", py_ok, py_ver)

    # 2. websockets
    try:
        import websockets
        all_passed &= check("websockets", True, f"v{websockets.__version__}")
    except ImportError as e:
        all_passed &= check("websockets", False, str(e))

    # 3. cryptography
    try:
        import cryptography
        all_passed &= check("cryptography", True, f"v{cryptography.__version__}")
    except ImportError as e:
        all_passed &= check("cryptography", False, str(e))

    # 4. Pillow & QRCode
    try:
        import PIL
        import qrcode
        all_passed &= check("Pillow & QRCode", True, f"PIL {PIL.__version__}")
    except ImportError as e:
        all_passed &= check("Pillow & QRCode", False, str(e))

    # 5. vgamepad & ViGEm Driver
    try:
        import vgamepad as vg
        try:
            pad = vg.VX360Gamepad()
            pad.reset()
            del pad
            all_passed &= check("ViGEmBus Virtual Xbox 360 Controller", True, "Driver Active")
        except Exception as e:
            all_passed &= check("ViGEmBus Virtual Xbox 360 Controller", False, f"Driver not found or error: {e}")
    except ImportError as e:
        all_passed &= check("vgamepad module", False, str(e))

    # 6. Ports Availability (8000, 8443, 8765, 8766)
    ports = [8000, 8443, 8765, 8766]
    for port in ports:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(0.5)
            in_use = (s.connect_ex(('127.0.0.1', port)) == 0)
            status_desc = "In use / Server running" if in_use else "Available"
            check(f"Port {port}", True, status_desc)

    # 7. Certificate and Key
    base_dir = Path(__file__).parent
    cert_exists = (base_dir / "cert.pem").exists()
    key_exists = (base_dir / "key.pem").exists()
    check("TLS Certificate (cert.pem)", cert_exists, "Present" if cert_exists else "Will generate on start")
    check("TLS Private Key (key.pem)", key_exists, "Present" if key_exists else "Will generate on start")

    # 8. Token File
    token_exists = (base_dir / ".pocketpad_token").exists()
    check("Auth Token Storage", token_exists, "Present" if token_exists else "Will generate on start")

    # 9. ADB Tools
    from usb_setup import get_adb_path
    adb_path = get_adb_path()
    check("Android Debug Bridge (ADB)", bool(adb_path), adb_path or "Not found in PATH or adb_tools")

    print("=" * 60)
    if all_passed:
        print("[+] System is fully ready for PocketPad!")
    else:
        print("[!] Some checks failed. Review the errors above.")
    print("=" * 60)


if __name__ == "__main__":
    main()
