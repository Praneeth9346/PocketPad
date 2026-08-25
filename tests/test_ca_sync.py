from cryptography import x509
from cryptography.hazmat.primitives import hashes

from ssl_helper import (
    CA_CERT_FILE,
    BASE_DIR,
)


ANDROID_CA_FILE = (
    BASE_DIR
    / "app"
    / "src"
    / "main"
    / "res"
    / "raw"
    / "pocketpad_ca.crt"
)


def test_android_ca_matches_server_ca():
    assert CA_CERT_FILE.exists()
    assert ANDROID_CA_FILE.exists()

    server_ca = x509.load_pem_x509_certificate(
        CA_CERT_FILE.read_bytes()
    )

    android_ca = x509.load_pem_x509_certificate(
        ANDROID_CA_FILE.read_bytes()
    )

    assert (
        server_ca.fingerprint(hashes.SHA256())
        == android_ca.fingerprint(hashes.SHA256())
    )
