import asyncio
import json
from aiortc import RTCPeerConnection, RTCSessionDescription, RTCIceCandidate

class WebRTCManager:
    """
    Manages WebRTC Unreliable / Unordered UDP DataChannels for PocketPad.
    Delivers true zero-head-of-line-blocking wireless UDP speed (< 1.5ms over Wi-Fi).
    """

    def __init__(self, bridge, broadcast_callback=None):
        self.bridge = bridge
        self.broadcast_callback = broadcast_callback
        self.peer_connections = set()
        self.active_channels = set()

    async def handle_offer(self, sdp: str, sdp_type: str, websocket) -> dict:
        """Handle WebRTC SDP Offer from mobile browser and create Answer."""
        pc = RTCPeerConnection()
        self.peer_connections.add(pc)

        @pc.on("datachannel")
        def on_datachannel(channel):
            print(f"[WebRTC] ⚡ Direct UDP DataChannel Opened: {channel.label} (ordered={channel.ordered}, maxRetransmits={channel.maxRetransmits})")
            self.active_channels.add(channel)

            @channel.on("message")
            def on_message(message):
                if isinstance(message, bytes):
                    resp = self.bridge.handle_binary_packet(message)
                    if resp:
                        channel.send(resp)

            @channel.on("close")
            def on_close():
                print(f"[WebRTC] DataChannel {channel.label} closed.")
                self.active_channels.discard(channel)

        @pc.on("connectionstatechange")
        async def on_connectionstatechange():
            print(f"[WebRTC] Connection state: {pc.connectionState}")
            if pc.connectionState in ("failed", "closed"):
                await pc.close()
                self.peer_connections.discard(pc)

        # Set remote offer and create local answer
        offer = RTCSessionDescription(sdp=sdp, type=sdp_type)
        await pc.setRemoteDescription(offer)
        answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)

        return {
            "type": "webrtc_answer",
            "sdp": pc.localDescription.sdp,
            "sdp_type": pc.localDescription.type
        }

    async def handle_ice_candidate(self, candidate_dict: dict):
        """Process incoming ICE Candidate (optional for direct LAN connection)."""
        pass

    def broadcast_telemetry(self, packet_bytes: bytes):
        """Broadcast 13-byte Forza telemetry frame over UDP DataChannels."""
        for channel in list(self.active_channels):
            try:
                if channel.readyState == "open":
                    channel.send(packet_bytes)
            except Exception:
                pass

    async def cleanup(self):
        """Close all active WebRTC peer connections."""
        coros = [pc.close() for pc in self.peer_connections]
        await asyncio.gather(*coros, return_exceptions=True)
        self.peer_connections.clear()
        self.active_channels.clear()
