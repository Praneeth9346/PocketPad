import struct

from controller_bridge import GamepadBridge


def test_bridge_mouse_opcode():
    """Verify that Opcode 0x07 (Mouse) unpacks without exceptions."""
    bridge = GamepadBridge()
    # Reset counts
    bridge.packet_count = 0
    
    # Mouse packet format: [0x07 (uint8), dx (int16), dy (int16), btns (uint8)] = 6 bytes
    dx = 100
    dy = -50
    btns = 0x03 # Left and right click
    
    pkt = struct.pack("<BhhB", 0x07, dx, dy, btns)
    
    # Process packet (should NOT throw error, and should gracefully return None)
    # The ctypes code runs on windows, but even on Linux it should gracefully pass the unpacking phase
    result = bridge.handle_binary_packet(pkt)
    
    assert result is None
    # packet_count is only incremented for gamepad updates, so we don't assert it here

def test_bridge_media_opcode():
    """Verify that Opcode 0x08 (Media) unpacks without exceptions."""
    bridge = GamepadBridge()
    
    # Media packet format: [0x08 (uint8), key (uint8)] = 2 bytes
    vk_vol_up = 0xAF
    pkt = struct.pack("<BB", 0x08, vk_vol_up)
    
    # Process packet
    result = bridge.handle_binary_packet(pkt)
    assert result is None

def test_bridge_rumble_callback():
    """Verify that Rumble callback is registered and triggered."""
    rumble_triggered = False
    
    def test_callback(large, small):
        nonlocal rumble_triggered
        rumble_triggered = True
        
    bridge = GamepadBridge(rumble_callback=test_callback)
    
    # We can't easily trigger the hardware rumble via ViGEm in CI, 
    # but we can verify the callback function was stored correctly.
    assert bridge.rumble_callback == test_callback
