import logging
from typing import Any, Dict, List, Optional


class WebRTCManager:
    """Optional WebRTC peer connection manager for zero-install streaming."""
    
    def __init__(self, ice_servers: Optional[List[str]] = None):
        self.ice_servers = ice_servers or ["stun:stun.l.google.com:19302"]
        self.logger = logging.getLogger(__name__)
        self.active_channels: Dict[str, Any] = {}
    
    async def create_offer(self) -> Dict[str, Any]:
        """Create WebRTC offer session descriptor."""
        return {
            "type": "offer",
            "ice_servers": self.ice_servers
        }
    
    async def handle_answer(self, answer: Dict[str, Any]):
        """Process remote peer WebRTC answer."""
        self.logger.debug("Received WebRTC answer: %s", answer)
