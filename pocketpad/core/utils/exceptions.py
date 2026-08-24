class PocketPadError(Exception):
    """Base exception for PocketPad."""
    pass


class ConnectionError(PocketPadError):
    """Connection-related error."""
    pass


class InputError(PocketPadError):
    """Input processing error."""
    pass


class ConfigurationError(PocketPadError):
    """Configuration error."""
    pass


class USBError(PocketPadError):
    """USB communication error."""
    pass


class WebRTCError(PocketPadError):
    """WebRTC communication error."""
    pass


class TelemetryError(PocketPadError):
    """Telemetry processing error."""
    pass
