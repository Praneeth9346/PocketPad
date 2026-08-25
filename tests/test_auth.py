import pytest

from server import EXPECTED_TOKEN, _token_matches


def test_valid_token():
    assert _token_matches(EXPECTED_TOKEN)


@pytest.mark.parametrize(
    "token",
    [
        "",
        "wrong",
        "wrong-token",
        None,
    ],
)
def test_invalid_token(token):
    assert not _token_matches(token)
