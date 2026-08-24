import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, Optional


class Config:
    """Configuration manager for PocketPad."""
    
    DEFAULT_CONFIG = {
        "server": {
            "host": "0.0.0.0",
            "port": 8443,
            "ssl_enabled": True,
            "cert_file": "cert/cert.pem",
            "key_file": "cert/key.pem"
        },
        "controller": {
            "max_steering_angle": 45,
            "linearity": 1.0,
            "deadzone": 0.1,
            "sensitivity": 1.0,
            "polling_rate": 240
        },
        "webrtc": {
            "ice_servers": ["stun:stun.l.google.com:19302"],
            "video_enabled": False,
            "audio_enabled": False
        },
        "usb": {
            "enabled": True,
            "adb_path": "adb",
            "port": 8443
        },
        "logging": {
            "level": "INFO",
            "file": "logs/pocketpad.log",
            "max_size": "10MB",
            "backup_count": 5
        }
    }
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self._config = self._merge_defaults(config or {})
        self.logger = logging.getLogger(__name__)
    
    @classmethod
    def load(cls, config_path: Optional[str] = None) -> "Config":
        """Load configuration from file or environment."""
        if config_path is None:
            config_path = os.environ.get('POCKETPAD_CONFIG', 'config.json')
        
        config = {}
        if os.path.exists(config_path):
            try:
                with open(config_path, 'r') as f:
                    config = json.load(f)
                logging.info("Configuration loaded from %s", config_path)
            except Exception as e:
                logging.warning("Failed to load config from %s: %s", config_path, e)
        else:
            logging.info("No config file found, using defaults")
        
        # Load from environment variables
        env_config = cls._load_from_env()
        config.update(env_config)
        
        return cls(config)
    
    def _merge_defaults(self, config: Dict[str, Any]) -> Dict[str, Any]:
        """Merge user config with defaults."""
        result = self.DEFAULT_CONFIG.copy()
        
        for key, value in config.items():
            if key in result and isinstance(result[key], dict) and isinstance(value, dict):
                merged = result[key].copy()
                merged.update(value)
                result[key] = merged
            else:
                result[key] = value
        
        return result
    
    @staticmethod
    def _load_from_env() -> Dict[str, Any]:
        """Load configuration from environment variables."""
        config: Dict[str, Any] = {}
        
        # Server settings
        if port := os.environ.get('POCKETPAD_PORT'):
            config.setdefault('server', {})['port'] = int(port)
        
        if host := os.environ.get('POCKETPAD_HOST'):
            config.setdefault('server', {})['host'] = host
        
        # Controller settings
        if angle := os.environ.get('POCKETPAD_STEERING_ANGLE'):
            config.setdefault('controller', {})['max_steering_angle'] = int(angle)
        
        if sensitivity := os.environ.get('POCKETPAD_SENSITIVITY'):
            config.setdefault('controller', {})['sensitivity'] = float(sensitivity)
        
        # Logging
        if level := os.environ.get('POCKETPAD_LOG_LEVEL'):
            config.setdefault('logging', {})['level'] = level
        
        return config
    
    def get(self, key: str, default: Any = None) -> Any:
        """Get configuration value by key."""
        keys = key.split('.')
        value = self._config
        
        for k in keys:
            if isinstance(value, dict) and k in value:
                value = value[k]
            else:
                return default
        
        return value
    
    def set(self, key: str, value: Any):
        """Set configuration value."""
        keys = key.split('.')
        config = self._config
        
        for k in keys[:-1]:
            if k not in config:
                config[k] = {}
            config = config[k]
        
        config[keys[-1]] = value
    
    def save(self, path: str):
        """Save configuration to file."""
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with open(path, 'w') as f:
            json.dump(self._config, f, indent=2)
        self.logger.info("Configuration saved to %s", path)
    
    @property
    def server(self) -> Dict[str, Any]:
        return self._config.get('server', {})
    
    @property
    def controller(self) -> Dict[str, Any]:
        return self._config.get('controller', {})
    
    @property
    def webrtc(self) -> Dict[str, Any]:
        return self._config.get('webrtc', {})
    
    @property
    def usb(self) -> Dict[str, Any]:
        return self._config.get('usb', {})
    
    @property
    def logging_config(self) -> Dict[str, Any]:
        return self._config.get('logging', {})
