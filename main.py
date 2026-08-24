import time

import vgamepad as vg


def main():
    print("Creating virtual Xbox 360 controller...")
    # This tells Windows to plug in a virtual controller
    gamepad = vg.VX360Gamepad()
    
    print("Virtual controller connected!")
    print("Holding 'A' button and moving left joystick fully right for 3 seconds...")
    
    # Press the A button
    gamepad.press_button(button=vg.XUSB_BUTTON.XUSB_GAMEPAD_A)
    # Move the left joystick 100% to the right (x=1.0, y=0.0)
    gamepad.left_joystick_float(x_value_float=1.0, y_value_float=0.0)
    
    # Send the commands to Windows
    gamepad.update()
    
    # Hold it for 3 seconds so you can see it in joy.cpl
    time.sleep(3)
    
    print("Releasing all inputs...")
    # Reset everything to neutral
    gamepad.reset()
    gamepad.update()
    
    # Keep controller alive briefly to ensure clean state
    time.sleep(0.5)
    print("Emulation test completed successfully!")

if __name__ == "__main__":
    main()
