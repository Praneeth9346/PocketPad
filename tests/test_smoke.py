import asyncio
import pytest
from smoke_test import SmokeTestRunner


def test_tls_and_token_smoke():
    runner = SmokeTestRunner()
    runner.test_tls_persistence()
    runner.test_token_lifecycle()
    runner.test_qr_url_parsing()

    for item, (passed, detail) in runner.results.items():
        assert passed, f"Smoke test failed on '{item}': {detail}"


@pytest.mark.asyncio
async def test_websocket_protocol_smoke():
    runner = SmokeTestRunner()
    await runner.test_websocket_handshake_and_protocol()

    for item, (passed, detail) in runner.results.items():
        assert passed, f"Smoke test failed on '{item}': {detail}"
