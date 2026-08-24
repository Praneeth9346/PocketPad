import asyncio

import websockets


async def attack_server():
    uri = "ws://127.0.0.1:8765"
    
    print("Testing connection...")
    try:
        async with websockets.connect(uri) as ws:
            print("Connected.")
            
            # 1. Invalid JSON
            print("Sending invalid JSON...")
            await ws.send("INVALID_JSON_HERE")
            await asyncio.sleep(0.5)
            
            # 2. Random binary garbage
            print("Sending random binary garbage...")
            await ws.send(b'\xDE\xAD\xBE\xEF\x00\x11' * 10)
            await asyncio.sleep(0.5)
            
            # 3. Payload exceeding max_size (2048)
            print("Sending oversized payload...")
            try:
                await ws.send("A" * 5000)
                await asyncio.sleep(0.5)
            except websockets.exceptions.ConnectionClosed:
                print("Server correctly dropped connection on oversized payload.")
            
    except Exception as e:
        print(f"Connection failed: {e}")

if __name__ == "__main__":
    asyncio.run(attack_server())
