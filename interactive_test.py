import time

import vgamepad as vg


def run_feature_showcase():
    print("==========================================")
    print("  Virtual Xbox 360 Controller Showcase")
    print("==========================================")
    print("Initializing VX360Gamepad...")
    gamepad = vg.VX360Gamepad()
    time.sleep(1)

    print("\n[1/5] Testing Face Buttons (A -> B -> X -> Y)...")
    buttons = [
        ("A (Bottom)", vg.XUSB_BUTTON.XUSB_GAMEPAD_A),
        ("B (Right)", vg.XUSB_BUTTON.XUSB_GAMEPAD_B),
        ("X (Left)", vg.XUSB_BUTTON.XUSB_GAMEPAD_X),
        ("Y (Top)", vg.XUSB_BUTTON.XUSB_GAMEPAD_Y),
    ]
    for name, btn in buttons:
        print(f" -> Pressing {name}")
        gamepad.press_button(button=btn)
        gamepad.update()
        time.sleep(0.6)
        gamepad.release_button(button=btn)
        gamepad.update()
        time.sleep(0.3)

    print("\n[2/5] Testing D-Pad (Up, Down, Left, Right)...")
    dpad_dirs = [
        ("Up", vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP),
        ("Right", vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT),
        ("Down", vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN),
        ("Left", vg.XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT),
    ]
    for name, btn in dpad_dirs:
        print(f" -> D-Pad {name}")
        gamepad.press_button(button=btn)
        gamepad.update()
        time.sleep(0.5)
        gamepad.release_button(button=btn)
        gamepad.update()
        time.sleep(0.2)

    print("\n[3/5] Testing Analog Triggers (Left Trigger -> Right Trigger)...")
    print(" -> Left Trigger 100%")
    gamepad.left_trigger_float(value_float=1.0)
    gamepad.update()
    time.sleep(0.8)
    gamepad.left_trigger_float(value_float=0.0)
    
    print(" -> Right Trigger 100%")
    gamepad.right_trigger_float(value_float=1.0)
    gamepad.update()
    time.sleep(0.8)
    gamepad.right_trigger_float(value_float=0.0)
    gamepad.update()

    print("\n[4/5] Testing Left & Right Thumbsticks (Circular motion)...")
    import math
    steps = 20
    for i in range(steps):
        angle = (2 * math.pi * i) / steps
        x = math.cos(angle)
        y = math.sin(angle)
        gamepad.left_joystick_float(x_value_float=x, y_value_float=y)
        gamepad.right_joystick_float(x_value_float=-x, y_value_float=-y)
        gamepad.update()
        time.sleep(0.05)

    print(" -> Centering thumbsticks")
    gamepad.left_joystick_float(0.0, 0.0)
    gamepad.right_joystick_float(0.0, 0.0)
    gamepad.update()

    print("\n[5/5] Resetting controller to neutral state...")
    gamepad.reset()
    gamepad.update()
    print("\nShowcase complete! Controller is ready.")

if __name__ == "__main__":
    run_feature_showcase()
