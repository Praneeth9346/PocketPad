import asyncio
import json
import struct
import sys
import time

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import websockets
from aiortc import RTCPeerConnection, RTCSessionDescription

WS_URI = "ws://127.0.0.1:8765"

async def test_webrtc_channel():
    print("[WebRTCTest] 1. Connecting to WebSocket Signaling Server...")
    async with websockets.connect(WS_URI) as ws:
        print("[WebRTCTest] 2. WebSocket Connected! Creating WebRTC PeerConnection...")
        pc = RTCPeerConnection()

        # Create Unordered, Unreliable DataChannel (Raw UDP 0ms latency)
        channel = pc.createDataChannel("pocketpad_udp", ordered=False, maxRetransmits=0)
        channel_open_future = asyncio.get_running_loop().create_future()
        pong_received_future = asyncio.get_running_loop().create_future()

        @channel.on("open")
        def on_open():
            print("[WebRTCTest] ⚡ 3. WebRTC UDP DataChannel OPENED!")
            if not channel_open_future.done():
                channel_open_future.set_result(True)

        @channel.on("message")
        def on_message(message):
            if isinstance(message, bytes) and len(message) > 0 and message[0] == 0x0A:
                ts = struct.unpack("<BI", message)[1]
                if not pong_received_future.done():
                    pong_received_future.set_result(ts)

        # Create SDP Offer
        offer = await pc.createOffer()
        await pc.setLocalDescription(offer)

        # Send SDP Offer over WebSocket
        await ws.send(json.dumps({
            "type": "webrtc_offer",
            "sdp": pc.localDescription.sdp,
            "sdp_type": pc.localDescription.type
        }))
        print("[WebRTCTest] 4. Sent SDP Offer. Waiting for SDP Answer from server...")

        # Receive SDP Answer from WebSocket
        answer_raw = await asyncio.wait_for(ws.recv(), timeout=4.0)
        answer_data = json.loads(answer_raw)
        print(f"[WebRTCTest] 5. Received SDP Answer: {answer_data.get('type')}")

        answer = RTCSessionDescription(sdp=answer_data["sdp"], type=answer_data["sdp_type"])
        await pc.setRemoteDescription(answer)

        # Wait for DataChannel to open
        print("[WebRTCTest] 6. Waiting for DataChannel open event...")
        await asyncio.wait_for(channel_open_future, timeout=5.0)

        # Test sending high-speed steering packet over WebRTC UDP
        print("[WebRTCTest] 7. Streaming 500 steering & ping packets over WebRTC UDP...")
        t_start = time.perf_counter()
        
        for i in range(500):
            # Send steering packet [0x01, int16_x]
            norm_val = int(16000 * ((i % 20) / 20.0))
            steer_pkt = struct.pack("<Bh", 0x01, norm_val)
            channel.send(steer_pkt)

        # Send Ping packet [0x09, timestamp]
        ping_ts = int(time.time() * 1000) & 0xFFFFFFFF
        channel.send(struct.pack("<BI", 0x09, ping_ts))

        # Wait for Pong echo over WebRTC DataChannel
        echoed_ts = await asyncio.wait_for(pong_received_future, timeout=3.0)
        t_end = time.perf_counter()
        elapsed_ms = (t_end - t_start) * 1000

        print(f"[WebRTCTest] ✅ SUCCESS! 500 WebRTC UDP Packets streamed in {elapsed_ms:.2f} ms")
        print(f"  -> Round-Trip Ping verified: {echoed_ts == ping_ts}")
        print(f"  -> DataChannel Throughput: {500 / (elapsed_ms/1000):.0f} packets/sec")

        await pc.close()

if __name__ == "__main__":
    asyncio.run(test_webrtc_channel())
