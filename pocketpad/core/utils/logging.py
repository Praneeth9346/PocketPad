import logging
import logging.handlers
import sys
from pathlib import Path
from typing import Any, Dict


def setup_logging(config: Dict[str, Any]) -> logging.Logger:
    """Setup application logging with file rotation and console stream."""
    log_level = getattr(logging, str(config.get('level', 'INFO')).upper(), logging.INFO)
    log_file = config.get('file', 'logs/pocketpad.log')
    max_size = config.get('max_size', '10MB')
    backup_count = int(config.get('backup_count', 5))
    
    # Convert max_size to bytes
    size_units = {'B': 1, 'KB': 1024, 'MB': 1024*1024, 'GB': 1024*1024*1024}
    if isinstance(max_size, str):
        unit = max_size[-2:].upper()
        if unit in size_units:
            max_bytes = int(max_size[:-2]) * size_units[unit]
        else:
            max_bytes = int(max_size)
    else:
        max_bytes = int(max_size)
    
    # Create log directory
    log_path = Path(log_file).parent
    log_path.mkdir(parents=True, exist_ok=True)
    
    # Create formatter
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # Setup file handler
    file_handler = logging.handlers.RotatingFileHandler(
        log_file,
        maxBytes=max_bytes,
        backupCount=backup_count
    )
    file_handler.setFormatter(formatter)
    file_handler.setLevel(log_level)
    
    # Setup console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setFormatter(formatter)
    console_handler.setLevel(log_level)
    
    # Setup root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(log_level)
    root_logger.addHandler(file_handler)
    root_logger.addHandler(console_handler)
    
    # Create application logger
    logger = logging.getLogger('PocketPad')
    logger.info("Logging system initialized")
    
    return logger
