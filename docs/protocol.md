# PocketPad Binary & WebSocket Protocol Specification (v1.1.0)

This document describes the exact binary packet layout and WebSocket handshake protocol used for communication between the PocketPad Android/Web client and the PocketPad Python host server.

---

## 1. Endianness & Numerical Representations

- All multi-byte integers are serialized in **Little-Endian** (`<`) byte order.
- Signed 16-bit integers (`int16`, range `-32768` to `+32767`) are used for analog steering axes and stick axes.
- Unsigned 8-bit integers (`uint8`, range `0` to `255`) are used for trigger levels (brake, throttle) and button indices.

---

## 2. Authentication & Handshake Flow

The first frame sent by the client upon establishing a WebSocket connection **MUST** be a text JSON payload containing the client authentication token:

### Client Hello (Text Frame)
```json
{
  "type": "hello",
  "token": "<32_BYTE_SECURITY_TOKEN>"
}
```

### Server Response (Text Frame)
- **Success (`hello_ack`)**:
  ```json
  {
    "type": "hello_ack",
    "version": 1,
    "server": "PocketPad",
    "controller_available": true
  }
  ```
- **Failure**: Server closes connection with WebSocket closure code `4001` (`Invalid token`), `4002` (`Handshake timeout`), or `4003` (`Too many auth failures`).

---

## 3. Binary Input Packets (Client ➔ Server)

| Opcode | Meaning | Wire Length | Byte Layout |
|:---|:---|:---:|:---|
| `0x00` | Keepalive | 1 byte | `[uint8 0x00]` |
| `0x01` | Steering Axis | 3 bytes | `[uint8 0x01, int16 steering_x]` |
| `0x02` | Pedals (LT / RT) | 3 bytes | `[uint8 0x02, uint8 brake_lt, uint8 throttle_rt]` |
| `0x03` | Digital Button | 3 bytes | `[uint8 0x03, uint8 btn_id, uint8 pressed]` |
| `0x04` | Full Controller Snapshot | 13 bytes | `[uint8 0x04, int16 lx, int16 ly, int16 rx, int16 ry, uint8 lt, uint8 rt, uint16 btn_mask]` |
| `0x05` | Left Stick 2D | 5 bytes | `[uint8 0x05, int16 lx, int16 ly]` |
| `0x06` | Right Stick 2D | 5 bytes | `[uint8 0x06, int16 rx, int16 ry]` |
| `0x07` | Mouse Emulation | 6 bytes | `[uint8 0x07, int16 dx, int16 dy, uint8 buttons]` |
| `0x08` | Media Key | 2 bytes | `[uint8 0x08, uint8 vk_key_code]` |
| `0x09` | Ping Echo Probe | 5 bytes | `[uint8 0x09, uint32 timestamp]` |
| `0x11` | Demo Mode Toggle | 1 byte | `[uint8 0x11]` |

---

## 4. Server ➔ Client Packets

| Opcode | Meaning | Wire Length | Byte Layout |
|:---|:---|:---:|:---|
| `0x0A` | Pong Echo Response | 5 bytes | `[uint8 0x0A, uint32 timestamp]` |
| `0x0B` | Force Feedback Rumble | 3 bytes | `[uint8 0x0B, uint8 large_motor, uint8 small_motor]` |
| `0x10` | Game Telemetry Feed | 13 bytes | `[uint8 0x10, int16 rpm, int16 max_rpm, int16 speed_x10, uint8 gear, uint8 shift_pct, uint8 slip_pct, uint8 accel, uint8 brake, uint8 boost]` |

---

## 5. Digital Button Indices

| Index | Button Name | Xbox 360 Equivalent |
|:---:|:---|:---|
| 0 | A | `XUSB_GAMEPAD_A` |
| 1 | B | `XUSB_GAMEPAD_B` |
| 2 | X | `XUSB_GAMEPAD_X` |
| 3 | Y | `XUSB_GAMEPAD_Y` |
| 4 | DPAD_UP | `XUSB_GAMEPAD_DPAD_UP` |
| 5 | DPAD_DOWN | `XUSB_GAMEPAD_DPAD_DOWN` |
| 6 | DPAD_LEFT | `XUSB_GAMEPAD_DPAD_LEFT` |
| 7 | DPAD_RIGHT | `XUSB_GAMEPAD_DPAD_RIGHT` |
| 8 | START | `XUSB_GAMEPAD_START` |
| 9 | BACK | `XUSB_GAMEPAD_BACK` |
| 10 | GUIDE | `XUSB_GAMEPAD_GUIDE` |
| 11 | LB | `XUSB_GAMEPAD_LEFT_SHOULDER` |
| 12 | RB | `XUSB_GAMEPAD_RIGHT_SHOULDER` |
| 13 | LS | `XUSB_GAMEPAD_LEFT_THUMB` |
| 14 | RS | `XUSB_GAMEPAD_RIGHT_THUMB` |
