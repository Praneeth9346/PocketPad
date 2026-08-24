import asyncio
import pytest
from pocketpad.config import Config
from pocketpad.core.controller.controller_bridge import ControllerBridge


@pytest.fixture
def config():
    return Config.load()


@pytest.fixture
async def controller_bridge(config):
    bridge = ControllerBridge(config.controller)
    await bridge.connect()
    yield bridge
    await bridge.disconnect()


class TestControllerBridge:
    
    @pytest.mark.asyncio
    async def test_connection(self, controller_bridge):
        """Test controller bridge connection."""
        assert controller_bridge.connected is True
    
    @pytest.mark.asyncio
    async def test_input_processing(self, controller_bridge):
        """Test input processing through bridge."""
        input_data = {
            'accelerometer': (0.3, 0.0, 0.953),  # ~17.5 degrees
            'touch': {'gas': 0.5},
            'buttons': {'a': True}
        }
        
        # Should not raise an exception
        await controller_bridge.process_input(input_data)
