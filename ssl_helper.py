import datetime
import ipaddress
import os
import socket
import ssl
from pathlib import Path
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
import sys

def get_base_dir() -> Path:
    if getattr(sys, 'frozen', False):
        return Path(sys.executable).parent
    return Path(__file__).parent

BASE_DIR = get_base_dir()
CERT_FILE = BASE_DIR / "cert.pem"
KEY_FILE = BASE_DIR / "key.pem"

def get_all_local_ips():
    """Discover all local network and USB interface IP addresses."""
    ips = set(["127.0.0.1"])
    try:
        host_ips = socket.gethostbyname_ex(socket.gethostname())[2]
        for ip in host_ips:
            if not ip.startswith("169.254"): # ignore APIPA
                ips.add(ip)
    except Exception:
        pass
    return list(ips)

def generate_self_signed_cert(local_ip: str = "127.0.0.1"):
    """Generate self-signed certificate supporting all local and USB network IPs."""
    all_ips = get_all_local_ips()
    if local_ip not in all_ips and not local_ip.startswith("169.254"):
        all_ips.append(local_ip)

    print("[SSL] Generating multi-interface SSL certificate (Wi-Fi & USB)...")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)

    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "PocketPad Local Server"),
        x509.NameAttribute(NameOID.COMMON_NAME, "PocketPad"),
    ])

    san_list = [x509.DNSName("localhost")]
    for ip_str in all_ips:
        try:
            san_list.append(x509.IPAddress(ipaddress.IPv4Address(ip_str)))
        except Exception:
            san_list.append(x509.DNSName(ip_str))

    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=1))
        .not_valid_after(datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(days=365))
        .add_extension(x509.SubjectAlternativeName(san_list), critical=False)
        .sign(key, hashes.SHA256())
    )

    with open(KEY_FILE, "wb") as f:
        f.write(key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption()
        ))

    with open(CERT_FILE, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))

    print(f"[SSL] Certificate created covering: {all_ips}")
    return str(CERT_FILE), str(KEY_FILE)

def get_ssl_context(local_ip: str = "127.0.0.1"):
    cert_path, key_path = generate_self_signed_cert(local_ip)
    ssl_ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_ctx.load_cert_chain(certfile=cert_path, keyfile=key_path)
    return ssl_ctx
