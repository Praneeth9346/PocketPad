import asyncio
import math
import struct
import sys
import time

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except AttributeError:
        pass

import json

import websockets

from server import EXPECTED_TOKEN

WS_URI = "ws://127.0.0.1:8765"


async def run_network_benchmark():
    print(f"[TestClient] Connecting to Gamepad WebSocket server at {WS_URI}...")

    try:
        async with websockets.connect(WS_URI) as ws:
            print("[TestClient] Connected! Sending authentication hello...")
            await ws.send(json.dumps({"type": "hello", "token": EXPECTED_TOKEN}))
            ack_raw = await asyncio.wait_for(ws.recv(), timeout=2.0)
            ack = json.loads(ack_raw)
            print(f"[TestClient] Handshake acknowledged: {ack}")

            # 1. Binary Ping / Latency Benchmark
            print("\n--- [1/6] Benchmarking High-Speed Binary Packet Latency ---")
            latencies = []
            for i in range(10):
                start = time.perf_counter()
                ping_pkt = struct.pack("<BI", 0x09, int(start * 1000) & 0xFFFFFFFF)
                await ws.send(ping_pkt)
                await ws.recv()
                rtt = (time.perf_counter() - start) * 1000  # ms
                latencies.append(rtt)
                print(f" -> Binary Ping #{i+1}: RTT = {rtt:.2f} ms")
                await asyncio.sleep(0.03)

            avg_rtt = sum(latencies) / len(latencies)
            min_rtt = min(latencies)
            max_rtt = max(latencies)
            print(f"[★] Binary Latency: Avg = {avg_rtt:.2f} ms | Min = {min_rtt:.2f} ms | Max = {max_rtt:.2f} ms")

            # 2. Test 2D Right Stick (0x06) - Camera Look Around
            print("\n--- [2/6] Testing 2D Right Stick (0x06) Camera Controls ---")
            for angle_deg in [0, 90, 180, 270, 0]:
                rad = math.radians(angle_deg)
                rx = int(math.cos(rad) * 32767)
                ry = int(math.sin(rad) * 32767)
                pkt = struct.pack("<Bhh", 0x06, rx, ry)
                await ws.send(pkt)
                print(f" -> Right Stick (RS) moved to angle {angle_deg}° (rx={rx}, ry={ry})")
                await asyncio.sleep(0.05)
            # Recenter Right Stick
            await ws.send(struct.pack("<Bhh", 0x06, 0, 0))
            print(" -> Right Stick recentered.")

            # 3. Test 2D Left Stick (0x05)
            print("\n--- [3/6] Testing 2D Left Stick (0x05) Thumbstick Controls ---")
            for angle_deg in [0, 90, 180, 270, 0]:
                rad = math.radians(angle_deg)
                lx = int(math.cos(rad) * 32767)
                ly = int(math.sin(rad) * 32767)
                pkt = struct.pack("<Bhh", 0x05, lx, ly)
                await ws.send(pkt)
                print(f" -> Left Stick (LS) moved to angle {angle_deg}° (lx={lx}, ly={ly})")
                await asyncio.sleep(0.05)
            # Recenter Left Stick
            await ws.send(struct.pack("<Bhh", 0x05, 0, 0))
            print(" -> Left Stick recentered.")

            # 4. Binary Shifter & Button Speed
            print("\n--- [4/6] Testing Buttons (A, B, X, Y) ---")
            buttons = [0, 1, 2, 3]  # A, B, X, Y
            for btn_idx in buttons:
                await ws.send(struct.pack("<BBB", 0x03, btn_idx, 1))
                await asyncio.sleep(0.02)
                await ws.send(struct.pack("<BBB", 0x03, btn_idx, 0))
                await asyncio.sleep(0.02)

            # 5. Test Mouse Movement (0x07)
            print("\n--- [5/6] Testing FPS Mouse Deltas (0x07) ---")
            for i in range(5):
                dx = 15
                dy = -5
                btns = 0x01 if i == 2 else 0x00  # Left click on 3rd iteration
                pkt = struct.pack("<BhhB", 0x07, dx, dy, btns)
                await ws.send(pkt)
                print(f" -> Sent Mouse Delta dx={dx}, dy={dy}, btns={btns}")
                await asyncio.sleep(0.05)

            # 6. Test Media Keys (0x08)
            print("\n--- [6/6] Testing Media Controls (0x08) ---")
            # 0xAF = Volume Up, 0xAE = Volume Down
            for vk in [0xAF, 0xAE]:
                pkt = struct.pack("<BB", 0x08, vk)
                await ws.send(pkt)
                print(f" -> Sent Media Key VK Code={hex(vk)}")
                await asyncio.sleep(0.1)

            print("\n[TestClient] ⚡ Complete 2D Dual-Stick, Mouse, Media & Controller Benchmark Succeeded!")

    except Exception as e:
        print(f"[!] Benchmark failed with error: {e}")


if __name__ == "__main__":
    asyncio.run(run_network_benchmark())
