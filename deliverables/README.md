# PocketPad v1.1.0 — Final Deliverables

## Contents

| File | Platform | Size | Description |
|------|----------|------|-------------|
| `PocketPad.exe` | Windows | ~44 MB | Standalone Windows desktop server (no Python required) |
| `PocketPad-release.apk` | Android | ~5.5 MB | Production Android client (minified, ProGuard) |
| `PocketPad-debug.apk` | Android | ~13 MB | Debug Android client (full symbols, logging) |
| `web/` | All | — | Web UI assets (needed alongside PocketPad.exe) |

## Quick Start

### Windows Server
1. Place `PocketPad.exe` and the `web/` folder in the same directory
2. Double-click `PocketPad.exe`
3. The server will start and show a QR code for phone connection

### Android Client
1. Install `PocketPad-release.apk` on your Android phone
2. Connect to the same WiFi network as your PC, **or** use USB tethering
3. Scan the QR code or enter the server IP

## Build Info
- **Version**: 1.1.0
- **Build Date**: August 24, 2026
- **Python**: 3.12.9
- **Android SDK**: 35, minSdk 24
- **Architecture**: Modular (`pocketpad/` package)
