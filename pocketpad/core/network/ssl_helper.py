import logging
import os
import socket
import ssl
from pathlib import Path
from typing import List, Optional


class SSLHelper:
    """Helper for SSL certificate resolution and IP querying."""
    
    @staticmethod
    def get_all_local_ips() -> List[str]:
        """Discover all local IP addresses for binding."""
        ips = []
        try:
            hostname = socket.gethostname()
            for ip in socket.gethostbyname_ex(hostname)[2]:
                if not ip.startswith("127."):
                    ips.append(ip)
        except Exception:
            pass
        
        if not ips:
            ips.append("127.0.0.1")
        return ips
    
    @staticmethod
    def get_ssl_context(cert_file: str = "cert.pem", key_file: str = "key.pem") -> Optional[ssl.SSLContext]:
        """Create or return SSL context for secure HTTPS/WSS."""
        if not (os.path.exists(cert_file) and os.path.exists(key_file)):
            return None
        
        try:
            ctx = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
            ctx.load_cert_chain(certfile=cert_file, keyfile=key_file)
            return ctx
        except Exception as e:
            logging.getLogger(__name__).error("Failed to load SSL certificates: %s", e)
            return None
