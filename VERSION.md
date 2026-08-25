# 🏎️ PocketPad v1.1.0 - Release Manifest

> **Release Version:** 1.1.0 (Production Hardened)
> **Release Date:** August 25, 2026
> **Binary Artifact:** deliverables/PocketPad.exe (43.9 MB) & deliverables/PocketPad-release.apk (5.8 MB)
> **Status:** Release Candidate

---

## 🌟 Core Features Included in v1.1.0

### 1. Ultra-Low-Latency Motion Engine
* **Direct 3D Lateral Gravity Arc Calculation (atan2)**: Smooth, continuous, and singularity-free across full 360° space.
* **Spike Rejection**: Frames with >40° instant jump vs. last sample are discarded to reject sensor glitch artifacts.
* **Hysteresis-Gated Anti-Deadzone (Schmitt-Trigger Latch)**: Dual-threshold engage/release latch eliminates resting tremor chatter while maintaining instantaneous in-game deadband bypass.
* **2.89x Direct Steering Ratio**: 90° physical wrist tilt delivers 260° of in-game steering wheel lock.
* **Deterministic Single-Tap Motion Toggle**: Clean state transitions between Motion ON and Motion OFF.

### 2. Sub-Millisecond Binary Protocol & Fast Path
* **Binary Framing**: 3–5 byte pre-allocated typed arrays (ArrayBuffer).
* **Kernel Scheduling Priority**: Process pinned to `HIGH_PRIORITY_CLASS` and worker thread to `THREAD_PRIORITY_TIME_CRITICAL` (+15).
* **Multimedia Timer**: Windows OS timer resolution locked to 1.0 ms via `winmm.timeBeginPeriod(1)`.
* **Timing-Safe Authentication**: Token validation protected against side-channel timing attacks via `secrets.compare_digest`.
* **Verified Latency Benchmarks**:
  * **USB Wired (Method B)**: 0.20 ms RTT
  * **5GHz Wi-Fi (Method A)**: ~2–4 ms RTT
  * **Throughput**: 21,000+ packets/sec

### 3. Accelerometer-Only Lateral Gravity Steering
* **Pure atan2 accelerometer tilt tracking**: No gyroscope fusion currently active in the shipped client — rotationRate fusion is planned, not yet wired in.

### 4. Standalone Windows Executable & Android Client
* **Single-File Native GUI**: Built with PyInstaller using `runw.exe` (`console=False`) for a clean, borderless dark-glass control center with zero command prompt popups.
* **Android Client**: Native Jetpack Compose UI with adaptive OneEuro filtering and reverse-tethered low-latency USB communication.
