import logging
from typing import Any, Dict, Optional


class PedalController:
    """Pedal input management for throttle and brake."""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.gas = 0.0
        self.brake = 0.0
        self._initialized = False
        self.logger = logging.getLogger(__name__)
    
    async def initialize(self):
        """Initialize pedal system."""
        self.gas = 0.0
        self.brake = 0.0
        self._initialized = True
        self.logger.info("Pedal controller initialized")
    
    async def cleanup(self):
        """Clean up pedal states."""
        self.gas = 0.0
        self.brake = 0.0
        self._initialized = False
        self.logger.info("Pedal controller cleaned up")
    
    async def set_gas(self, value: float):
        """Set throttle pedal value [0.0 - 1.0]."""
        self.gas = max(0.0, min(1.0, float(value)))
    
    async def set_brake(self, value: float):
        """Set brake pedal value [0.0 - 1.0]."""
        self.brake = max(0.0, min(1.0, float(value)))
