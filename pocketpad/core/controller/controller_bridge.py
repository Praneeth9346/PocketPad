import asyncio
import logging
from typing import Any, Dict, Optional

from .input_processor import InputProcessor
from .pedals import PedalController
from .steering_wheel import SteeringWheel


class ControllerBridge:
    """Bridge between mobile device input and game controller emulation."""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.input_processor = InputProcessor(self.config.get('input', self.config))
        self.steering_wheel = SteeringWheel(self.config.get('steering', self.config))
        self.pedals = PedalController(self.config.get('pedals', self.config))
        self.connected = False
        self.logger = logging.getLogger(__name__)
    
    async def connect(self):
        """Connect to the controller system."""
        try:
            await self.input_processor.initialize()
            await self.steering_wheel.calibrate()
            await self.pedals.initialize()
            self.connected = True
            self.logger.info("Controller bridge connected successfully")
        except Exception as e:
            self.logger.error("Failed to connect controller: %s", e)
            raise ConnectionError(f"Controller connection failed: {e}")
    
    async def disconnect(self):
        """Disconnect the controller."""
        if not self.connected:
            return
        
        try:
            await self.input_processor.cleanup()
            await self.pedals.cleanup()
            self.connected = False
            self.logger.info("Controller bridge disconnected")
        except Exception as e:
            self.logger.error("Error during disconnect: %s", e)
    
    async def process_input(self, input_data: Dict[str, Any]):
        """Process input from mobile device."""
        if not self.connected:
            self.logger.warning("Input received but controller not connected")
            return
        
        try:
            # Process different input types
            processed = await self.input_processor.process(input_data)
            
            # Update steering wheel
            if 'steering' in processed:
                await self.steering_wheel.update(processed['steering'])
            
            # Update pedals
            if 'gas' in processed:
                await self.pedals.set_gas(processed['gas'])
            if 'brake' in processed:
                await self.pedals.set_brake(processed['brake'])
            
            # Handle button presses
            if 'buttons' in processed:
                await self._handle_buttons(processed['buttons'])
                
        except Exception as e:
            self.logger.error("Input processing error: %s", e)
            # Don't re-raise in stream processing to maintain connection
    
    async def _handle_buttons(self, buttons: Dict[str, bool]):
        """Handle button press events."""
        for button, pressed in buttons.items():
            if pressed:
                await self._press_button(button)
            else:
                await self._release_button(button)
    
    async def _press_button(self, button: str):
        """Press a controller button."""
        button_map = {
            'a': 0x1000,
            'b': 0x2000,
            'x': 0x4000,
            'y': 0x8000,
        }
        btn_key = button.lower()
        if btn_key in button_map:
            self.logger.debug("Button pressed: %s", button)
    
    async def _release_button(self, button: str):
        """Release a controller button."""
        self.logger.debug("Button released: %s", button)
    
    async def calibrate(self):
        """Calibrate the steering wheel."""
        await self.steering_wheel.calibrate()
        self.logger.info("Steering wheel calibrated")
