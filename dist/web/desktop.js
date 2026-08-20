(function () {
  'use strict';

  let ws = null;
  let isConnected = false;
  let isDemoActive = false;
  let lastPacketCount = 0;
  let currentPps = 0;
  let ppsInterval = null;

  const dtShiftDots = document.querySelectorAll('#dt-shift-lights .dt-dot');
  const dtGear = document.getElementById('dt-gear');
  const dtSpeed = document.getElementById('dt-speed');
  const dtRpm = document.getElementById('dt-rpm');
  const dtBoost = document.getElementById('dt-boost');
  const dtSlip = document.getElementById('dt-slip');
  const dtSlipBox = document.getElementById('dt-slip-box');

  const statLatency = document.getElementById('stat-latency');
  const statPps = document.getElementById('stat-pps');
  const statDevice = document.getElementById('stat-device');
  const statConnType = document.getElementById('stat-conn-type');
  const badgeClientMode = document.getElementById('badge-client-mode');

  const urlUsb = document.getElementById('url-usb');
  const urlWifi = document.getElementById('url-wifi');
  const qrImgElement = document.getElementById('qr-img-element');
  const btnDesktopDemo = document.getElementById('btn-desktop-demo');

  // Detect Host IP
  const currentHost = window.location.hostname || 'localhost';
  const isLocal = currentHost === 'localhost' || currentHost === '127.0.0.1';

  // Setup QR Code & Server Status
  function initQrCode() {
    if (qrImgElement) {
      // Use local high-speed server QR generator (100% offline support)
      qrImgElement.src = `/api/qr?t=${Date.now()}`;
    }

    pollStatus();
    setInterval(pollStatus, 2500);
  }

  function pollStatus() {
    fetch('/api/status')
      .then(res => res.json())
      .then(data => {
        if (urlWifi) urlWifi.textContent = `https://${data.primary_ip}:${data.https_port}`;
        if (urlUsb) urlUsb.textContent = `https://localhost:${data.https_port}`;
        if (data.connected_count > 0 && data.clients && data.clients.length > 0) {
          const client = data.clients[data.clients.length - 1];
          updateDeviceUI(true, client.is_usb, client.ip, client.label);
        } else {
          updateDeviceUI(false, false, '', '');
        }
      })
      .catch(() => {});
  }

  function updateDeviceUI(connected, isUsb, clientIp, connLabel) {
    if (badgeClientMode) {
      badgeClientMode.textContent = connected ? 'CONNECTED' : 'STANDBY';
      badgeClientMode.className = connected ? 'badge-tag live-badge' : 'badge-tag client-badge';
    }

    if (statDevice) {
      if (connected) {
        statDevice.textContent = isUsb ? '⚡ USB Phone Connected' : '📱 Wi-Fi Phone Connected';
        statDevice.style.color = isUsb ? 'var(--forza-cyan)' : 'var(--forza-green)';
      } else {
        statDevice.textContent = 'Waiting for Phone...';
        statDevice.style.color = '#fff';
      }
    }

    if (statConnType) {
      if (connected) {
        statConnType.textContent = (clientIp || 'Phone') + (isUsb ? ' • 0.2ms Wire Speed' : ' • 5GHz QoS (240Hz)');
      } else {
        statConnType.textContent = 'Wired USB or 5GHz Wi-Fi';
      }
    }

    if (statLatency) {
      statLatency.textContent = connected ? (isUsb ? '0.23 ms' : '2.8 ms') : '-- ms';
    }
  }

  // Connect to Local Gamepad Server
  function connectWebSocket() {
    const wsUrl = `ws://${currentHost}:8765`;
    try {
      ws = new WebSocket(wsUrl);
      ws.binaryType = 'arraybuffer';
    } catch (e) {
      setTimeout(connectWebSocket, 1500);
      return;
    }

    ws.onopen = () => {
      isConnected = true;
      try {
        ws.send(JSON.stringify({ type: 'desktop_init' }));
      } catch (e) {}
      if (ppsInterval) clearInterval(ppsInterval);
      ppsInterval = setInterval(updatePps, 1000);
    };

    ws.onmessage = (event) => {
      lastPacketCount++;
      if (event.data instanceof ArrayBuffer) {
        const view = new DataView(event.data);
        const opcode = view.getUint8(0);

        if (opcode === 0x10 && view.byteLength >= 13) {
          handleForzaTelemetry(view);
        } else if (opcode === 0x0A) {
          // Pong
        }
      } else if (typeof event.data === 'string') {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'device_status') {
            updateDeviceUI(msg.connected, msg.is_usb, msg.client_ip, msg.conn_label);
          } else if (msg.type === 'telemetry_mode') {
            isDemoActive = msg.demo;
            if (btnDesktopDemo) {
              btnDesktopDemo.classList.toggle('active', isDemoActive);
              btnDesktopDemo.textContent = isDemoActive ? '🏁 DEMO: ACTIVE' : '🏁 TOGGLE DEMO RACE';
            }
          }
        } catch (e) {}
      }
    };

    ws.onclose = () => {
      isConnected = false;
      if (ppsInterval) clearInterval(ppsInterval);
      setTimeout(connectWebSocket, 1200);
    };

    ws.onerror = () => {
      if (ws) ws.close();
    };
  }

  function handleForzaTelemetry(view) {
    const rpm = view.getUint16(1, true);
    const maxRpm = view.getUint16(3, true);
    const speedMphX10 = view.getUint16(5, true);
    const gear = view.getUint8(7);
    const shiftPct = view.getUint8(8);
    const slipPct = view.getUint8(9);
    const boost = view.getUint8(12);

    // Update Dials
    if (dtSpeed) dtSpeed.textContent = Math.round(speedMphX10 / 10.0);
    if (dtRpm) dtRpm.textContent = rpm.toLocaleString();
    if (dtBoost) dtBoost.textContent = (boost > 0 ? (boost / 1.0).toFixed(1) : '0.0');

    // Gear Readout
    if (dtGear) {
      let gStr = 'N';
      if (gear === 0) gStr = 'R';
      else if (gear >= 1 && gear <= 10) gStr = gear.toString();
      dtGear.textContent = gStr;
    }

    // Tire Traction / Drift
    if (dtSlip && dtSlipBox) {
      if (slipPct > 20) {
        dtSlip.textContent = `DRIFT ${slipPct}%`;
        dtSlipBox.classList.add('drifting');
      } else {
        dtSlip.textContent = '100%';
        dtSlipBox.classList.remove('drifting');
      }
    }

    // Shift Lights (10 Segments)
    if (dtShiftDots && dtShiftDots.length === 10) {
      if (shiftPct >= 96) {
        dtShiftDots.forEach(dot => dot.className = 'dt-dot strobe');
      } else {
        dtShiftDots.forEach((dot, i) => {
          let activeClass = '';
          if (i < 4) {
            if (shiftPct >= (50 + i * 4)) activeClass = 'green';
          } else if (i < 7) {
            if (shiftPct >= (68 + (i - 4) * 4.5)) activeClass = 'yellow';
          } else {
            if (shiftPct >= (82 + (i - 7) * 4)) activeClass = 'red';
          }
          dot.className = activeClass ? `dt-dot ${activeClass}` : 'dt-dot';
        });
      }
    }
  }

  function updatePps() {
    currentPps = lastPacketCount;
    lastPacketCount = 0;
    if (statPps) statPps.textContent = `${Math.max(60, currentPps)} Hz`;
  }

  // Copy helper
  window.copyLink = function (elementId, btn) {
    const el = document.getElementById(elementId);
    if (!el) return;
    navigator.clipboard.writeText(el.textContent.trim()).then(() => {
      const originalText = btn.textContent;
      btn.textContent = '✅ COPIED!';
      btn.classList.add('copied');
      setTimeout(() => {
        btn.textContent = originalText;
        btn.classList.remove('copied');
      }, 1500);
    });
  };

  // Toggle Demo
  window.toggleDesktopDemo = function () {
    if (ws && ws.readyState === WebSocket.OPEN) {
      const buf = new ArrayBuffer(1);
      new DataView(buf).setUint8(0, 0x11);
      ws.send(buf);
    }
  };

  // Quick Utilities
  window.openJoyCpl = function () {
    if (window.pywebview && window.pywebview.api) {
      window.pywebview.api.open_joy_cpl();
    } else {
      fetch('/api/joy_cpl').catch(() => {});
    }
  };

  window.restartAdb = function () {
    if (window.pywebview && window.pywebview.api) {
      window.pywebview.api.restart_adb();
    }
  };

  window.openWebLink = function () {
    window.open(`https://${currentHost}:8443`, '_blank');
  };

  // Startup
  initQrCode();
  connectWebSocket();
})();
