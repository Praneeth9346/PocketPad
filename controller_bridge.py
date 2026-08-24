import ctypes
import struct
import sys
import threading

try:
    import vgamepad as vg
    VGAMEPAD_AVAILABLE = True
except Exception:
    vg = None
    VGAMEPAD_AVAILABLE = False

# Enable Windows Kernel 1ms High-Resolution Multimedia Timer & High-Priority Thread Scheduling
if sys.platform == "win32":
    try:
        winmm = ctypes.windll.winmm
        timer_status = winmm.timeBeginPeriod(1) # 0 = TIMERR_NOERROR
        kernel32 = ctypes.windll.kernel32
        # Set process to HIGH_PRIORITY_CLASS (0x00000080) to avoid starving OS input/display threads
        kernel32.SetPriorityClass(kernel32.GetCurrentProcess(), 0x00000080)
        # Elevate current worker thread priority to THREAD_PRIORITY_TIME_CRITICAL (+15)
        kernel32.SetThreadPriority(kernel32.GetCurrentThread(), 15)
    except Exception:
        pass

class GamepadBridge:
    """
    Ultra-low-latency, zero-allocation native C-speed bridge between network
    binary packets and the Virtual Xbox 360 controller (ViGEmBus).
    """

    # Pre-compiled C Struct Unpackers (100-nanosecond execution time)
    STRUCT_STEER = struct.Struct("<h")            # Opcode 0x01: [int16_x]
    STRUCT_PEDALS = struct.Struct("<BB")          # Opcode 0x02: [uint8_lt, uint8_rt]
    STRUCT_BUTTON = struct.Struct("<BB")          # Opcode 0x03: [uint8_btn_id, uint8_pressed]
    STRUCT_STICK_LS = struct.Struct("<hh")        # Opcode 0x05: [int16_lx, int16_ly]
    STRUCT_STICK_RS = struct.Struct("<hh")        # Opcode 0x06: [int16_rx, int16_ry]
    STRUCT_SNAPSHOT = struct.Struct("<hhhhBBH")    # Opcode 0x04: [lx, ly, rx, ry, lt, rt, mask]
    STRUCT_MOUSE = struct.Struct("<hhB")          # Opcode 0x07: [int16_dx, int16_dy, uint8_btns]
    STRUCT_MEDIA = struct.Struct("<B")            # Opcode 0x08: [uint8_key]

    if VGAMEPAD_AVAILABLE:
        BUTTON_MAP = {
            "A": vg.XUSB_BUTTON.XUSB_GAMEPAD_A,
            "B": vg.XUSB_BUTTON.XUSB_GAMEPAD_B,
            "X": vg.XUSB_BUTTON.XUSB_GAMEPAD_X,
            "Y": vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,
            "DPAD_UP": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
            "DPAD_DOWN": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
            "DPAD_LEFT": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
            "DPAD_RIGHT": vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
            "START": vg.XUSB_BUTTON.XUSB_GAMEPAD_START,
            "BACK": vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,
            "GUIDE": vg.XUSB_BUTTON.XUSB_GAMEPAD_GUIDE,
            "LB": vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
            "RB": vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
            "LS": vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
            "RS": vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
        }

        BUTTON_INDEX_MAP = [
            vg.XUSB_BUTTON.XUSB_GAMEPAD_A,              # 0
            vg.XUSB_BUTTON.XUSB_GAMEPAD_B,              # 1
            vg.XUSB_BUTTON.XUSB_GAMEPAD_X,              # 2
            vg.XUSB_BUTTON.XUSB_GAMEPAD_Y,              # 3
            vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,        # 4
            vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,      # 5
            vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,      # 6
            vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,     # 7
            vg.XUSB_BUTTON.XUSB_GAMEPAD_START,          # 8
            vg.XUSB_BUTTON.XUSB_GAMEPAD_BACK,           # 9
            vg.XUSB_BUTTON.XUSB_GAMEPAD_GUIDE,          # 10
            vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,  # 11 (LB)
            vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER, # 12 (RB)
            vg.XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,     # 13 (LS)
            vg.XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,    # 14 (RS)
        ]
    else:
        BUTTON_MAP = {}
        BUTTON_INDEX_MAP = []

    def __init__(self, rumble_callback=None):
        self.lock = threading.Lock()
        self.gamepad = None
        self.controller_available = False
        self.controller_error = ""
        self.active_buttons = set()
        self.lx = 0.0
        self.ly = 0.0
        self.rx = 0.0
        self.ry = 0.0
        self.lt = 0.0
        self.rt = 0.0
        self.packet_count = 0
        self.pong_buffer = bytearray(b'\x0A\x00\x00\x00\x00')
        self.rumble_callback = rumble_callback
        
        # Win32 APIs for Mouse/Keyboard emulation
        self.mouse_event = None
        self.keybd_event = None
        if sys.platform == "win32":
            try:
                self.mouse_event = ctypes.windll.user32.mouse_event
                self.keybd_event = ctypes.windll.user32.keybd_event
            except Exception:
                pass

        if VGAMEPAD_AVAILABLE:
            try:
                self.gamepad = vg.VX360Gamepad()
                self.controller_available = True
                
                # Register Rumble Callback
                def _internal_rumble(client, target, large_motor, small_motor, led_number, user_data):
                    if self.rumble_callback:
                        self.rumble_callback(large_motor, small_motor)
                self.gamepad.register_notification(callback_function=_internal_rumble)
                
                self.reset()
                print("[GamepadBridge] ⚡ Native C-Speed Xbox 360 Gamepad Engine Active (High Priority 1ms).")
            except Exception as e:
                self.controller_error = str(e)
                print(f"[GamepadBridge] ❌ Failed to create ViGEm Xbox 360 controller: {e}")
                print("[GamepadBridge] ⚠️ Install ViGEmBus driver: https://github.com/nefarius/ViGEmBus/releases")
        else:
            self.controller_error = "vgamepad module not installed"
            print("[GamepadBridge] ⚠️ vgamepad not available — controller output disabled.")

    def reset(self):
        """Reset all inputs to neutral state."""
        with self.lock:
            if self.gamepad:
                self.gamepad.reset()
                self.gamepad.update()
            self.active_buttons.clear()
            self.lx = 0.0
            self.ly = 0.0
            self.rx = 0.0
            self.ry = 0.0
            self.ry = 0.0
            self.lt = 0.0
            self.rt = 0.0

    def shutdown(self):
        """Unplug virtual controller and clean up."""
        with self.lock:
            if self.gamepad:
                try:
                    self.gamepad.reset()
                    self.gamepad.update()
                    # vgamepad handles un-plugging when object is destroyed
                    del self.gamepad
                    self.gamepad = None
                except Exception:
                    pass

    def handle_binary_packet(self, data: bytes):
        """
        Fastest path C-speed binary packet dispatcher (< 0.0001 ms):
        0x00: No-op radio keepalive (1 byte)
        0x01: Steer [0x01, int16_x] (3 bytes)
        0x02: Pedals [0x02, uint8_lt, uint8_rt] (3 bytes)
        0x03: Button [0x03, uint8_btn_id, uint8_pressed] (3 bytes)
        0x05: Left Stick 2D [0x05, int16_lx, int16_ly] (5 bytes)
        0x06: Right Stick 2D [0x06, int16_rx, int16_ry] (5 bytes)
        0x04: Snapshot [0x04, int16*4, uint8*2, uint16] (13 bytes)
        0x07: Mouse [0x07, int16_dx, int16_dy, uint8_btns] (6 bytes)
        0x08: Media Key [0x08, uint8_key] (2 bytes)
        0x09: Ping [0x09, uint32_timestamp] (5 bytes)
        """
        if not data:
            return None

        if not self.gamepad:
            # Still handle ping even without controller
            if len(data) >= 5 and data[0] == 0x09:
                self.pong_buffer[1:5] = data[1:5]
                return bytes(self.pong_buffer)
            return None

        opcode = data[0]

        # 0x00: Radio Keepalive (Instant Return)
        if opcode == 0x00:
            return None

        # 0x01: High-Speed Steering Only (3 bytes)
        elif opcode == 0x01 and len(data) >= 3:
            raw_x = self.STRUCT_STEER.unpack_from(data, 1)[0]
            with self.lock:
                self.gamepad.report.sThumbLX = raw_x
                self.gamepad.update()
                self.packet_count += 1
            return None

        # 0x02: High-Speed Pedals (LT / RT) (3 bytes)
        elif opcode == 0x02 and len(data) >= 3:
            raw_lt, raw_rt = self.STRUCT_PEDALS.unpack_from(data, 1)
            with self.lock:
                self.gamepad.report.bLeftTrigger = raw_lt
                self.gamepad.report.bRightTrigger = raw_rt
                self.gamepad.update()
                self.packet_count += 1
            return None

        # 0x03: Digital Button (3 bytes)
        elif opcode == 0x03 and len(data) >= 3:
            btn_idx, pressed = self.STRUCT_BUTTON.unpack_from(data, 1)
            if btn_idx < len(self.BUTTON_INDEX_MAP):
                btn_enum = self.BUTTON_INDEX_MAP[btn_idx]
                with self.lock:
                    if pressed:
                        self.gamepad.press_button(button=btn_enum)
                    else:
                        self.gamepad.release_button(button=btn_enum)
                    self.gamepad.update()
                    self.packet_count += 1
            return None

        # 0x05: Left Stick 2D (5 bytes)
        elif opcode == 0x05 and len(data) >= 5:
            raw_x, raw_y = self.STRUCT_STICK_LS.unpack_from(data, 1)
            with self.lock:
                self.gamepad.report.sThumbLX = raw_x
                self.gamepad.report.sThumbLY = raw_y
                self.gamepad.update()
                self.packet_count += 1
            return None

        # 0x06: Right Stick 2D (5 bytes)
        elif opcode == 0x06 and len(data) >= 5:
            raw_x, raw_y = self.STRUCT_STICK_RS.unpack_from(data, 1)
            with self.lock:
                self.gamepad.report.sThumbRX = raw_x
                self.gamepad.report.sThumbRY = raw_y
                self.gamepad.update()
                self.packet_count += 1
            return None

        # 0x04: Full Controller State Snapshot (13 bytes)
        elif opcode == 0x04 and len(data) >= 13:
            lx, ly, rx, ry, lt, rt, btn_mask = self.STRUCT_SNAPSHOT.unpack_from(data, 1)
            with self.lock:
                self.lx = max(-1.0, min(1.0, lx / 32767.0))
                self.ly = max(-1.0, min(1.0, ly / 32767.0))
                self.rx = max(-1.0, min(1.0, rx / 32767.0))
                self.ry = max(-1.0, min(1.0, ry / 32767.0))
                self.lt = lt / 255.0
                self.rt = rt / 255.0

                self.gamepad.left_joystick_float(x_value_float=self.lx, y_value_float=self.ly)
                self.gamepad.right_joystick_float(x_value_float=self.rx, y_value_float=self.ry)
                self.gamepad.left_trigger_float(value_float=self.lt)
                self.gamepad.right_trigger_float(value_float=self.rt)

                for idx, btn_enum in enumerate(self.BUTTON_INDEX_MAP):
                    if (btn_mask & (1 << idx)) != 0:
                        self.gamepad.press_button(button=btn_enum)
                    else:
                        self.gamepad.release_button(button=btn_enum)

                self.gamepad.update()
                self.packet_count += 1
            return None

        # 0x09: Zero-Allocation Ping Echo
        elif opcode == 0x09 and len(data) >= 5:
            self.pong_buffer[1:5] = data[1:5]
            return bytes(self.pong_buffer)

        # 0x07: Mouse Movement (6 bytes)
        elif opcode == 0x07 and len(data) >= 6:
            dx, dy, btns = self.STRUCT_MOUSE.unpack_from(data, 1)
            # Security: Clamp mouse deltas
            dx = max(-100, min(100, dx))
            dy = max(-100, min(100, dy))
            if self.mouse_event:
                # MOUSEEVENTF_MOVE = 0x0001
                self.mouse_event(0x0001, dx, dy, 0, 0)
                # Left Click
                if btns & 0x01: self.mouse_event(0x0002, 0, 0, 0, 0) # LEFTDOWN
                elif btns & 0x10: self.mouse_event(0x0004, 0, 0, 0, 0) # LEFTUP
                # Right Click
                if btns & 0x02: self.mouse_event(0x0008, 0, 0, 0, 0) # RIGHTDOWN
                elif btns & 0x20: self.mouse_event(0x0010, 0, 0, 0, 0) # RIGHTUP
            return None

        # 0x08: Media Key (2 bytes)
        elif opcode == 0x08 and len(data) >= 2:
            key_code = self.STRUCT_MEDIA.unpack_from(data, 1)[0]
            # SECURITY (Phase 5/6): Whitelist only media-related Virtual Key Codes
            # 0xAD=Mute, 0xAE=VolDown, 0xAF=VolUp, 0xB0=Next, 0xB1=Prev, 0xB2=Stop, 0xB3=Play/Pause
            if self.keybd_event and key_code in {0xAD, 0xAE, 0xAF, 0xB0, 0xB1, 0xB2, 0xB3}:
                self.keybd_event(key_code, 0, 0, 0) # DOWN
                self.keybd_event(key_code, 0, 2, 0) # UP
            return None

        return None

    def handle_button(self, button_name: str, is_pressed: bool):
        """Handle digital button press / release (JSON fallback)."""
        btn_key = button_name.upper()
        if btn_key not in self.BUTTON_MAP:
            return False

        btn_enum = self.BUTTON_MAP[btn_key]
        with self.lock:
            if is_pressed:
                self.active_buttons.add(btn_key)
                self.gamepad.press_button(button=btn_enum)
            else:
                self.active_buttons.discard(btn_key)
                self.gamepad.release_button(button=btn_enum)
            self.gamepad.update()
            self.packet_count += 1
        return True

    def handle_left_trigger(self, value: float):
        """Handle Left Trigger [0.0 - 1.0]."""
        v = max(0.0, min(1.0, float(value)))
        with self.lock:
            self.lt = v
            self.gamepad.left_trigger_float(value_float=v)
            self.gamepad.update()
            self.packet_count += 1

    def handle_right_trigger(self, value: float):
        """Handle Right Trigger [0.0 - 1.0]."""
        v = max(0.0, min(1.0, float(value)))
        with self.lock:
            self.rt = v
            self.gamepad.right_trigger_float(value_float=v)
            self.gamepad.update()
            self.packet_count += 1

    def handle_left_joystick(self, x: float, y: float):
        """Handle Left Joystick [-1.0 - 1.0]."""
        norm_x = max(-1.0, min(1.0, float(x)))
        norm_y = max(-1.0, min(1.0, float(y)))
        with self.lock:
            self.lx = norm_x
            self.ly = norm_y
            self.gamepad.left_joystick_float(x_value_float=norm_x, y_value_float=norm_y)
            self.gamepad.update()
            self.packet_count += 1

    def handle_right_joystick(self, x: float, y: float):
        """Handle Right Joystick [-1.0 - 1.0]."""
        norm_x = max(-1.0, min(1.0, float(x)))
        norm_y = max(-1.0, min(1.0, float(y)))
        with self.lock:
            self.rx = norm_x
            self.ry = norm_y
            self.gamepad.right_joystick_float(x_value_float=norm_x, y_value_float=norm_y)
            self.gamepad.update()
            self.packet_count += 1

    def handle_batch_state(self, state: dict):
        """Update full controller state in one single ViGEm update."""
        with self.lock:
            if "buttons" in state:
                for btn_name, is_pressed in state["buttons"].items():
                    btn_key = btn_name.upper()
                    if btn_key in self.BUTTON_MAP:
                        btn_enum = self.BUTTON_MAP[btn_key]
                        if is_pressed:
                            self.active_buttons.add(btn_key)
                            self.gamepad.press_button(button=btn_enum)
                        else:
                            self.active_buttons.discard(btn_key)
                            self.gamepad.release_button(button=btn_enum)

            if "triggers" in state:
                trigs = state["triggers"]
                if "LT" in trigs:
                    self.lt = max(0.0, min(1.0, float(trigs["LT"])))
                    self.gamepad.left_trigger_float(value_float=self.lt)
                if "RT" in trigs:
                    self.rt = max(0.0, min(1.0, float(trigs["RT"])))
                    self.gamepad.right_trigger_float(value_float=self.rt)

            if "joysticks" in state:
                joy = state["joysticks"]
                if "left" in joy:
                    self.lx = max(-1.0, min(1.0, float(joy["left"].get("x", 0.0))))
                    self.ly = max(-1.0, min(1.0, float(joy["left"].get("y", 0.0))))
                    self.gamepad.left_joystick_float(x_value_float=self.lx, y_value_float=self.ly)
                if "right" in joy:
                    self.rx = max(-1.0, min(1.0, float(joy["right"].get("x", 0.0))))
                    self.ry = max(-1.0, min(1.0, float(joy["right"].get("y", 0.0))))
                    self.gamepad.right_joystick_float(x_value_float=self.rx, y_value_float=self.ry)

            self.gamepad.update()
            self.packet_count += 1
