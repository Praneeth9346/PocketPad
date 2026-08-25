from server import _token_matches, EXPECTED_TOKEN


def test_valid_token():
    assert _token_matches(EXPECTED_TOKEN)


def test_invalid_token():
    assert not _token_matches("wrong-token")


def test_empty_token():
    assert not _token_matches("")


def test_non_string_token():
    assert not _token_matches(None)
    assert not _token_matches(12345)
    assert not _token_matches({})
