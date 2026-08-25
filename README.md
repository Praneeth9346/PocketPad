# 🏎️ PocketPad - High-Performance Racing Controller & Live Telemetry Dashboard

PocketPad turns your smartphone (Android / iOS) into a low-latency **Motion Steering Racing Wheel**, **Virtual Xbox 360 Controller**, and **Live GT3 / Formula 1 Telemetry Cockpit** tailored for *Forza Horizon 5*, *Forza Horizon 4*, and *Forza Motorsport*.

---

## ⚡ Architecture & Features

1. **🏎️ Real-Time In-Game Telemetry Dashboard**:
   - **GT3 / F1 Dynamic LED Shift Light Bar** (10-stage LEDs with strobe rev-limiter redline flash).
   - **Digital Gear Display** inside the wheel center badge (`1, 2, 3... 7, R, N`) with shift pulse animation.
   - **Digital Speedometer** (`MPH` / `KM/H` unit toggle).
   - **Live RPM & Turbo Boost Gauge** (`PSI`).
   - **Tire Slip & G-Force Drift Meter** (glows red when breaking traction in a drift).
   - **1-Tap Dash Demo Mode** to preview the dashboard even when the game isn't running.

2. **⚡ Low-Latency Native Binary Protocol**:
   - Windows Kernel 1ms Multimedia Timer (`timeBeginPeriod(1)`) + `HIGH_PRIORITY_CLASS`.
   - Pre-allocated zero-allocation binary frames (`1–13 bytes`).
   - Dual-transport support: **USB Wired Mode** and **Wireless 5 GHz Wi-Fi** (WMM Voice QoS AC_VO).

3. **📱 Precision Motion Steering**:
   - Direct physical gravity vector tracking with **Zero Gimbal Lock**.
   - Adjustable Steering Max Angle: **15° to 90°** with instant HUD cycle button (`📐 45° Lock`).
   - S-Curve Linearity adjustment for micro-precision on high-speed straights.
   - Anti-deadzone bypass with hysteresis to eliminate tremor chatter at rest.
   - Instant Zero Calibration (`🎯 Center Wheel`) & Micro-Trim (`±0.5°`).

4. **🕹️ Complete Cockpit Controls**:
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
- **TLS 1.2+ Security**: Self-signed multi-interface SSL certificates covering localhost and local network subnets with strict client certificate validation.
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

## 🏁 How to Enable Live Telemetry in Forza Horizon

1. Open **Forza Horizon 5 / 4** or **Forza Motorsport**.
2. Go to **Settings ➔ HUD and Gameplay**.
3. Scroll to the bottom:
   - **Data Out**: `ON`
   - **Data Out IP Address**: `127.0.0.1`
   - **Data Out IP Port**: `5300`
4. Step on the gas — your cockpit will illuminate with shift lights, gear numbers, and speed telemetry.

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
