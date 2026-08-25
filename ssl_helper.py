"""
PocketPad SSL & Certificate Authority (CA) Helper
Manages the persistent PocketPad Root CA and issues CA-signed server certificates.
"""

import ipaddress
import os
import socket
import ssl
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

from paths import BASE_DIR

CA_KEY_FILE = BASE_DIR / "pocketpad_ca_key.pem"
CA_CERT_FILE = BASE_DIR / "pocketpad_ca_cert.pem"

SERVER_KEY_FILE = BASE_DIR / "key.pem"
SERVER_CERT_FILE = BASE_DIR / "cert.pem"

# Compatibility aliases
CERT_FILE = SERVER_CERT_FILE
KEY_FILE = SERVER_KEY_FILE


def secure_file(path: Path):
    """Restrict file permissions to current user only."""
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass


def get_all_local_ips() -> list[str]:
    """Discover all local network and USB interface IP addresses."""
    ips = {"127.0.0.1"}
    try:
        host_ips = socket.gethostbyname_ex(socket.gethostname())[2]
        for ip in host_ips:
            if not ip.startswith("169.254"):  # ignore APIPA
                ips.add(ip)
    except Exception:
        pass
    return list(ips)


def generate_ca() -> None:
    """Generate persistent PocketPad Root Certificate Authority (CA)."""
    if CA_KEY_FILE.exists() and CA_CERT_FILE.exists():
        return

    key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=3072,
    )

    subject = issuer = x509.Name(
        [
            x509.NameAttribute(NameOID.COUNTRY_NAME, "IN"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "PocketPad"),
            x509.NameAttribute(NameOID.COMMON_NAME, "PocketPad Local CA"),
        ]
    )

    now = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=3650))
        .add_extension(
            x509.BasicConstraints(ca=True, path_length=1),
            critical=True,
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=True,
                key_encipherment=False,
                key_cert_sign=True,
                crl_sign=True,
                content_commitment=False,
                data_encipherment=False,
                key_agreement=False,
                encipher_only=False,
                decipher_only=False,
            ),
            critical=True,
        )
        .sign(key, hashes.SHA256())
    )

    CA_KEY_FILE.write_bytes(
        key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    secure_file(CA_KEY_FILE)

    CA_CERT_FILE.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    secure_file(CA_CERT_FILE)


def sync_ca_to_android() -> None:
    """Explicit developer command to sync CA certificate to Android raw resource."""
    generate_ca()
    target = BASE_DIR / "app" / "src" / "main" / "res" / "raw" / "pocketpad_ca.crt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(CA_CERT_FILE.read_bytes())
    print(f"CA copied to {target}")


def generate_server_certificate(local_ips: list[str] | None = None) -> None:
    """Generate server certificate signed by PocketPad Root CA."""
    generate_ca()

    if local_ips is None:
        local_ips = get_all_local_ips()

    ca_key = serialization.load_pem_private_key(
        CA_KEY_FILE.read_bytes(),
        password=None,
    )

    ca_cert = x509.load_pem_x509_certificate(CA_CERT_FILE.read_bytes())

    server_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048,
    )

    san_entries: list[x509.GeneralName] = [
        x509.DNSName("localhost"),
        x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
    ]

    for ip in local_ips:
        try:
            san_entries.append(x509.IPAddress(ipaddress.ip_address(ip)))
        except ValueError:
            continue

    subject = x509.Name(
        [
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "PocketPad"),
            x509.NameAttribute(NameOID.COMMON_NAME, "PocketPad Server"),
        ]
    )

    now = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(ca_cert.subject)
        .public_key(server_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(minutes=5))
        .not_valid_after(now + timedelta(days=825))
        .add_extension(
            x509.SubjectAlternativeName(san_entries),
            critical=False,
        )
        .add_extension(
            x509.BasicConstraints(ca=False, path_length=None),
            critical=True,
        )
        .add_extension(
            x509.ExtendedKeyUsage([x509.oid.ExtendedKeyUsageOID.SERVER_AUTH]),
            critical=True,
        )
        .sign(ca_key, hashes.SHA256())
    )

    SERVER_KEY_FILE.write_bytes(
        server_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
    )
    secure_file(SERVER_KEY_FILE)

    SERVER_CERT_FILE.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    secure_file(SERVER_CERT_FILE)


def certificate_matches_ips(
    certificate: x509.Certificate,
    local_ips: list[str],
) -> bool:
    """Check if the certificate SANs contain all required IPs and localhost."""
    required = {
        "localhost",
        "127.0.0.1",
        *local_ips,
    }

    try:
        san = certificate.extensions.get_extension_for_class(x509.SubjectAlternativeName).value
    except x509.ExtensionNotFound:
        return False

    present: set[str] = set()

    for entry in san:
        if isinstance(entry, x509.DNSName):
            present.add(entry.value)
        elif isinstance(entry, x509.IPAddress):
            present.add(str(entry.value))

    return required.issubset(present)


def ensure_server_certificate(
    local_ips: list[str],
) -> None:
    """Ensure valid server certificate exists and matches current IPs."""
    generate_ca()

    if not SERVER_CERT_FILE.exists() or not SERVER_KEY_FILE.exists():
        generate_server_certificate(local_ips)
        return

    try:
        certificate = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())
        if not certificate_matches_ips(certificate, local_ips):
            generate_server_certificate(local_ips)
    except Exception:
        generate_server_certificate(local_ips)


def get_ssl_context(local_ip: str = "127.0.0.1") -> ssl.SSLContext:
    """Get or generate a stable TLS 1.2+ SSL context signed by PocketPad CA."""
    ensure_server_certificate(get_all_local_ips())

    ssl_context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ssl_context.minimum_version = ssl.TLSVersion.TLSv1_2
    ssl_context.load_cert_chain(
        certfile=str(SERVER_CERT_FILE),
        keyfile=str(SERVER_KEY_FILE),
    )
    return ssl_context


if __name__ == "__main__":
    generate_ca()
    generate_server_certificate()
    sync_ca_to_android()
    print("[+] PocketPad CA and Server Certificate successfully generated and verified.")
