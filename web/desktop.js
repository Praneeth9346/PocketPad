(function () {
  'use strict';

  let ws = null;
  let isConnected = false;
  let lastPacketCount = 0;
  let currentPps = 0;
  let ppsInterval = null;
  let pingInterval = null;
  let hasClient = false;

  let latencyBuffer = [];

  // DOM Bindings
  const statLatency = document.getElementById('stat-latency');
  const statPps = document.getElementById('stat-pps');
  const statDevice = document.getElementById('stat-device');
  const statClientIp = document.getElementById('stat-client-ip');
  const statConnType = document.getElementById('stat-conn-type');
  const barLatency = document.getElementById('bar-latency');
  const barClient = document.getElementById('bar-client');
  const eqBars = document.querySelectorAll('#eq-bars .eq-bar');

  const urlUsb = document.getElementById('url-usb');
  const urlWifi = document.getElementById('url-wifi');
  const qrImgElement = document.getElementById('qr-img-element');

  const qrContainer = document.getElementById('qr-container');
  const activeSessionContainer = document.getElementById('active-session-container');
  const activeClientName = document.getElementById('active-client-name');
  const activeClientIp = document.getElementById('active-client-ip');

  const currentHost = window.location.hostname || 'localhost';

  const urlParams = new URLSearchParams(window.location.search);
  const desktopSession = urlParams.get('session') || '';
  let authToken = '';
  let sessionBootstrapComplete = false;

  async function bootstrapDesktopSession() {
    if (!desktopSession) {
      throw new Error('Missing desktop session.');
    }

    const response = await fetch(
      `/desktop-session?session=${encodeURIComponent(desktopSession)}`
    );

    if (!response.ok) {
      throw new Error(`Desktop authentication failed (${response.status}).`);
    }

    const data = await response.json();

    if (!data.ok || !data.authenticated || !data.token) {
      throw new Error('Desktop session exchange returned an invalid response.');
    }

    authToken = data.token;
    sessionBootstrapComplete = true;

    // Remove the one-time session from the visible URL.
    // The access token remains only in JavaScript memory.
    try {
      window.history.replaceState({}, document.title, window.location.pathname);
    } catch (_) {}
  }

  async function apiFetch(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (authToken) {
      headers.set('Authorization', `Bearer ${authToken}`);
    }
    return fetch(url, {
      ...options,
      headers
    });
  }

  async function loadQrCode() {
    if (!qrImgElement) {
      return;
    }

    const spinner = document.getElementById('qr-loading-spinner');
    try {
      const response = await apiFetch(`/api/qr?t=${Date.now()}`);
      if (!response.ok) {
        throw new Error(`QR request failed: ${response.status}`);
      }

      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);

      // Release previous QR object URL.
      const previousUrl = qrImgElement.dataset.objectUrl;
      if (previousUrl) {
        URL.revokeObjectURL(previousUrl);
      }

      qrImgElement.dataset.objectUrl = objectUrl;
      qrImgElement.src = objectUrl;
      qrImgElement.style.display = 'block';
      if (spinner) spinner.style.display = 'none';
    } catch (error) {
      addLog(`Failed to load pairing QR: ${error.message}`, "error");
      qrImgElement.style.display = 'none';
      if (spinner) {
        spinner.style.display = 'flex';
        spinner.textContent = 'Failed to generate QR';
      }
    }
  }

  function initQrCode() {
    loadQrCode();
    pollStatus();
    setInterval(pollStatus, 1500);
  }

  function addLog(msg, type = "info") {
    const logContainer = document.getElementById('event-log-container');
    if (!logContainer) return;

    const now = new Date();
    const timeStr = now.getHours().toString().padStart(2, '0') + ':' +
                    now.getMinutes().toString().padStart(2, '0') + ':' +
                    now.getSeconds().toString().padStart(2, '0');

    const entry = document.createElement('div');
    entry.className = 'log-entry';
    let color = '#A0AAB5';
    if (type === 'error') color = '#e74c3c';
    if (type === 'success') color = '#00C853';
    if (type === 'warn') color = '#FFC107';

    entry.innerHTML = `<span class="log-time">[${timeStr}]</span> <span style="color: ${color}">${msg}</span>`;
    logContainer.appendChild(entry);
    logContainer.scrollTop = logContainer.scrollHeight;
  }

  function pollStatus() {
    apiFetch('/api/status')
      .then(res => res.json())
      .then(data => {
        if (urlWifi && data.primary_ip) {
          urlWifi.textContent = `https://${data.primary_ip}:${data.https_port}`;
        }
        if (urlUsb && data.https_port) {
          urlUsb.textContent = `https://localhost:${data.https_port}`;
        }

        // ViGEmBus Check
        const vigemDot = document.getElementById('dot-vigem');
        const vigemText = document.getElementById('text-vigem');
        if (data.controller_available) {
            vigemDot.className = 'status-dot dot-green';
            vigemText.textContent = 'Xbox 360 Pad';
            vigemText.style.color = '';
        } else {
            vigemDot.className = 'status-dot dot-red';
            vigemText.textContent = 'ViGEm Error (Click to Repair)';
            vigemText.style.color = '#e74c3c';
        }

        if (data.connected_count > 0 && data.clients && data.clients.length > 0) {
          const client = data.clients[data.clients.length - 1];
          hasClient = true;
          updateDeviceUI(true, client.is_usb, client.ip, client.label || "PocketPad Client");
        } else {
          if (hasClient) {
              addLog("Client disconnected.", "warn");
          }
          hasClient = false;
          updateDeviceUI(false, false, '', '');
        }
      })
      .catch(() => {});
  }

  let forcedQr = false;
  window.showQrCode = function(e) {
      e.preventDefault();
      forcedQr = true;
      qrContainer.style.display = 'flex';
      activeSessionContainer.style.display = 'none';
  };

  window.disconnectClient = function() {
     addLog("Force disconnecting client...", "info");
  };

  function updateDeviceUI(connected, isUsb, clientIp, connLabel) {
    if (connected && !forcedQr) {
        qrContainer.style.display = 'none';
        activeSessionContainer.style.display = 'flex';
        activeClientName.textContent = connLabel;
        activeClientIp.textContent = clientIp + (isUsb ? " (USB)" : " (Wi-Fi)");
    } else if (!connected) {
        forcedQr = false;
        qrContainer.style.display = 'flex';
        activeSessionContainer.style.display = 'none';
    }

    if (statDevice) {
      if (connected) {
        statDevice.textContent = isUsb ? 'USB Wired' : '5GHz Wi-Fi';
        statDevice.className = 'client-status-title green-val';
      } else {
        statDevice.textContent = 'Waiting for Phone...';
        statDevice.className = 'client-status-title white-text';
      }
    }

    if (statClientIp) {
      if (connected) {
        statClientIp.textContent = clientIp ? `${clientIp} (${isUsb ? 'ADB Rev' : 'Wi-Fi'})` : '127.0.0.1 (Connected)';
      } else {
        statClientIp.textContent = 'Scan QR Code to Connect';
      }
    }

    if (statConnType) {
      if (connected) {
        statConnType.textContent = isUsb ? 'Phone Sync: Active & Stable' : 'Wireless Sync: Low Jitter';
      } else {
        statConnType.textContent = 'Wired USB or 5GHz Wi-Fi';
      }
    }

    if (barClient) {
      barClient.style.width = connected ? '95%' : '15%';
      barClient.style.background = connected ? '#00C853' : 'var(--text-muted)';
    }

    // Reset Latency and PPS if disconnected
    if (!connected) {
        if (statLatency) {
            statLatency.textContent = '-';
            statLatency.className = 'large-val';
        }
        if (statPps) statPps.textContent = '-';
        if (barLatency) barLatency.style.width = '0%';
        if (eqBars) eqBars.forEach(b => b.style.height = '0px');
        const sub = document.getElementById('stat-latency-sub');
        if (sub) sub.textContent = 'Min/Avg/Max over 60s';
        latencyBuffer = [];
        updateLiveMonitor(0, 0, 0);
    }
  }

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
        ws.send(JSON.stringify({ type: 'hello', token: authToken }));
        ws.send(JSON.stringify({ type: 'desktop_init' }));
      } catch (e) {}
      if (ppsInterval) clearInterval(ppsInterval);
      ppsInterval = setInterval(updatePps, 1000);

      if (pingInterval) clearInterval(pingInterval);
      pingInterval = setInterval(sendPing, 1000);

      addLog("Connected to local engine WebSocket.", "success");
    };

    ws.onmessage = (event) => {
      lastPacketCount++;
      if (typeof event.data === 'string') {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'device_status') {
             if (msg.connected && !hasClient) {
                 addLog(`Client connected from ${msg.client_ip}`, "success");
             }
          } else if (msg.type === 'pong') {
             const rtt = Math.max(0.1, performance.now() - msg.t);
             latencyBuffer.push(rtt);
             if (latencyBuffer.length > 60) latencyBuffer.shift();

             let minLat = Math.min(...latencyBuffer);
             let maxLat = Math.max(...latencyBuffer);
             let avgLat = latencyBuffer.reduce((a,b) => a+b, 0) / latencyBuffer.length;

             if (statLatency) {
                 statLatency.textContent = rtt.toFixed(2);
                 if (rtt < 5.0) {
                     statLatency.className = 'large-val green-text';
                     barLatency.style.background = '#00C853';
                     barLatency.style.width = '90%';
                 } else if (rtt < 20.0) {
                     statLatency.className = 'large-val orange-text';
                     barLatency.style.background = '#FFC107';
                     barLatency.style.width = '50%';
                 } else {
                     statLatency.className = 'large-val red-text';
                     barLatency.style.background = '#e74c3c';
                     barLatency.style.width = '20%';
                 }
             }
             const sub = document.getElementById('stat-latency-sub');
             if (sub) sub.textContent = `${minLat.toFixed(1)} / ${avgLat.toFixed(1)} / ${maxLat.toFixed(1)} ms`;
          }
        } catch (e) {}
      } else if (event.data instanceof ArrayBuffer) {
         parseTelemetryPacket(event.data);
      }
    };

    ws.onclose = () => {
      isConnected = false;
      if (ppsInterval) clearInterval(ppsInterval);
      if (pingInterval) clearInterval(pingInterval);
      addLog("Engine WebSocket disconnected. Reconnecting...", "error");
      setTimeout(connectWebSocket, 1200);
    };

    ws.onerror = () => {
      if (ws) ws.close();
    };
  }

  function sendPing() {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'ping', t: performance.now() }));
    }
  }

  function parseTelemetryPacket(buffer) {
     if (buffer.byteLength >= 6) {
         const view = new DataView(buffer);
         const throttle = view.getUint8(2) / 255.0;
         const brake = view.getUint8(3) / 255.0;
         const rawTilt = view.getInt16(4, true);
         const angleDeg = rawTilt / 100.0;

         updateLiveMonitor(angleDeg, throttle, brake);
     }
  }

  function updateLiveMonitor(angle, throttle, brake) {
      const steeringText = document.getElementById('steeringText');
      const throttleBar = document.getElementById('throttleBar');
      const brakeBar = document.getElementById('brakeBar');

      if (steeringText) steeringText.textContent = `${angle > 0 ? '+' : ''}${angle.toFixed(1)}°`;
      if (throttleBar) throttleBar.style.width = `${Math.min(100, Math.max(0, throttle * 100))}%`;
      if (brakeBar) brakeBar.style.width = `${Math.min(100, Math.max(0, brake * 100))}%`;

      drawSteering(angle);
  }

  function drawSteering(angle) {
      const canvas = document.getElementById('steeringCanvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const cx = canvas.width / 2;
      const cy = canvas.height / 2;
      const r = 40;

      ctx.save();
      ctx.translate(cx, cy);
      ctx.rotate(angle * Math.PI / 180);

      // Draw wheel outline
      ctx.strokeStyle = '#2C3E5A';
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.arc(0, 0, r, 0, Math.PI * 2);
      ctx.stroke();

      // Draw top marker
      ctx.fillStyle = '#00E5FF';
      ctx.beginPath();
      ctx.arc(0, -r, 6, 0, Math.PI * 2);
      ctx.fill();

      ctx.restore();
  }

  function updatePps() {
    currentPps = lastPacketCount;
    lastPacketCount = 0;

    if (hasClient) {
        if (statPps) statPps.textContent = currentPps.toString();
        // Dynamic Equalizer Animation
        if (eqBars && eqBars.length > 0) {
          eqBars.forEach(bar => {
            const h = currentPps > 0 ? (Math.floor(Math.random() * 12) + 2) : 2;
            bar.style.height = `${h}px`;
          });
        }
    }
  }

  // Copy helper
  window.copyLink = function (elementId, btn) {
    const el = document.getElementById(elementId);
    if (!el) return;
    navigator.clipboard.writeText(el.textContent.trim()).then(() => {
      const originalText = btn.textContent;
      btn.textContent = 'COPIED!';
      btn.classList.add('copied');
      setTimeout(() => {
        btn.textContent = originalText;
        btn.classList.remove('copied');
      }, 1500);
    });
  };

  // Quick Utilities
  window.openJoyCpl = function () {
    if (window.pywebview && window.pywebview.api) {
      window.pywebview.api.open_joy_cpl();
      addLog("Launched joy.cpl", "info");
    } else {
      apiFetch('/api/joy_cpl').catch(() => {});
    }
  };

  window.restartAdb = function () {
    if (window.pywebview && window.pywebview.api) {
      addLog("Starting ADB reverse bridge...", "info");
      window.pywebview.api.restart_adb().then(res => {
          if (res.ok) addLog("USB Bridge started successfully.", "success");
          else addLog("USB Bridge failed: " + res.msg, "error");
      });
    } else {
      apiFetch('/api/restart_adb')
        .then(res => res.json())
        .then(res => {
          if (res.ok) addLog("USB Bridge started successfully.", "success");
          else addLog("USB Bridge failed: " + res.msg, "error");
        }).catch(() => {});
    }
  };

  window.openWebLink = function () {
    window.open(`https://${currentHost}:8443`, '_blank');
  };

  window.repairDriver = function () {
     if (window.pywebview && window.pywebview.api) {
        window.pywebview.api.repair_driver();
        addLog("Opened ViGEmBus installer page.", "info");
     }
  };

  window.toggleSettings = function () {
      const modal = document.getElementById('settings-modal');
      if (modal.style.display === 'none') {
          modal.style.display = 'flex';
          addLog("Opened Settings Dialog.", "info");
      } else {
          modal.style.display = 'none';
      }
  };

  window.applyAutoStart = function () {
      const isEnabled = document.getElementById('chk-autostart').checked;
      if (window.pywebview && window.pywebview.api) {
          window.pywebview.api.toggle_autostart(isEnabled).then(res => {
              if(res) {
                  addLog(isEnabled ? "Enabled Auto-Start." : "Disabled Auto-Start.", "success");
              } else {
                  addLog("Failed to toggle Auto-Start.", "error");
                  document.getElementById('chk-autostart').checked = !isEnabled;
              }
          });
      } else {
          addLog("Auto-Start requires native desktop mode.", "warn");
          document.getElementById('chk-autostart').checked = !isEnabled;
      }
  };

  // Startup
  async function initializeDesktopControlCenter() {
    try {
      addLog("Authenticating desktop control center...", "info");
      await bootstrapDesktopSession();
      addLog("Desktop authentication successful.", "success");
      initQrCode();
      connectWebSocket();
      drawSteering(0);
    } catch (error) {
      addLog(`Desktop startup failed: ${error.message}`, "error");
      const device = document.getElementById("stat-device");
      if (device) {
        device.textContent = "Desktop Authentication Failed";
      }
    }
  }

  initializeDesktopControlCenter();
})();
