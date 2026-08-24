import asyncio
import pytest
from pocketpad.core.controller.steering_wheel import SteeringWheel


@pytest.fixture
def config():
    return {
        'max_angle': 90,
        'linearity': 1.0
    }


@pytest.fixture
def steering_wheel(config):
    return SteeringWheel(config)


class TestSteeringWheel:
    
    @pytest.mark.asyncio
    async def test_calibration(self, steering_wheel):
        """Test steering wheel calibration."""
        await steering_wheel.calibrate()
        assert steering_wheel.center_offset == 0.0
    
    @pytest.mark.asyncio
    async def test_angle_clamping(self, steering_wheel):
        """Test that angle is properly clamped."""
        await steering_wheel.set_angle(150)
        assert steering_wheel.current_angle == 90
        
        await steering_wheel.set_angle(-150)
        assert steering_wheel.current_angle == -90
    
    def test_s_curve_application(self, steering_wheel):
        """Test S-curve application."""
        steering_wheel.linearity = 0.5  # Non-linear
        result = steering_wheel.s_curve(0.5)
        assert 0 <= result <= 1
    
    def test_max_angle_setting(self, steering_wheel):
        """Test max angle setting."""
        steering_wheel.set_max_angle(45)
        assert steering_wheel.max_angle == 45
        
        # Test bounds
        steering_wheel.set_max_angle(10)  # Too low
        assert steering_wheel.max_angle == 15  # Should be clamped
        
        steering_wheel.set_max_angle(100)  # Too high
        assert steering_wheel.max_angle == 90  # Should be clamped
