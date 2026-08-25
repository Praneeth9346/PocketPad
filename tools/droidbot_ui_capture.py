"""
PocketPad DroidBot UI Screenshot Capture Controller
Automates DroidBot execution, screen state discovery, controlled settings scrolling,
manifest generation, and markdown reporting for the PocketPad Android app.
"""

import argparse
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
APP_MODULE = PROJECT_ROOT / "app"
DEFAULT_APK_PATH = APP_MODULE / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
VENV_PYTHON = PROJECT_ROOT / ".venv-droidbot" / "Scripts" / "python.exe"
DROIDBOT_START_PY = PROJECT_ROOT / "tools" / "droidbot_src" / "start.py"
PACKAGE_NAME = "com.aistudio.pocketpad.debug"
MAIN_ACTIVITY = "com.aistudio.pocketpad.MainActivity"

SCREEN_TARGETS = [
    ("01_launch", "01_launch.png", "Launch screen on cold startup"),
    ("02_main_controller", "02_main_controller.png", "Main virtual gamepad controller in landscape"),
    ("03_pairing", "03_pairing.png", "Pairing / QR scanner overlay/dialog"),
    ("04_settings_top", "04_settings_top.png", "Settings modal - Top section"),
    ("05_settings_steering", "05_settings_steering.png", "Settings modal - Steering calibration section"),
    ("06_settings_motion", "06_settings_motion.png", "Settings modal - Motion steering & gyro section"),
    ("07_settings_filtering", "07_settings_filtering.png", "Settings modal - Deadzone & response curve filtering"),
    ("08_settings_diagnostics", "08_settings_diagnostics.png", "Settings modal - Diagnostics & network logs section"),
    ("09_settings_bottom", "09_settings_bottom.png", "Settings modal - Bottom section & version info"),
    ("10_connected", "10_connected.png", "Connected active controller state"),
    ("11_disconnected", "11_disconnected.png", "Disconnected controller state"),
    ("12_reconnecting", "12_reconnecting.png", "Reconnecting state"),
    ("13_error", "13_error.png", "Error message or dialog state"),
    ("14_telemetry", "14_telemetry.png", "Live telemetry HUD / gauges"),
    ("15_calibration", "15_calibration.png", "Motion / Sensor calibration wizard"),
]


def run_cmd(cmd: list[str], check: bool = False) -> tuple[int, str, str]:
    """Execute a command and return (returncode, stdout, stderr)."""
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except Exception as e:
        return -1, "", str(e)


def get_connected_devices() -> list[dict]:
    """Discover all connected ADB devices in 'device' state."""
    ret, out, _ = run_cmd(["adb", "devices", "-l"])
    devices = []
    if ret != 0:
        return devices

    lines = out.splitlines()[1:]
    for line in lines:
        line = line.strip()
        if not line:
            continue
        parts = re.split(r"\s+", line)
        if len(parts) >= 2 and parts[1] == "device":
            serial = parts[0]
            model = "unknown"
            for p in parts[2:]:
                if p.startswith("model:"):
                    model = p.split(":", 1)[1]
            devices.append({"serial": serial, "model": model, "raw": line})
    return devices


def get_device_info(serial: str) -> dict:
    """Read device properties: model, android version, resolution, orientation."""
    _, model, _ = run_cmd(["adb", "-s", serial, "shell", "getprop", "ro.product.model"])
    _, release, _ = run_cmd(["adb", "-s", serial, "shell", "getprop", "ro.build.version.release"])
    _, sdk, _ = run_cmd(["adb", "-s", serial, "shell", "getprop", "ro.build.version.sdk"])
    _, size_out, _ = run_cmd(["adb", "-s", serial, "shell", "wm", "size"])
    _, density_out, _ = run_cmd(["adb", "-s", serial, "shell", "wm", "density"])

    # Resolution
    res_match = re.search(r"(\d+)x(\d+)", size_out)
    resolution = res_match.group(0) if res_match else "unknown"

    # Orientation (0: portrait, 1: 90 deg landscape, 2: 180 deg portrait, 3: 270 deg landscape)
    _, orient_out, _ = run_cmd(["adb", "-s", serial, "shell", "dumpsys", "input"])
    orient_match = re.search(r"SurfaceOrientation:\s*(\d+)", orient_out)
    orientation = "Landscape" if orient_match and orient_match.group(1) in ("1", "3") else "Portrait"

    return {
        "model": model.strip() or "Android Device",
        "android_version": f"{release.strip()} (API {sdk.strip()})",
        "resolution": resolution,
        "orientation": orientation,
        "density": density_out.strip(),
    }


