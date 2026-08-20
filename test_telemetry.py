import asyncio
import socket
import struct
import sys
import time

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import websockets

UDP_PORT = 5300
WS_URI = "ws://127.0.0.1:8765"

async def test_live_telemetry():
    print("[TelemetryTest] Connecting to Gamepad WebSocket server...")
    async with websockets.connect(WS_URI) as ws:
        print("[TelemetryTest] Connected to WebSocket! Sending simulated Forza UDP telemetry packet...")

        # Construct a simulated Forza 324-byte packet:
        # Race on=1, max_rpm=8500, cur_rpm=7800, speed=68.5 m/s (153 MPH), gear=5, boost=14.2 PSI
        dummy = bytearray(324)
        struct.pack_into("<i", dummy, 0, 1)        # is_race_on
        struct.pack_into("<f", dummy, 8, 8500.0)   # max_rpm
        struct.pack_into("<f", dummy, 16, 7800.0)  # cur_rpm
        struct.pack_into("<f", dummy, 84, 0.45)    # slip_ratio (45% drift)
        struct.pack_into("<f", dummy, 244, 68.5)   # speed (m/s) -> 153.2 MPH
        struct.pack_into("<f", dummy, 272, 14.2)   # boost (PSI)
        dummy[307] = 5                             # Gear 5
        dummy[303] = 255                           # 100% Throttle

        # Send to UDP port 5300
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.sendto(dummy, ("127.0.0.1", UDP_PORT))
        print("[TelemetryTest] UDP packet sent to 127.0.0.1:5300. Waiting for WebSocket binary telemetry frame...")

        # Receive telemetry from WebSocket
        resp = await asyncio.wait_for(ws.recv(), timeout=3.0)
        if isinstance(resp, bytes) and len(resp) >= 13 and resp[0] == 0x10:
            opcode, rpm, max_rpm, spd10, gear, shift_pct, slip_pct, accel, brake, boost = struct.unpack("<BHHHBBBBBB", resp)
            print(f"[TelemetryTest] ✅ SUCCESS! Received Opcode 0x10 Telemetry Frame:")
            print(f"  -> RPM:        {rpm} / {max_rpm}")
            print(f"  -> Speed:      {spd10/10.0:.1f} MPH")
            print(f"  -> Gear:       {gear}")
            print(f"  -> Shift Pct:  {shift_pct}%")
            print(f"  -> Slip/Drift: {slip_pct}%")
            print(f"  -> Boost:      {boost} PSI")
        else:
            print(f"[TelemetryTest] Received unexpected packet: {resp}")

if __name__ == "__main__":
    asyncio.run(test_live_telemetry())
