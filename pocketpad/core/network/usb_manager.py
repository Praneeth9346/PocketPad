import logging
import subprocess
from typing import Optional, Tuple


class USBManager:
    """USB ADB Reverse Port Forwarding Manager."""
    
    def __init__(self, adb_path: str = "adb", http_port: int = 8000, https_port: int = 8443, ws_port: int = 8765):
        self.adb_path = adb_path
        self.http_port = http_port
        self.https_port = https_port
        self.ws_port = ws_port
        self.logger = logging.getLogger(__name__)
    
    def setup_reverse_forwarding(self) -> Tuple[bool, str]:
        """Configure ADB reverse port forwarding for ultra-low latency USB."""
        try:
            # Check devices
            res = subprocess.run([self.adb_path, "devices"], capture_output=True, text=True, timeout=3)
            lines = [line for line in res.stdout.strip().split("\n")[1:] if line.strip() and not line.startswith("*")]
            
            if not lines:
                return False, "No USB device detected in ADB mode"
            
            # Setup reverse ports
            for port in (self.http_port, self.https_port, self.ws_port):
                subprocess.run(
                    [self.adb_path, "reverse", f"tcp:{port}", f"tcp:{port}"],
                    capture_output=True,
                    check=False,
                    timeout=3
                )
            
            return True, f"ADB USB Forwarding active ({len(lines)} device connected)"
        except Exception as e:
            self.logger.warning("ADB USB setup error: %s", e)
            return False, f"ADB error: {e}"