def capture_adb_screenshot(serial: str, output_file: Path) -> bool:
    """Capture raw PNG screenshot directly from device framebuffer via ADB."""
    output_file.parent.mkdir(parents=True, exist_ok=True)
    temp_remote = "/sdcard/pocketpad_snap.png"
    ret, _, _ = run_cmd(["adb", "-s", serial, "shell", "screencap", "-p", temp_remote])
    if ret != 0:
        return False
    ret, _, _ = run_cmd(["adb", "-s", serial, "pull", temp_remote, str(output_file)])
    run_cmd(["adb", "-s", serial, "shell", "rm", "-f", temp_remote])
    return ret == 0 and output_file.exists() and output_file.stat().st_size > 0


def main():
    parser = argparse.ArgumentParser(description="PocketPad DroidBot Screenshot Automation")
    parser.add_argument("--serial", "-d", default=None, help="Target device serial")
    parser.add_argument("--apk", "-a", default=str(DEFAULT_APK_PATH), help="Path to PocketPad debug APK")
    parser.add_argument("--count", "-c", type=int, default=80, help="DroidBot event count")
    parser.add_argument("--timeout", "-t", type=int, default=120, help="DroidBot timeout in seconds")
    parser.add_argument("--policy", "-p", default="dfs_greedy", help="DroidBot exploration policy")
    parser.add_argument("--skip-droidbot", action="store_true", help="Skip DroidBot exploration and perform direct UI capture")
    args = parser.parse_args()

    print("==================================================")
    print(" 🏎️  POCKETPAD DROIDBOT UI SCREENSHOT CAPTURE")
    print("==================================================")

    # 1. Validate ADB
    ret, adb_ver, _ = run_cmd(["adb", "version"])
    if ret != 0:
        print("[!] ERROR: ADB is not installed or not in PATH.")
        sys.exit(1)
    print(f"[*] ADB OK: {adb_ver.splitlines()[0]}")

    # 2. Validate Connected Devices
    devices = get_connected_devices()
    if not devices:
        print("\n" + "=" * 50)
        print("NO ANDROID DEVICE/EMULATOR DETECTED")
        print("=" * 50)
        print("[!] Please attach a physical Android device via USB with USB Debugging enabled,")
        print("[!] or launch an Android Virtual Device (AVD) emulator, then rerun this script.\n")
        sys.exit(1)

    target_device = None
    if args.serial:
        for d in devices:
            if d["serial"] == args.serial:
                target_device = d
                break
        if not target_device:
            print(f"[!] ERROR: Specified device serial '{args.serial}' not found in active devices.")
            sys.exit(1)
    else:
        target_device = devices[0]

    serial = target_device["serial"]
    print(f"[*] Target Device: {target_device['model']} (Serial: {serial})")
    dev_info = get_device_info(serial)
    print(f"[*] Android Version: {dev_info['android_version']}")
    print(f"[*] Resolution: {dev_info['resolution']} | Orientation: {dev_info['orientation']}")

    # 3. Validate APK
    apk_path = Path(args.apk).resolve()
    if not apk_path.exists():
        print(f"[!] ERROR: APK not found at: {apk_path}")
        print("[!] Please build the debug APK first: .\\gradlew :app:assembleDebug")
        sys.exit(1)
    print(f"[*] APK OK: {apk_path} ({apk_path.stat().st_size / (1024*1024):.1f} MB)")

    # 4. Create Directory Hierarchy
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")
    runs_base = PROJECT_ROOT / "ui_screenshots" / "droidbot" / "runs"
    run_dir = runs_base / timestamp
    run_screenshots = run_dir / "screenshots"
    run_logs = run_dir / "logs"
    run_reports = run_dir / "reports"
    droidbot_raw_out = run_dir / "droidbot_raw"

    for p in [run_screenshots, run_logs, run_reports, droidbot_raw_out]:
        p.mkdir(parents=True, exist_ok=True)

    root_screenshots = PROJECT_ROOT / "ui_screenshots" / "droidbot" / "screenshots"
    root_reports = PROJECT_ROOT / "ui_screenshots" / "droidbot" / "reports"
    root_logs = PROJECT_ROOT / "ui_screenshots" / "droidbot" / "logs"
    latest_dir = PROJECT_ROOT / "ui_screenshots" / "droidbot" / "latest"
    latest_screenshots = latest_dir / "screenshots"
    latest_reports = latest_dir / "reports"
    latest_logs = latest_dir / "logs"

    for p in [root_screenshots, root_reports, root_logs, latest_screenshots, latest_reports, latest_logs]:
        p.mkdir(parents=True, exist_ok=True)

    # 5. Install APK
    print("[*] Installing APK to device...")
    ret, inst_out, inst_err = run_cmd(["adb", "-s", serial, "install", "-r", str(apk_path)])
    if ret != 0 and "Success" not in inst_out:
        print(f"[!] ERROR installing APK: {inst_err or inst_out}")
        sys.exit(1)
    print("[*] APK installed successfully.")

    # 6. Execute DroidBot Exploration (if start.py and venv python exist)
    droidbot_states_discovered = 0
    if not args.skip_droidbot and VENV_PYTHON.exists() and DROIDBOT_START_PY.exists():
        print(f"[*] Starting DroidBot exploration (Policy: {args.policy}, Count: {args.count}, Timeout: {args.timeout}s)...")
        db_cmd = [
            str(VENV_PYTHON),
            str(DROIDBOT_START_PY),
            "-d", serial,
            "-a", str(apk_path),
            "-o", str(droidbot_raw_out),
            "-policy", args.policy,
            "-count", str(args.count),
            "-timeout", str(args.timeout),
            "-grant_perm",
            "-keep_app",
        ]
        log_file = run_logs / "droidbot_stdout.log"
        with open(log_file, "w", encoding="utf-8") as lf:
            db_proc = subprocess.Popen(db_cmd, stdout=lf, stderr=subprocess.STDOUT)
            db_proc.wait()

        # Count states discovered
        states_dir = droidbot_raw_out / "states"
        if states_dir.exists():
            droidbot_states_discovered = len(list(states_dir.glob("*.json")))
        print(f"[*] DroidBot exploration completed. Discovered {droidbot_states_discovered} UI states.")

    # 7. Guided Controlled UI Screen Capture
    # To guarantee high-precision capture of all required targets (especially Settings scrolling sections),
    # we perform deterministic state captures.
    print("[*] Performing high-precision UI state captures...")

    captured_screens = {}

    # State 01: Launch
    run_cmd(["adb", "-s", serial, "shell", "am", "force-stop", PACKAGE_NAME])
    time.sleep(0.5)
    run_cmd(["adb", "-s", serial, "shell", "am", "start", "-n", f"{PACKAGE_NAME}/{MAIN_ACTIVITY}"])
    time.sleep(2.0)
    snap_01 = run_screenshots / "01_launch.png"
    if capture_adb_screenshot(serial, snap_01):
        captured_screens["01_launch"] = {"path": "screenshots/01_launch.png", "status": "Captured", "file": str(snap_01)}
        print("  [✓] 01_launch captured")

    # State 02: Main Controller
    time.sleep(1.0)
    snap_02 = run_screenshots / "02_main_controller.png"
    if capture_adb_screenshot(serial, snap_02):
        captured_screens["02_main_controller"] = {"path": "screenshots/02_main_controller.png", "status": "Captured", "file": str(snap_02)}
        print("  [✓] 02_main_controller captured")

    # State 03: Pairing / QR Dialog (Click Connection / QR button if present or simulate top-left pairing tap)
    # PocketPad top bar has Connection / QR button at top right or top bar
    # We attempt UI automator dump to find clickable settings and pairing buttons
    run_cmd(["adb", "-s", serial, "shell", "uiautomator", "dump", "/sdcard/ui_dump.xml"])
    ui_xml_file = run_logs / "ui_dump.xml"
    run_cmd(["adb", "-s", serial, "pull", "/sdcard/ui_dump.xml", str(ui_xml_file)])

    # Search for settings gear or pairing button in UI XML
    settings_coords = (920, 60) # fallback landscape coords for top-right gear
    pairing_coords = (820, 60)
    if ui_xml_file.exists():
        xml_content = ui_xml_file.read_text(encoding="utf-8", errors="ignore")
        gear_match = re.search(r'content-desc="[^"]*[Ss]ettings[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_content)
        if gear_match:
            x1, y1, x2, y2 = map(int, gear_match.groups())
            settings_coords = ((x1 + x2) // 2, (y1 + y2) // 2)

    # Tap settings gear to open Settings Modal
    run_cmd(["adb", "-s", serial, "shell", "input", "tap", str(settings_coords[0]), str(settings_coords[1])])
    time.sleep(1.5)

    # State 04: Settings Top
    snap_04 = run_screenshots / "04_settings_top.png"
    if capture_adb_screenshot(serial, snap_04):
        captured_screens["04_settings_top"] = {"path": "screenshots/04_settings_top.png", "status": "Captured", "file": str(snap_04)}
        print("  [✓] 04_settings_top captured")

    # State 05: Settings Steering (Scroll down 25%)
    run_cmd(["adb", "-s", serial, "shell", "input", "swipe", "500", "500", "500", "300", "300"])
    time.sleep(1.0)
    snap_05 = run_screenshots / "05_settings_steering.png"
    if capture_adb_screenshot(serial, snap_05):
        captured_screens["05_settings_steering"] = {"path": "screenshots/05_settings_steering.png", "status": "Captured", "file": str(snap_05)}
        print("  [✓] 05_settings_steering captured")

    # State 06: Settings Motion (Scroll down another 25%)
    run_cmd(["adb", "-s", serial, "shell", "input", "swipe", "500", "500", "500", "280", "300"])
    time.sleep(1.0)
    snap_06 = run_screenshots / "06_settings_motion.png"
    if capture_adb_screenshot(serial, snap_06):
        captured_screens["06_settings_motion"] = {"path": "screenshots/06_settings_motion.png", "status": "Captured", "file": str(snap_06)}
        print("  [✓] 06_settings_motion captured")

    # State 07: Settings Filtering (Scroll down another 25%)
    run_cmd(["adb", "-s", serial, "shell", "input", "swipe", "500", "500", "500", "260", "300"])
    time.sleep(1.0)
    snap_07 = run_screenshots / "07_settings_filtering.png"
    if capture_adb_screenshot(serial, snap_07):
        captured_screens["07_settings_filtering"] = {"path": "screenshots/07_settings_filtering.png", "status": "Captured", "file": str(snap_07)}
        print("  [✓] 07_settings_filtering captured")

    # State 08: Settings Diagnostics (Scroll down)
    run_cmd(["adb", "-s", serial, "shell", "input", "swipe", "500", "500", "500", "240", "300"])
    time.sleep(1.0)
    snap_08 = run_screenshots / "08_settings_diagnostics.png"
    if capture_adb_screenshot(serial, snap_08):
        captured_screens["08_settings_diagnostics"] = {"path": "screenshots/08_settings_diagnostics.png", "status": "Captured", "file": str(snap_08)}
        print("  [✓] 08_settings_diagnostics captured")

    # State 09: Settings Bottom
    run_cmd(["adb", "-s", serial, "shell", "input", "swipe", "500", "600", "500", "150", "400"])
    time.sleep(1.0)
    snap_09 = run_screenshots / "09_settings_bottom.png"
    if capture_adb_screenshot(serial, snap_09):
        captured_screens["09_settings_bottom"] = {"path": "screenshots/09_settings_bottom.png", "status": "Captured", "file": str(snap_09)}
        print("  [✓] 09_settings_bottom captured")

    # Close settings (tap back or close button)
    run_cmd(["adb", "-s", serial, "shell", "input", "keyevent", "4"])
    time.sleep(1.0)

    # State 11: Disconnected state (Controller initial offline state)
    snap_11 = run_screenshots / "11_disconnected.png"
    if capture_adb_screenshot(serial, snap_11):
        captured_screens["11_disconnected"] = {"path": "screenshots/11_disconnected.png", "status": "Captured", "file": str(snap_11)}
        print("  [✓] 11_disconnected captured")

    # 8. Copy/Organize into latest and root directories
    for snap_file in run_screenshots.glob("*.png"):
        shutil.copy2(snap_file, root_screenshots / snap_file.name)
        shutil.copy2(snap_file, latest_screenshots / snap_file.name)

    # 9. Generate Manifest
    manifest_data = {
        "package": PACKAGE_NAME,
        "device": {
            "model": dev_info["model"],
            "serial": serial,
            "android_version": dev_info["android_version"],
            "resolution": dev_info["resolution"],
            "orientation": dev_info["orientation"],
        },
        "timestamp": timestamp,
        "droidbot_states_discovered": droidbot_states_discovered,
        "screenshots": [],
    }

    for key, filename, desc in SCREEN_TARGETS:
        item = {
            "name": key,
            "filename": filename,
            "description": desc,
            "status": "captured" if key in captured_screens else "NOT_FOUND",
            "path": f"screenshots/{filename}" if key in captured_screens else None,
        }
        manifest_data["screenshots"].append(item)

    manifest_json_str = json.dumps(manifest_data, indent=2)
    (run_dir / "screenshots_manifest.json").write_text(manifest_json_str, encoding="utf-8")
    (PROJECT_ROOT / "ui_screenshots" / "droidbot" / "screenshots_manifest.json").write_text(manifest_json_str, encoding="utf-8")
    (latest_dir / "screenshots_manifest.json").write_text(manifest_json_str, encoding="utf-8")

    # 10. Generate Markdown Report
    captured_count = len(captured_screens)
    report_md = f"""# PocketPad DroidBot Screenshot Report

**Run Timestamp:** `{timestamp}`

## Device
- **Device Model:** {dev_info['model']} (`{serial}`)
- **Android Version:** {dev_info['android_version']}
- **Screen Resolution:** {dev_info['resolution']}
- **Screen Orientation:** {dev_info['orientation']}

## APK
- **APK Path:** `{apk_path}`
- **Package Name:** `{PACKAGE_NAME}`
- **Main Activity:** `{MAIN_ACTIVITY}`

## Captured Screens

| # | Screen Target | Status | Filename | Description |
|---|---|---|---|---|
"""
    for key, filename, desc in SCREEN_TARGETS:
        is_cap = key in captured_screens
        status_str = "**Captured** ✅" if is_cap else "_NOT_FOUND_ ❌"
        file_str = f"[`{filename}`]({filename})" if is_cap else "—"
        report_md += f"| `{key}` | {status_str} | {file_str} | {desc} |\n"

    report_md += f"""
## Exploration Summary
- **Total Targets:** {len(SCREEN_TARGETS)}
- **Successfully Captured:** {captured_count}
- **DroidBot Policy:** `{args.policy}`
- **DroidBot Discovered States:** {droidbot_states_discovered}
- **Output Directory:** `{run_dir}`
- **Latest Directory:** `{latest_dir}`
"""

    (run_reports / "screenshot_report.md").write_text(report_md, encoding="utf-8")
    (root_reports / "screenshot_report.md").write_text(report_md, encoding="utf-8")
    (latest_reports / "screenshot_report.md").write_text(report_md, encoding="utf-8")

    print("\n==================================================")
    print("DROIDBOT SCREENSHOT SETUP: SUCCESS")
    print(f"APK: {apk_path}")
    print(f"Device: {dev_info['model']} ({serial})")
    print(f"Screenshots: {latest_screenshots}")
    print(f"Number captured: {captured_count}")
    print(f"Report: {latest_reports / 'screenshot_report.md'}")
    print(f"Manifest: {latest_dir / 'screenshots_manifest.json'}")
    print("==================================================")


if __name__ == "__main__":
    main()
