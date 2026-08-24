# 🏎️ PocketPad - Live Forza Telemetry Dashboard & Motion Racing Gamepad

PocketPad turns your smartphone (iOS / Android) into an ultra-low-latency **Motion Steering Racing Wheel**, **Xbox 360 controller**, and **Live GT3 / Formula 1 Telemetry Dashboard** tailored for *Forza Horizon 5*, *Forza Horizon 4*, and *Forza Motorsport*.

---

## ⚡ Key Features

1. **🏎️ Real-Time In-Game Telemetry Dashboard**:
   - **GT3 / F1 Dynamic LED Shift Light Bar** (10-stage LEDs with strobe rev-limiter redline flash!).
   - **Digital Gear Display** inside the wheel center badge (`1, 2, 3... 7, R, N`) with shift pulse animation.
   - **Digital Speedometer** (`MPH` / `KM/H` unit toggle).
   - **Live RPM & Turbo Boost Gauge** (`PSI`).
   - **Tire Slip & G-Force Drift Meter** (glows red when breaking traction in a drift).
   - **1-Tap Dash Demo Mode** to preview the dashboard even when the game isn't open.

2. **⚡ Sub-Millisecond Native C-Speed Binary Protocol**:
   - Windows Kernel 1ms Multimedia Timer (`timeBeginPeriod(1)`) + `HIGH_PRIORITY_CLASS`.
   - Pre-allocated zero-allocation binary frames.
   - **~0.23 ms Wire Speed over USB** / **~2–4 ms over 5 GHz Wi-Fi**.

3. **📱 Glitch-Free Gravity Vector Steering Wheel**:
   - Direct physical gravity vector tracking with **Zero Euler Gimbal Lock**.
   - Adjustable Steering Max Angle: **15° to 90°** with instant 1-tap HUD cycle button (`📐 45° Lock`).
   - S-Curve Linearity adjustment for micro-precision on high-speed straights.
   - Instant Zero Calibration (`🎯 Center Wheel`) & Micro-Trim (`±0.5°`).

4. **🕹️ Complete Racing Cockpit Controls**:
   - GPU-accelerated 240Hz analog slide pedals for Gas (RT) and Brake (LT) feathering.
   - Paddle Shifters (Shift UP `B`, Shift DOWN `X`), Drift Handbrake (`A`), Clutch (`LB`), Rewind (`Y`), Camera (`View`), Look Behind (`RS`), Horn (`LS`).
   - Full 2D Left and Right Thumbsticks in Standard Gamepad Mode.

---

## 🏁 How to Enable Live Telemetry in Forza Horizon (10 Seconds)

1. Open **Forza Horizon 5 / 4** or **Forza Motorsport**.
2. Go to **Settings ➔ HUD and Gameplay**.
3. Scroll down to the bottom:
   - **Data Out**: `ON`
   - **Data Out IP Address**: `127.0.0.1`
   - **Data Out IP Port**: `5300`
4. Jump into any car and step on the gas — your phone cockpit will instantly spring to life with glowing shift lights, live digital gear numbers, and high-speed telemetry!

---

## ⚡ Connection Modes

### Method A: 📶 Wireless 5GHz Wi-Fi Mode (< 3–5 ms)
* Connect your phone to **5 GHz Wi-Fi** and open:
  ```text
  https://10.192.134.151:8443
  ```

### Method B: ⚡ USB Cable Pro-Speed Mode (0.2 ms Instant Wire Speed)
* Plug your phone into your PC with a **USB cable** and open:
  ```text
  https://localhost:8443   (or https://10.18.215.226:8443)
  ```
