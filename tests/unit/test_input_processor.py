import asyncio
import pytest
from pocketpad.core.controller.input_processor import InputProcessor


@pytest.fixture
def config():
    return {
        'deadzone': 0.1,
        'sensitivity': 1.0,
        'max_steering_angle': 90
    }


@pytest.fixture
async def processor(config):
    proc = InputProcessor(config)
    await proc.initialize()
    yield proc
    await proc.cleanup()


class TestInputProcessor:
    
    @pytest.mark.asyncio
    async def test_process_accelerometer_input(self, processor):
        """Test processing of accelerometer input."""
        input_data = {
            'accelerometer': (0.5, 0.0, 0.866)  # 30 degree tilt
        }
        
        result = await processor.process(input_data)
        
        assert 'steering' in result
        assert isinstance(result['steering'], float)
        assert abs(result['steering']) > 0
    
    @pytest.mark.asyncio
    async def test_deadzone_application(self, processor):
        """Test that deadzone is properly applied."""
        # Input below deadzone
        input_data = {
            'accelerometer': (0.05, 0.0, 0.998)  # Small angle
        }
        
        result = await processor.process(input_data)
        assert result['steering'] == 0.0
        
        # Input above deadzone
        input_data = {
            'accelerometer': (0.2, 0.0, 0.979)  # Larger angle
        }
        
        result = await processor.process(input_data)
        assert abs(result['steering']) > 0
    
    @pytest.mark.asyncio
    async def test_pedal_input_processing(self, processor):
        """Test pedal input processing."""
        input_data = {
            'touch': {
                'gas': 0.5,
                'brake': 0.3
            }
        }
        
        result = await processor.process(input_data)
        
        assert 'gas' in result
        assert 'brake' in result
        assert 0 <= result['gas'] <= 1
        assert 0 <= result['brake'] <= 1
    
    def test_sensitivity_scaling(self, processor):
        """Test sensitivity scaling."""
        processor.sensitivity = 2.0
        value = processor.apply_deadzone(0.5)
        assert abs(value) > 0.5  # Should be scaled up
