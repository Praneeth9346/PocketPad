from pathlib import Path

from cryptography import x509

from ssl_helper import (
    CA_CERT_FILE,
    CA_KEY_FILE,
    SERVER_CERT_FILE,
    SERVER_KEY_FILE,
    generate_ca,
    generate_server_certificate,
)


def setup_module():
    generate_ca()
    generate_server_certificate(["127.0.0.1", "192.168.1.100"])


def test_ca_exists():
    assert CA_CERT_FILE.exists()
    assert CA_KEY_FILE.exists()


def test_server_certificate_exists():
    assert SERVER_CERT_FILE.exists()


def test_server_key_exists():
    assert SERVER_KEY_FILE.exists()


def test_certificate_files_not_empty():
    for path in (
        CA_CERT_FILE,
        CA_KEY_FILE,
        SERVER_CERT_FILE,
        SERVER_KEY_FILE,
    ):
        assert Path(path).stat().st_size > 0


def test_server_certificate_issued_by_pocketpad_ca():
    ca_cert = x509.load_pem_x509_certificate(CA_CERT_FILE.read_bytes())
    server_cert = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())

    assert server_cert.issuer == ca_cert.subject


def test_server_certificate_san_contains_localhost_and_ips():
    server_cert = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())
    san = server_cert.extensions.get_extension_for_oid(x509.oid.ExtensionOID.SUBJECT_ALTERNATIVE_NAME).value

    dns_names = [name.value for name in san if isinstance(name, x509.DNSName)]
    ip_addresses = [str(name.value) for name in san if isinstance(name, x509.IPAddress)]

    assert "localhost" in dns_names
    assert "127.0.0.1" in ip_addresses


def test_server_certificate_is_not_ca():
    server_cert = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())
    basic_constraints = server_cert.extensions.get_extension_for_class(x509.BasicConstraints).value
    assert basic_constraints.ca is False


def test_certificate_matches_ips_validation():
    from ssl_helper import certificate_matches_ips, ensure_server_certificate

    server_cert = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())
    assert certificate_matches_ips(server_cert, ["127.0.0.1", "192.168.1.100"])
    # Not matching when a new IP is required
    assert not certificate_matches_ips(server_cert, ["10.200.1.50"])

    # Test ensure_server_certificate regenerates when IPs change
    ensure_server_certificate(["127.0.0.1", "10.200.1.50"])
    new_cert = x509.load_pem_x509_certificate(SERVER_CERT_FILE.read_bytes())
    assert certificate_matches_ips(new_cert, ["10.200.1.50"])
