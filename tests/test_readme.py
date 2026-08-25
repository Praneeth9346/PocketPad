from pathlib import Path

README = Path(__file__).resolve().parents[1] / "README.md"


def test_removed_telemetry_is_not_advertised():
    text = README.read_text(encoding="utf-8").lower()

    forbidden_phrases = [
        "telemetry streaming",
        "how to enable telemetry",
        "live telemetry",
        "telemetry server",
        "telemetry broadcast",
    ]

    for phrase in forbidden_phrases:
        assert phrase not in text
