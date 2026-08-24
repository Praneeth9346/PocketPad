import logging
import math
from typing import Any, Dict, Optional


class SteeringWheel:
    """Steering wheel modeling, calibration, and curve adjustment."""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.max_angle = float(self.config.get('max_angle', 90))
        self.linearity = float(self.config.get('linearity', 1.0))
        self.center_offset = 0.0
        self.current_angle = 0.0
        self.logger = logging.getLogger(__name__)
    
    async def calibrate(self):
        """Calibrate the center steering offset."""
        self.center_offset = 0.0
        self.current_angle = 0.0
        self.logger.info("Steering wheel calibrated to center")
    
    async def set_angle(self, angle: float):
        """Set steering angle clamped to max limits."""
        effective_angle = angle - self.center_offset
        self.current_angle = max(-self.max_angle, min(self.max_angle, effective_angle))
    
    async def update(self, angle: float):
        """Update steering wheel state."""
        await self.set_angle(angle)
    
    def set_max_angle(self, angle: float):
        """Set max angle clamped within bounds (15 to 90 degrees)."""
        self.max_angle = max(15.0, min(90.0, float(angle)))
    
    def s_curve(self, value: float) -> float:
        """Apply progressive S-curve linearity mapping."""
        sign = 1.0 if value >= 0 else -1.0
        magnitude = abs(value)
        
        if self.linearity == 1.0:
            return value
        
        # Power curve based on linearity
        exponent = 1.0 / max(0.1, self.linearity)
        curved = math.pow(min(1.0, magnitude), exponent)
        return sign * curved
