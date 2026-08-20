import asyncio
import math
import socket
import struct
import sys
import threading
import time

TELEMETRY_OUT_STRUCT = struct.Struct("<BHHHBBBBBB") # 13-byte compact binary packet (Opcode 0x10)

class ForzaTelemetryServer:
    """
    High-speed UDP Telemetry Collector for Forza Horizon 4/5 & Forza Motorsport.
    Listens on UDP port 5300 and converts 232 / 311 / 324 / 332 byte telemetry into a 13-byte binary stream.
    """

    def __init__(self, port: int = 5300, broadcast_callback = None):
        self.port = port
        self.broadcast_callback = broadcast_callback
        self.is_running = False
        self.last_packet_time = 0
        self.is_live = False
        self.demo_mode = False
        self.demo_task = None
        self.sock = None
        self.loop = None
        self.thread = None

        # Telemetry State Cache
        self.current_rpm = 0
        self.max_rpm = 8500
        self.speed_mph = 0.0
        self.speed_kmh = 0.0
        self.gear = 1
        self.accel = 0
        self.brake = 0
        self.slip_ratio = 0.0
        self.boost = 0.0
        self.race_pos = 1

    def parse_forza_packet(self, data: bytes) -> bytes | None:
        """Parse raw Forza UDP packet (supports 232, 311, 324, 332 bytes)."""
        length = len(data)
        if length < 232:
            return None

        try:
            is_race_on = struct.unpack_from("<i", data, 0)[0]
            max_rpm = max(1000.0, struct.unpack_from("<f", data, 8)[0])
            idle_rpm = struct.unpack_from("<f", data, 12)[0]
            cur_rpm = max(0.0, struct.unpack_from("<f", data, 16)[0])

            if length >= 311:
                speed_ms = max(0.0, struct.unpack_from("<f", data, 244)[0]) if length >= 248 else 0.0
                gear_raw = data[307] if length > 307 else 1
                accel_byte = data[303] if length > 303 else 0
                brake_byte = data[304] if length > 304 else 0
                boost_psi = max(0.0, struct.unpack_from("<f", data, 272)[0]) if length >= 276 else 0.0

                if length >= 100:
                    s_fl, s_fr, s_rl, s_rr = struct.unpack_from("<ffff", data, 84)
                    max_slip = max(abs(s_fl), abs(s_fr), abs(s_rl), abs(s_rr))
                else:
                    max_slip = 0.0
            else:
                vx, vy, vz = struct.unpack_from("<fff", data, 32)
                speed_ms = math.sqrt(vx*vx + vy*vy + vz*vz)
                gear_raw = 1
                accel_byte = 0
                brake_byte = 0
                boost_psi = 0.0
                max_slip = 0.0

            self.is_live = bool(is_race_on != 0 or cur_rpm > 100)
            self.last_packet_time = time.time()
            self.current_rpm = int(cur_rpm)
            self.max_rpm = int(max_rpm)
            self.speed_mph = speed_ms * 2.23694
            self.speed_kmh = speed_ms * 3.6
            self.gear = gear_raw
            self.accel = accel_byte
            self.brake = brake_byte
            self.slip_ratio = min(1.0, max_slip)
            self.boost = boost_psi

            shift_pct = int(min(100.0, (cur_rpm / max_rpm) * 100.0))
            slip_pct = int(min(100.0, self.slip_ratio * 100.0))
            boost_byte = int(min(255, max(0, int(boost_psi))))

            return TELEMETRY_OUT_STRUCT.pack(
                0x10,
                self.current_rpm,
                self.max_rpm,
                int(self.speed_mph * 10),
                self.gear,
                shift_pct,
                slip_pct,
                self.accel,
                self.brake,
                boost_byte
            )
        except Exception:
            return None

    def start_udp_listener(self, loop):
        """Start high-performance threaded UDP receiver (Windows-compatible)."""
        self.loop = loop
        self.is_running = True
        self.thread = threading.Thread(target=self._udp_worker, daemon=True)
        self.thread.start()

    def _udp_worker(self):
        """Synchronous high-speed UDP worker thread."""
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.sock.bind(("0.0.0.0", self.port))
            print(f"[Telemetry] 🏎️ Forza Horizon Telemetry Server listening on UDP port {self.port}...")

            while self.is_running:
                try:
                    data, _ = self.sock.recvfrom(512)
                    pkt = self.parse_forza_packet(data)
                    if pkt and self.broadcast_callback:
                        self.broadcast_callback(pkt)
                except Exception:
                    pass
        except Exception as e:
            print(f"[Telemetry] Could not bind UDP port {self.port}: {e}")

    def toggle_demo_mode(self):
        """Toggle a high-octane simulated race for dashboard testing."""
        self.demo_mode = not self.demo_mode
        if self.demo_mode:
            if self.loop and (not self.demo_task or self.demo_task.done()):
                self.demo_task = self.loop.create_task(self._demo_telemetry_loop())
            print("[Telemetry] 🏁 Demo Telemetry Simulation: STARTED")
        else:
            print("[Telemetry] 🏁 Demo Telemetry Simulation: STOPPED")
        return self.demo_mode

    async def _demo_telemetry_loop(self):
        """Simulate realistic GT car acceleration, paddle shifts, and drift slip (60 FPS)."""
        gear_ratios = [0, 48, 80, 115, 150, 185, 220]
        max_rpm = 8500
        idle_rpm = 1000
        current_gear = 1
        speed_mph = 0.0
        current_rpm = idle_rpm
        slip = 0.0

        while self.demo_mode:
            top_speed_for_gear = gear_ratios[min(len(gear_ratios)-1, current_gear)]
            
            speed_mph += 0.85
            current_rpm = idle_rpm + (speed_mph / top_speed_for_gear) * (max_rpm - idle_rpm)

            # High-RPM Shift Up at 8350 RPM
            if current_rpm >= 8350:
                current_rpm = 5300
                current_gear = min(6, current_gear + 1)
                slip = 0.50 # Shift chirp / drift
            else:
                slip = max(0.0, slip - 0.03)

            # Loop back to 1st gear after top speed
            if speed_mph > 195:
                speed_mph = 0.0
                current_gear = 1
                current_rpm = idle_rpm

            shift_pct = int(min(100.0, (current_rpm / max_rpm) * 100.0))
            slip_pct = int(min(100.0, slip * 100.0))

            pkt = TELEMETRY_OUT_STRUCT.pack(
                0x10,
                int(current_rpm),
                int(max_rpm),
                int(speed_mph * 10),
                int(current_gear),
                int(shift_pct),
                int(slip_pct),
                255,
                0,
                14
            )

            if self.broadcast_callback:
                self.broadcast_callback(pkt)

            await asyncio.sleep(0.016) # 60 FPS
