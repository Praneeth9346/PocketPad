# 🏎️ PocketPad - High-Performance Virtual Racing Controller & Gamepad

PocketPad turns your smartphone (Android / iOS) into a ultra-low-latency **Motion Steering Racing Wheel** and **Virtual Xbox 360 Controller** for PC games (*Forza Horizon 5*, *Forza Horizon 4*, *Assetto Corsa*, *F1*, and more).

---

## ⚡ Architecture & Features

1. **⚡ Ultra Low-Latency Native Binary Protocol**:
   - Windows Kernel 1ms Multimedia Timer (`timeBeginPeriod(1)`) + `HIGH_PRIORITY_CLASS`.
   - Pre-allocated zero-allocation binary frames (`1–13 bytes`).
   - Dual-transport support: **USB Wired Mode** (ADB reverse loopback) and **Wireless 5 GHz Wi-Fi** (WMM Voice QoS AC_VO).

2. **📱 Precision Motion Steering**:
   - Direct physical gravity vector tracking with **Zero Gimbal Lock**.
   - Adjustable Steering Max Angle: **15° to 90°** with instant HUD cycle button (`📐 45° Lock`).
   - S-Curve Linearity adjustment for micro-precision on high-speed straights.
   - Anti-deadzone bypass with hysteresis to eliminate tremor chatter at rest.
   - Instant Zero Calibration (`🎯 Center Wheel`) & Micro-Trim (`±0.5°`).

3. **🕹️ Complete Cockpit Controls**:
   - Analog slide pedals for Gas (RT) and Brake (LT) feathering.
   - Paddle Shifters (Shift UP `B`, Shift DOWN `X`), Drift Handbrake (`A`), Clutch (`LB`), Rewind (`Y`), Camera (`View`), Look Behind (`RS`), Horn (`LS`).
   - Full 2D Left and Right Thumbsticks in Standard Gamepad Mode.

---

## 📊 Performance & Latency

PocketPad is engineered for high responsiveness and minimal latency:

| Metric | Typical USB Wired | Typical 5 GHz Wi-Fi |
|:---|:---:|:---:|
| **Network RTT** | < 1 ms | 2 – 5 ms |
| **Input Stream Rate** | ~120 – 240 Hz | ~120 – 200 Hz |
| **Transport** | TCP Loopback via ADB Reverse | TLS / WSS Socket |

> [!NOTE]
> End-to-end latency depends on phone sensor sampling rate, operating system thread scheduling, transport medium, ViGEm driver dispatch, and game input polling frequency.

---

## 🔒 Security Model

- **Token Authentication**: First WebSocket frame requires a cryptographically secure 32-byte auth token (`hello` ➔ `hello_ack`).
- **TLS 1.2+ Security**: PocketPad Root CA → CA-signed local server certificate covering localhost and local network subnets → Android trusts bundled PocketPad CA.
- **Protected APIs**: Administrative endpoints (`/api/joy_cpl`, `/api/restart_adb`, `/api/rotate_token`) strictly require Bearer authorization.
- **Crash-Safe Reset**: Process exit hooks (`atexit`) ensure virtual controllers are immediately neutralized if the server process terminates.

---

## 🔌 How to Connect

1. **USB Wired Mode** (Recommended for lowest latency):
   Ensure ADB is running and port forwarding is active (the server handles this automatically).
   In the PocketPad app, connect to: `127.0.0.1:8443`

2. **Wireless Wi-Fi Mode**:
   Ensure your PC and phone are on the same network (5 GHz recommended).
   In the PocketPad app, connect to: `<PC-LAN-IP>:8443` (e.g., `192.168.1.50:8443`)

---

## 🩺 System Diagnostics

Run the built-in system doctor:
```powershell
python doctor.py
```

Run test suites:
```powershell
# Python tests
pytest tests/ -v

# Android unit tests
.\gradlew :app:testDebugUnitTest --no-daemon
```
