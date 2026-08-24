import logging
import math
from typing import Any, Dict, Optional, Tuple


class InputProcessor:
    """Process raw input from mobile device sensors."""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.deadzone = float(self.config.get('deadzone', 0.1))
        self.sensitivity = float(self.config.get('sensitivity', 1.0))
        self.max_steering_angle = float(self.config.get('max_steering_angle', 90))
        self.logger = logging.getLogger(__name__)
        self._initialized = False
    
    async def initialize(self):
        """Initialize the input processor."""
        self._initialized = True
        self.logger.info("Input processor initialized")
    
    async def cleanup(self):
        """Cleanup resources."""
        self._initialized = False
        self.logger.info("Input processor cleaned up")
    
    async def process(self, input_data: Dict[str, Any]) -> Dict[str, Any]:
        """Process raw input data."""
        if not self._initialized:
            raise RuntimeError("Input processor not initialized")
        
        processed: Dict[str, Any] = {}
        
        try:
            # Process accelerometer data for steering
            if 'accelerometer' in input_data:
                x, y, z = input_data['accelerometer']
                angle = self.calculate_steering_angle(x, y, z)
                normalized_angle = angle / max(1.0, self.max_steering_angle)
                processed['steering'] = self.apply_deadzone(normalized_angle)
            
            # Process touch input for pedals
            if 'touch' in input_data:
                touch_data = input_data['touch']
                processed['gas'] = self.process_pedal_input(float(touch_data.get('gas', 0.0)))
                processed['brake'] = self.process_pedal_input(float(touch_data.get('brake', 0.0)))
            
            # Process gyroscope for additional precision
            if 'gyroscope' in input_data:
                gyro_data = input_data['gyroscope']
                processed['gyro'] = self.process_gyro(gyro_data)
            
            # Process button presses
            if 'buttons' in input_data:
                processed['buttons'] = self.process_buttons(input_data['buttons'])
            
            return processed
            
        except Exception as e:
            self.logger.error("Input processing error: %s", e)
            raise
    
    def calculate_steering_angle(self, x: float, y: float, z: float) -> float:
        """Calculate steering angle from accelerometer data in degrees."""
        gravity_magnitude = math.sqrt(x*x + y*y + z*z)
        if gravity_magnitude == 0:
            return 0.0
        
        normalized_x = x / gravity_magnitude
        angle = math.degrees(math.asin(max(-1.0, min(1.0, normalized_x))))
        angle = max(-self.max_steering_angle, min(self.max_steering_angle, angle))
        return angle
    
    def apply_deadzone(self, value: float) -> float:
        """Apply deadzone and sensitivity scaling to input value."""
        if abs(value) < self.deadzone:
            return 0.0
        
        sign = 1.0 if value > 0 else -1.0
        scaled = (abs(value) - self.deadzone) / (1.0 - self.deadzone)
        scaled *= self.sensitivity
        return sign * scaled
    
    def process_pedal_input(self, value: float) -> float:
        """Process pedal input (0-1 range)."""
        clamped = max(0.0, min(1.0, value))
        processed = math.pow(clamped, 2)  # Quadratic response curve
        return min(1.0, processed * self.sensitivity)
    
    def process_gyro(self, gyro_data: Tuple[float, float, float]) -> Dict[str, float]:
        """Process gyroscope data for additional precision."""
        x, y, z = gyro_data
        return {
            'rotation_rate_x': float(x),
            'rotation_rate_y': float(y),
            'rotation_rate_z': float(z)
        }
    
    def process_buttons(self, buttons: Dict[str, bool]) -> Dict[str, bool]:
        """Process button states with debouncing."""
        return {str(k): bool(v) for k, v in buttons.items()}
