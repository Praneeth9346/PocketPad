import asyncio
import json
import logging
import os
import ssl
from pathlib import Path
from typing import Any, Dict, Optional, Set

from aiohttp import WSMsgType, web


class PocketPadServer:
    """Web and WebSocket server for PocketPad communication."""
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.app = web.Application()
        self.runner: Optional[web.AppRunner] = None
        self.site: Optional[web.TCPSite] = None
        self.websocket_clients: Set[web.WebSocketResponse] = set()
        self.ssl_context: Optional[ssl.SSLContext] = None
        self.logger = logging.getLogger(__name__)
        
        # Setup SSL if enabled
        if self.config.get('ssl_enabled', False):
            self.ssl_context = self._create_ssl_context()
        
        # Setup routes
        self._setup_routes()
    
    def _setup_routes(self):
        """Setup application routes."""
        self.app.add_routes([
            web.get('/', self.handle_index),
            web.get('/ws', self.handle_websocket),
            web.post('/controller', self.handle_controller_input),
            web.get('/status', self.handle_status),
        ])
    
    def _create_ssl_context(self) -> Optional[ssl.SSLContext]:
        """Create SSL context for secure communication."""
        cert_file = self.config.get('cert_file', 'cert.pem')
        key_file = self.config.get('key_file', 'key.pem')
        
        if not (os.path.exists(cert_file) and os.path.exists(key_file)):
            self.logger.warning("SSL certificate or key file not found, running without SSL")
            return None
        
        try:
            ssl_context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
            ssl_context.load_cert_chain(cert_file, key_file)
            self.logger.info("SSL context created successfully")
            return ssl_context
        except Exception as e:
            self.logger.error("Failed to create SSL context: %s", e)
            return None
    
    async def start(self):
        """Start the web server."""
        self.runner = web.AppRunner(self.app)
        await self.runner.setup()
        
        host = self.config.get('host', '0.0.0.0')
        port = int(self.config.get('port', 8443 if self.ssl_context else 8000))
        
        self.site = web.TCPSite(
            self.runner,
            host,
            port,
            ssl_context=self.ssl_context
        )
        
        await self.site.start()
        self.logger.info("Server started on %s:%s", host, port)
    
    async def stop(self):
        """Stop the web server."""
        for ws in list(self.websocket_clients):
            try:
                await ws.close(code=1000, message=b"Server shutting down")
            except Exception:
                pass
        self.websocket_clients.clear()
        
        if self.runner:
            await self.runner.cleanup()
            self.logger.info("Server stopped")
    
    async def handle_index(self, request: web.Request) -> web.Response:
        """Serve the main page or status."""
        index_path = Path("web/index.html")
        if index_path.exists():
            return web.FileResponse(index_path)
        return web.Response(text="PocketPad Server Running", content_type="text/plain")
    
    async def handle_websocket(self, request: web.Request) -> web.WebSocketResponse:
        """Handle WebSocket connections."""
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        self.websocket_clients.add(ws)
        
        self.logger.info("WebSocket client connected. Total clients: %d", len(self.websocket_clients))
        
        try:
            async for msg in ws:
                if msg.type == WSMsgType.TEXT:
                    try:
                        data = json.loads(msg.data)
                        await self._handle_message(data, ws)
                    except json.JSONDecodeError:
                        self.logger.warning("Invalid JSON received from client")
                elif msg.type == WSMsgType.ERROR:
                    self.logger.error("WebSocket error: %s", ws.exception())
        finally:
            self.websocket_clients.discard(ws)
            self.logger.info("WebSocket client disconnected. Total clients: %d", len(self.websocket_clients))
        
        return ws
    
    async def _handle_message(self, data: dict, websocket: web.WebSocketResponse):
        """Handle incoming WebSocket messages."""
        msg_type = data.get('type')
        
        if msg_type == 'controller_input':
            await self._process_controller_input(data, websocket)
        elif msg_type == 'calibration':
            await self._handle_calibration(data, websocket)
        elif msg_type == 'ping':
            await websocket.send_json({'type': 'pong', 'timestamp': data.get('timestamp')})
        else:
            self.logger.debug("Received message type: %s", msg_type)
    
    async def _process_controller_input(self, data: dict, websocket: web.WebSocketResponse):
        """Process controller input data."""
        try:
            await websocket.send_json({
                'type': 'ack',
                'status': 'processed'
            })
        except Exception as e:
            self.logger.error("Controller input processing error: %s", e)
            await websocket.send_json({
                'type': 'error',
                'message': str(e)
            })
    
    async def _handle_calibration(self, data: dict, websocket: web.WebSocketResponse):
        """Handle calibration request."""
        try:
            await websocket.send_json({
                'type': 'calibration_complete',
                'status': 'success'
            })
        except Exception as e:
            self.logger.error("Calibration error: %s", e)
    
    async def handle_controller_input(self, request: web.Request) -> web.Response:
        """Handle controller input from mobile device via POST."""
        try:
            data = await request.json()
            
            # Validate input
            if not self._validate_input(data):
                return web.json_response(
                    {'error': 'Invalid input data'},
                    status=400
                )
            
            # Process input
            await self._process_input_data(data)
            
            return web.json_response({
                'status': 'success',
                'timestamp': data.get('timestamp')
            })
            
        except json.JSONDecodeError:
            return web.json_response({'error': 'Invalid JSON'}, status=400)
        except Exception as e:
            self.logger.error("Controller input error: %s", e)
            return web.json_response({'error': str(e)}, status=500)
    
    def _validate_input(self, data: dict) -> bool:
        """Validate input data structure."""
        required_fields = ['timestamp', 'type']
        return all(field in data for field in required_fields)
    
    async def _process_input_data(self, data: dict):
        """Process validated input data."""
        pass
    
    async def handle_status(self, request: web.Request) -> web.Response:
        """Handle status request."""
        return web.json_response({
            'status': 'running',
            'clients': len(self.websocket_clients),
            'version': '1.1.0'
        })
    
    async def broadcast(self, message: dict):
        """Broadcast message to all WebSocket clients."""
        if not self.websocket_clients:
            return
        
        clients = list(self.websocket_clients)
        for ws in clients:
            try:
                await ws.send_json(message)
            except Exception as e:
                self.logger.error("Failed to send to client: %s", e)
                self.websocket_clients.discard(ws)
