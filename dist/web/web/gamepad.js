// PocketPad - Ultra-Low-Latency Forza Horizon Precision Gamepad Engine (Glitch-Free Gravity Pipeline)
(() => {
  const isHttps = window.location.protocol === 'https:';
  const WS_PORT = isHttps ? 8766 : 8765;
  const WS_PROTOCOL = isHttps ? 'wss:' : 'ws:';

  let socket = null;
  let isConnected = false;
  let pingInterval = null;
  let radioKeepaliveInterval = null;
  let lastPingHighRes = 0;
  let wakeLock = null;

  // Settings & Precision Calibration State
  let motionEnabled = false;
  let latestRawAngle = 0.0;
  let lastProcessedAngle = 0.0;
  let calibratedCenter = 0.0;
  let manualTrimOffset = 0.0;
  let invertSteering = false;
  let maxSteeringAngle = 45; // degrees for 100% lock (Default: 45° GT/Sport)
  let steeringCurveExponent = 1.0; // Linearity power curve (1.0 = linear, 1.6 = progressive S-curve)
  let steeringDeadzone = 0.02; // 2% deadzone
  let pedalMode = 'analog';

  let smoothedAngle = 0.0;
  const SMOOTHING_FACTOR = 0.82; // Butter-smooth responsive EMA

  let currentSteerX = 0.0;
  let lastSentSteerX = 999.0;
  let currentThrottle = 0.0;
  let currentBrake = 0.0;
  let lastSentThrottle = -1.0;
  let lastSentBrake = -1.0;
  let stateDirty = false;

  // Pre-allocated Binary Buffers (Zero Allocations in hot paths)
  const steerBuffer = new ArrayBuffer(3);
  const steerView = new DataView(steerBuffer);
  steerView.setUint8(0, 0x01); // 0x01 = Steer

  const pedalBuffer = new ArrayBuffer(3);
  const pedalView = new DataView(pedalBuffer);
  pedalView.setUint8(0, 0x02); // 0x02 = Pedals

  const buttonBuffer = new ArrayBuffer(3);
  const buttonView = new DataView(buttonBuffer);
  buttonView.setUint8(0, 0x03); // 0x03 = Button

  const leftStickBuffer = new ArrayBuffer(5);
  const leftStickView = new DataView(leftStickBuffer);
  leftStickView.setUint8(0, 0x05); // 0x05 = Left Stick (X, Y)

  const rightStickBuffer = new ArrayBuffer(5);
  const rightStickView = new DataView(rightStickBuffer);
  rightStickView.setUint8(0, 0x06); // 0x06 = Right Stick (X, Y)

  const pingBuffer = new ArrayBuffer(5);
  const pingView = new DataView(pingBuffer);
  pingView.setUint8(0, 0x09); // 0x09 = Ping

  const keepaliveBuffer = new ArrayBuffer(1);
  const keepaliveView = new DataView(keepaliveBuffer);
  keepaliveView.setUint8(0, 0x00);

  const BUTTON_INDEX_MAP = {
    'A': 0, 'B': 1, 'X': 2, 'Y': 3,
    'DPAD_UP': 4, 'DPAD_DOWN': 5, 'DPAD_LEFT': 6, 'DPAD_RIGHT': 7,
    'START': 8, 'BACK': 9, 'GUIDE': 10,
    'LB': 11, 'RB': 12, 'LS': 13, 'RS': 14
  };

  // DOM Elements
  const connBadge = document.getElementById('conn-badge');
  const pingBadge = document.getElementById('ping-badge');
  const btnFullscreen = document.getElementById('btn-fullscreen');
  const btnSettings = document.getElementById('btn-settings');
  const settingsModal = document.getElementById('settings-modal');
  const btnCloseSettings = document.getElementById('btn-close-settings');
  const httpsWarningBanner = document.getElementById('https-warning-banner');

  const tabRacing = document.getElementById('tab-racing');
  const tabStandard = document.getElementById('tab-standard');
  const viewRacing = document.getElementById('mode-racing-view');
  const viewStandard = document.getElementById('mode-standard-view');

  // Racing HUD DOM
  const wheelGraphic = document.getElementById('wheel-graphic');
  const wheelContainer = document.getElementById('wheel-container');
  const steeringAngleText = document.getElementById('steering-angle-text');
  const indLeft = document.getElementById('ind-left');
  const indRight = document.getElementById('ind-right');
  const btnEnableMotion = document.getElementById('btn-enable-motion');
  const btnCalibrateMotion = document.getElementById('btn-calibrate-motion');
  const btnTrimLeft = document.getElementById('btn-trim-left');
  const btnTrimRight = document.getElementById('btn-trim-right');
  const btnInvertSteer = document.getElementById('btn-invert-steer');
  const sensorDebugText = document.getElementById('sensor-debug-text');

  const throttleZone = document.getElementById('throttle-pedal-zone');
  const throttleFill = document.getElementById('throttle-meter-fill');
  const throttlePct = document.getElementById('throttle-pct');

  const brakeZone = document.getElementById('brake-pedal-zone');
  const brakeFill = document.getElementById('brake-meter-fill');
  const brakePct = document.getElementById('brake-pct');

  // Live Telemetry Dashboard DOM Elements
  const shiftLightDots = document.querySelectorAll('#shift-light-bar .rpm-dot');
  const telemSpeed = document.getElementById('telem-speed');
  const telemSpeedUnit = document.getElementById('telem-speed-unit');
  const telemRpm = document.getElementById('telem-rpm');
  const telemBoost = document.getElementById('telem-boost');
  const telemSlip = document.getElementById('telem-slip');
  const telemSlipBlock = document.getElementById('telem-slip-block');
  const telemetryGear = document.getElementById('telemetry-gear');
  const btnTelemetryDemo = document.getElementById('btn-telemetry-demo');

  let speedUnit = 'MPH';
  let lastGear = -1;
  let isDemoTelemetry = false;

  // Standard Sticks
  const sticks = {
    LEFT: {
      zone: document.getElementById('stick-left'),
      thumb: document.getElementById('stick-left-thumb'),
      pointerId: null,
      x: 0,
      y: 0,
    },
    RIGHT: {
      zone: document.getElementById('stick-right'),
      thumb: document.getElementById('stick-right-thumb'),
      pointerId: null,
      x: 0,
      y: 0,
    }
  };

  // ==================== SCREEN WAKE LOCK ====================
  async function requestWakeLock() {
    if ('wakeLock' in navigator) {
      try {
        wakeLock = await navigator.wakeLock.request('screen');
      } catch (err) {}
    }
  }
  document.addEventListener('visibilitychange', () => {
    if (wakeLock !== null && document.visibilityState === 'visible') {
      requestWakeLock();
    }
  });

  // ==================== SECURE CONTEXT CHECK ====================
  function checkSecureContext() {
    const isSecure = window.isSecureContext || window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
    if (!isSecure && !isHttps && httpsWarningBanner) {
      httpsWarningBanner.style.display = 'flex';
      const host = window.location.hostname;
      const httpsLink = document.getElementById('https-switch-link');
      if (httpsLink) {
        httpsLink.href = `https://${host}:8443`;
      }
    }
  }

  // ==================== ULTRA-FAST C-SPEED BINARY NETWORKING ====================
  function handleIncomingBinary(view) {
    const opcode = view.getUint8(0);
    if (opcode === 0x0A) { // Pong
      const rtt = performance.now() - lastPingHighRes;
      pingBadge.textContent = `${rtt.toFixed(1)} ms`;
      if (rtt < 3) {
        pingBadge.style.color = '#39ff14';
      } else if (rtt < 7) {
        pingBadge.style.color = '#58a6ff';
      } else {
        pingBadge.style.color = '#d29922';
      }
    }
    else if (opcode === 0x10 && view.byteLength >= 13) { // 🏎️ Live Forza Telemetry Packet
      handleForzaTelemetryBinary(view);
    }
  }

  function sendBinaryPacket(buffer) {
    if (isConnected && socket && socket.readyState === WebSocket.OPEN) {
      if (socket.bufferedAmount === 0) {
        socket.send(buffer);
      }
    }
  }

  function connectWebSocket() {
    const host = window.location.hostname || 'localhost';
    const wsUrl = `${WS_PROTOCOL}//${host}:${WS_PORT}`;

    connBadge.textContent = 'Connecting...';
    connBadge.className = 'status-badge disconnected';

    try {
      socket = new WebSocket(wsUrl);
      socket.binaryType = 'arraybuffer';
    } catch (e) {
      setTimeout(connectWebSocket, 1500);
      return;
    }

    socket.onopen = () => {
      isConnected = true;
      const isUsb = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.hostname.startsWith('10.18.');
      connBadge.textContent = isUsb ? '⚡ USB Wired (0.2ms)' : '📶 Wi-Fi 5GHz (QoS)';
      connBadge.className = 'status-badge connected';
      requestWakeLock();

      if (pingInterval) clearInterval(pingInterval);
      pingInterval = setInterval(sendBinaryPing, 1000);

      if (radioKeepaliveInterval) clearInterval(radioKeepaliveInterval);
      radioKeepaliveInterval = setInterval(sendRadioKeepalive, 50);

      requestAnimationFrame(flushInputLoop);
    };

    socket.onmessage = (event) => {
      if (event.data instanceof ArrayBuffer) {
        handleIncomingBinary(new DataView(event.data));
      } else if (typeof event.data === 'string') {
        try {
          const msg = JSON.parse(event.data);
          if (msg.type === 'telemetry_mode') {
            isDemoTelemetry = msg.demo;
            if (btnTelemetryDemo) {
              btnTelemetryDemo.classList.toggle('active', isDemoTelemetry);
              btnTelemetryDemo.innerHTML = isDemoTelemetry ? '<span>🏁</span> DEMO: ON' : '<span>🏁</span> DASH DEMO';
            }
          }
        } catch(err){}
      }
    };

    socket.onclose = () => {
      isConnected = false;
      connBadge.textContent = 'Disconnected';
      connBadge.className = 'status-badge disconnected';
      pingBadge.textContent = '-- ms';
      if (pingInterval) clearInterval(pingInterval);
      if (radioKeepaliveInterval) clearInterval(radioKeepaliveInterval);
      setTimeout(connectWebSocket, 1200);
    };

    socket.onerror = () => {
      if (socket) socket.close();
    };
  }

  function sendBinaryPing() {
    if (isConnected && socket && socket.readyState === WebSocket.OPEN) {
      lastPingHighRes = performance.now();
      pingView.setUint32(1, Math.floor(lastPingHighRes) & 0xFFFFFFFF, true);
      socket.send(pingBuffer);
    }
  }

  // ==================== LIVE FORZA TELEMETRY DASHBOARD ENGINE ====================
  function handleForzaTelemetryBinary(view) {
    const rpm = view.getUint16(1, true);
    const maxRpm = view.getUint16(3, true);
    const speedMphX10 = view.getUint16(5, true);
    const gear = view.getUint8(7);
    const shiftPct = view.getUint8(8);
    const slipPct = view.getUint8(9);
    const boost = view.getUint8(12);

    // 1. Digital Speedometer Display
    const speedMph = speedMphX10 / 10.0;
    const finalSpeed = (speedUnit === 'KMH') ? Math.round(speedMph * 1.60934) : Math.round(speedMph);
    if (telemSpeed) telemSpeed.textContent = finalSpeed;
    if (telemSpeedUnit) telemSpeedUnit.textContent = speedUnit;

    // 2. Live RPM & Turbo Boost Readout
    if (telemRpm) telemRpm.textContent = rpm.toLocaleString();
    if (telemBoost) telemBoost.textContent = (boost > 0 ? (boost / 1.0).toFixed(1) : '0.0');

    // 3. Tire Slip & G-Force Drift Indicator
    if (telemSlip && telemSlipBlock) {
      if (slipPct > 22) {
        telemSlip.textContent = `DRIFT ${slipPct}%`;
        telemSlipBlock.classList.add('drifting');
      } else {
        telemSlip.textContent = 'GRIP';
        telemSlipBlock.classList.remove('drifting');
      }
    }

    // 4. Digital Gear Indicator & Shift Pulse
    if (telemetryGear) {
      let gearStr = 'N';
      if (gear === 0) gearStr = 'R';
      else if (gear >= 1 && gear <= 10) gearStr = gear.toString();
      else if (gear > 10) gearStr = 'N';

      if (gear !== lastGear) {
        lastGear = gear;
        telemetryGear.textContent = gearStr;
        telemetryGear.classList.add('shift-pulse');
        triggerHaptic(20);
        setTimeout(() => telemetryGear.classList.remove('shift-pulse'), 120);
      }
    }

    // 5. Formula 1 / GT3 Dynamic LED Shift Lights (10 Segments)
    if (shiftLightDots && shiftLightDots.length === 10) {
      if (shiftPct >= 96) {
        // Redline Strobe Limiter Strobe!
        shiftLightDots.forEach(dot => {
          dot.className = 'rpm-dot led-strobe';
        });
      } else {
        shiftLightDots.forEach((dot, idx) => {
          let activeClass = '';
          if (idx < 4) { // Green LEDs (50% - 65%)
            if (shiftPct >= (50 + idx * 4)) activeClass = 'led-green';
          } else if (idx < 7) { // Yellow LEDs (68% - 80%)
            if (shiftPct >= (68 + (idx - 4) * 4.5)) activeClass = 'led-yellow';
          } else { // Red LEDs (82% - 94%)
            if (shiftPct >= (82 + (idx - 7) * 4)) activeClass = 'led-red';
          }
          dot.className = activeClass ? `rpm-dot ${activeClass}` : 'rpm-dot';
        });
      }
    }
  }

  function toggleTelemetryDemo() {
    const demoBuf = new ArrayBuffer(1);
    new DataView(demoBuf).setUint8(0, 0x11); // Opcode 0x11: Toggle Demo
    sendBinaryPacket(demoBuf);
    triggerHaptic(30);
  }

  function sendRadioKeepalive() {
    sendBinaryPacket(keepaliveBuffer);
  }

  function sendBinarySteer(normX) {
    const intVal = Math.round(normX * 32767);
    steerView.setInt16(1, intVal, true);
    sendBinaryPacket(steerBuffer);
    lastSentSteerX = normX;
  }

  // 2D Thumbsticks (Left Stick 0x05 / Right Stick 0x06)
  function sendBinaryStick(side, x, y) {
    const intX = Math.round(x * 32767);
    const intY = Math.round(y * 32767);
    if (side === 'LEFT') {
      leftStickView.setInt16(1, intX, true);
      leftStickView.setInt16(3, intY, true);
      sendBinaryPacket(leftStickBuffer);
    } else if (side === 'RIGHT') {
      rightStickView.setInt16(1, intX, true);
      rightStickView.setInt16(3, intY, true);
      sendBinaryPacket(rightStickBuffer);
    }
  }

  function sendBinaryPedals(lt, rt) {
    const ltByte = Math.round(lt * 255);
    const rtByte = Math.round(rt * 255);
    pedalView.setUint8(1, ltByte);
    pedalView.setUint8(2, rtByte);
    sendBinaryPacket(pedalBuffer);
    lastSentThrottle = rt;
    lastSentBrake = lt;
  }

  function sendBinaryButton(btnName, pressed) {
    const idx = BUTTON_INDEX_MAP[btnName];
    if (idx !== undefined) {
      buttonView.setUint8(1, idx);
      buttonView.setUint8(2, pressed ? 1 : 0);
      sendBinaryPacket(buttonBuffer);
    }
  }

  function sendBinaryPing() {
    lastPingHighRes = performance.now();
    pingView.setUint32(1, Math.floor(lastPingHighRes) & 0xFFFFFFFF, true);
    sendBinaryPacket(pingBuffer);
  }

  function flushInputLoop() {
    if (isConnected && socket && socket.readyState === WebSocket.OPEN) {
      if (stateDirty) {
        if (Math.abs(currentSteerX - lastSentSteerX) >= 0.002) {
          sendBinarySteer(currentSteerX);
        }
        if (Math.abs(currentThrottle - lastSentThrottle) >= 0.008 || Math.abs(currentBrake - lastSentBrake) >= 0.008) {
          sendBinaryPedals(currentBrake, currentThrottle);
        }
        stateDirty = false;
      }
    }
    requestAnimationFrame(flushInputLoop);
  }

  function triggerHaptic(pattern = 25) {
    if (navigator.vibrate) {
      try { navigator.vibrate(pattern); } catch (e) {}
    }
  }

  // ==================== MODE SWITCHING ====================
  tabRacing.addEventListener('click', () => {
    tabRacing.classList.add('active');
    tabStandard.classList.remove('active');
    viewRacing.classList.add('active');
    viewStandard.classList.remove('active');
    triggerHaptic(15);
  });

  tabStandard.addEventListener('click', () => {
    tabStandard.classList.add('active');
    tabRacing.classList.remove('active');
    viewStandard.classList.add('active');
    viewRacing.classList.remove('active');
    triggerHaptic(15);
  });

  // ==================== GLITCH-FREE GRAVITY VECTOR STEERING ENGINE ====================
  /**
   * Pure Gravity Vector Tracking:
   * Uses real-time lateral acceleration from devicemotion.
   * Completely immune to Euler angle flips and 180° gimbal jumps!
   */
  function handleDirectDeviceMotion(e) {
    if (!motionEnabled) return;

    const acc = e.accelerationIncludingGravity || e.acceleration;
    if (!acc || (acc.x === null && acc.y === null)) return;

    const screenAngle = (screen.orientation ? screen.orientation.angle : window.orientation) || 90;

    // Calculate lateral gravity pull across the long edge of the phone
    let lateralG = 0;
    if (screenAngle === 90) {
      lateralG = -(acc.y || 0); // Tilting left makes acc.y positive -> negated to negative angle
    } else if (screenAngle === 270 || screenAngle === -90) {
      lateralG = (acc.y || 0);
    } else {
      lateralG = (acc.x || 0);
    }

    // Clamp normalized gravity ratio to [-1.0, 1.0]
    const clampedRatio = Math.max(-1.0, Math.min(1.0, lateralG / 9.80665));

    // Convert to exact physical degrees: theta = arcsin(ratio) * (180/pi)
    let rawAngle = Math.asin(clampedRatio) * (180 / Math.PI);

    // Spike Rejection Filter: Discard impossible instant leaps (> 40° in single frame)
    if (lastProcessedAngle !== 0.0 && Math.abs(rawAngle - lastProcessedAngle) > 40) {
      return; // Reject corrupted frame
    }
    lastProcessedAngle = rawAngle;
    latestRawAngle = rawAngle;

    // Delta from calibrated center + manual trim
    const effectiveCenter = calibratedCenter + manualTrimOffset;
    let rawDelta = rawAngle - effectiveCenter;

    if (invertSteering) {
      rawDelta = -rawDelta;
    }

    // Apply Exponential Moving Average (EMA) smoothing for zero-jitter
    smoothedAngle = (SMOOTHING_FACTOR * rawDelta) + ((1 - SMOOTHING_FACTOR) * smoothedAngle);

    // Update Visual Wheel HUD
    const displayAngle = Math.abs(smoothedAngle) < 0.05 ? 0.0 : smoothedAngle;
    wheelGraphic.style.transform = `rotate(${displayAngle * 2.2}deg)`;
    steeringAngleText.textContent = `${displayAngle >= 0 ? '+' : ''}${displayAngle.toFixed(1)}°`;

    // Direction Tag Glows
    if (displayAngle < -1.5) {
      indLeft.classList.add('active-left');
      indRight.classList.remove('active-right');
    } else if (displayAngle > 1.5) {
      indRight.classList.add('active-right');
      indLeft.classList.remove('active-left');
    } else {
      indLeft.classList.remove('active-left');
      indRight.classList.remove('active-right');
    }

    if (sensorDebugText) {
      sensorDebugText.textContent = `Gravity Vector | Delta: ${displayAngle.toFixed(1)}° | Smooth: 120Hz`;
    }

    // Map to Normalized Controller Output [-1.0 to +1.0]
    let norm = smoothedAngle / maxSteeringAngle;
    norm = Math.max(-1.0, Math.min(1.0, norm));

    if (Math.abs(norm) < steeringDeadzone) {
      norm = 0.0;
    } else {
      // Apply S-Curve Linearity
      const sign = Math.sign(norm);
      const mag = Math.abs(norm);
      norm = sign * Math.pow(mag, steeringCurveExponent);
    }

    currentSteerX = Math.round(norm * 1000) / 1000;
    stateDirty = true;
  }

  // ==================== INSTANT ZERO CALIBRATION ====================
  function executeInstantZeroCalibration() {
    calibratedCenter = latestRawAngle;
    manualTrimOffset = 0.0;
    smoothedAngle = 0.0;
    currentSteerX = 0.0;
    lastSentSteerX = 0.0;

    wheelGraphic.style.transform = 'rotate(0deg)';
    steeringAngleText.textContent = '0.0° (Zeroed)';
    indLeft.classList.remove('active-left');
    indRight.classList.remove('active-right');

    sendBinarySteer(0.0);
    triggerHaptic([40, 50]);

    setTimeout(() => {
      steeringAngleText.textContent = '0.0°';
    }, 600);
  }

  btnCalibrateMotion.addEventListener('click', executeInstantZeroCalibration);

  // Invert Steering Toggle
  if (btnInvertSteer) {
    btnInvertSteer.addEventListener('click', () => {
      invertSteering = !invertSteering;
      btnInvertSteer.textContent = `🔄 Invert: ${invertSteering ? 'ON' : 'OFF'}`;
      btnInvertSteer.style.borderColor = invertSteering ? 'var(--forza-magenta)' : 'rgba(255,255,255,0.12)';
      triggerHaptic(25);
    });
  }

  // Micro-Trim Buttons (±0.5°)
  if (btnTrimLeft && btnTrimRight) {
    btnTrimLeft.addEventListener('click', () => {
      manualTrimOffset -= 0.5;
      triggerHaptic(15);
    });

    btnTrimRight.addEventListener('click', () => {
      manualTrimOffset += 0.5;
      triggerHaptic(15);
    });
  }

  // Touch Steering Wheel Drag Fallback (240Hz Hardware Touch Event)
  const moveEventName = ('onpointerrawupdate' in window) ? 'pointerrawupdate' : 'pointermove';

  function setupTouchSteering() {
    let isTouchingWheel = false;

    wheelContainer.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      isTouchingWheel = true;
      wheelContainer.setPointerCapture(e.pointerId);
    });

    wheelContainer.addEventListener(moveEventName, (e) => {
      if (!isTouchingWheel) return;
      e.preventDefault();
      const rect = wheelContainer.getBoundingClientRect();
      const deltaX = e.clientX - (rect.left + rect.width / 2);
      const maxDist = rect.width / 2;
      const pct = Math.max(-1.0, Math.min(1.0, deltaX / maxDist));
      const angle = pct * maxSteeringAngle;

      smoothedAngle = angle;
      wheelGraphic.style.transform = `rotate(${angle * 2.2}deg)`;
      steeringAngleText.textContent = `${angle >= 0 ? '+' : ''}${angle.toFixed(1)}°`;

      currentSteerX = Math.round(pct * 1000) / 1000;
      stateDirty = true;
    });

    const onWheelRelease = (e) => {
      if (!isTouchingWheel) return;
      isTouchingWheel = false;
      try { wheelContainer.releasePointerCapture(e.pointerId); } catch(err){}
      if (!motionEnabled) {
        smoothedAngle = 0.0;
        currentSteerX = 0.0;
        wheelGraphic.style.transform = 'rotate(0deg)';
        steeringAngleText.textContent = '0.0°';
        stateDirty = true;
      }
    };

    wheelContainer.addEventListener('pointerup', onWheelRelease);
    wheelContainer.addEventListener('pointercancel', onWheelRelease);
  }

  // Toggle Motion Sensors ON / OFF
  btnEnableMotion.addEventListener('click', async () => {
    if (motionEnabled) {
      stopMotionListeners();
    } else {
      if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
          const response = await DeviceOrientationEvent.requestPermission();
          if (response === 'granted') {
            startMotionListeners();
          } else {
            alert('Motion sensor permission was denied in Safari. Please allow motion sensors in Settings.');
          }
        } catch (err) {
          startMotionListeners();
        }
      } else {
        startMotionListeners();
      }
    }
  });

  function startMotionListeners() {
    motionEnabled = true;
    btnEnableMotion.classList.add('active');
    btnEnableMotion.innerHTML = '<span>✅</span> Motion: ON';

    // Register ONLY the single pure gravity pipeline
    window.addEventListener('devicemotion', handleDirectDeviceMotion, true);

    triggerHaptic([30, 40, 50]);

    setTimeout(() => {
      executeInstantZeroCalibration();
    }, 150);
  }

  function stopMotionListeners() {
    motionEnabled = false;
    btnEnableMotion.classList.remove('active');
    btnEnableMotion.innerHTML = '<span>📱</span> Motion: OFF';

    window.removeEventListener('devicemotion', handleDirectDeviceMotion, true);

    smoothedAngle = 0.0;
    currentSteerX = 0.0;
    lastSentSteerX = 0.0;
    lastProcessedAngle = 0.0;
    wheelGraphic.style.transform = 'rotate(0deg)';
    steeringAngleText.textContent = '0.0° (OFF)';
    indLeft.classList.remove('active-left');
    indRight.classList.remove('active-right');

    if (sensorDebugText) {
      sensorDebugText.textContent = 'Sensor: Paused (Motion OFF)';
    }

    sendBinarySteer(0.0);
    triggerHaptic(20);
  }

  // ==================== ANALOG PEDALS (GPU-Accelerated 240Hz Scaling) ====================
  function setupPedals() {
    setupPedalZone(throttleZone, (val) => {
      currentThrottle = val;
      // GPU Composited scaleY Transform (Zero Layout Reflows)
      throttleFill.style.transform = `scaleY(${val})`;
      throttlePct.textContent = `${Math.round(val * 100)}%`;
      stateDirty = true;
    });

    setupPedalZone(brakeZone, (val) => {
      currentBrake = val;
      // GPU Composited scaleY Transform (Zero Layout Reflows)
      brakeFill.style.transform = `scaleY(${val})`;
      brakePct.textContent = `${Math.round(val * 100)}%`;
      stateDirty = true;
    });
  }

  function setupPedalZone(zoneElem, onValueChange) {
    let pointerId = null;

    const calcPedalValue = (clientY) => {
      const rect = zoneElem.getBoundingClientRect();
      const relativeY = clientY - rect.top;
      const height = rect.height;
      let pct = 1.0 - (relativeY / height);
      pct = Math.max(0.0, Math.min(1.0, pct));
      return Math.round(pct * 1000) / 1000;
    };

    zoneElem.addEventListener('pointerdown', (e) => {
      e.preventDefault();
      pointerId = e.pointerId;
      zoneElem.setPointerCapture(e.pointerId);
      triggerHaptic(20);

      const val = (pedalMode === 'digital') ? 1.0 : calcPedalValue(e.clientY);
      onValueChange(val);
      sendBinaryPedals(currentBrake, currentThrottle);
    });

    zoneElem.addEventListener(moveEventName, (e) => {
      if (e.pointerId !== pointerId) return;
      e.preventDefault();
      if (pedalMode === 'analog') {
        const val = calcPedalValue(e.clientY);
        onValueChange(val);
      }
    });

    const onRelease = (e) => {
      if (e.pointerId !== pointerId) return;
      e.preventDefault();
      pointerId = null;
      try { zoneElem.releasePointerCapture(e.pointerId); } catch(err){}
      onValueChange(0.0);
      sendBinaryPedals(currentBrake, 0.0);
    };

    zoneElem.addEventListener('pointerup', onRelease);
    zoneElem.addEventListener('pointercancel', onRelease);
  }

  // ==================== BUTTONS & PADDLE SHIFTERS ====================
  function initAllButtons() {
    const buttons = document.querySelectorAll('[data-btn]');
    buttons.forEach(btn => {
      const btnName = btn.getAttribute('data-btn');

      btn.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        btn.classList.add('active');
        btn.setPointerCapture(e.pointerId);

        if (btnName === 'A') triggerHaptic(45);
        else if (btnName === 'B' || btnName === 'X') triggerHaptic(30);
        else if (btnName === 'Y') triggerHaptic([20, 20, 20]);
        else triggerHaptic(20);

        sendBinaryButton(btnName, true);
      });

      const handleRelease = (e) => {
        e.preventDefault();
        btn.classList.remove('active');
        try { btn.releasePointerCapture(e.pointerId); } catch(err){}
        sendBinaryButton(btnName, false);
      };

      btn.addEventListener('pointerup', handleRelease);
      btn.addEventListener('pointercancel', handleRelease);
    });

    // Standard mode triggers
    const triggers = document.querySelectorAll('[data-trigger]');
    triggers.forEach(trig => {
      const trigName = trig.getAttribute('data-trigger');
      trig.addEventListener('pointerdown', (e) => {
        e.preventDefault();
        trig.classList.add('active');
        trig.setPointerCapture(e.pointerId);
        triggerHaptic(25);
        if (trigName === 'LT') sendBinaryPedals(1.0, currentThrottle);
        else if (trigName === 'RT') sendBinaryPedals(currentBrake, 1.0);
      });

      const handleTrigRelease = (e) => {
        e.preventDefault();
        trig.classList.remove('active');
        try { trig.releasePointerCapture(e.pointerId); } catch(err){}
        if (trigName === 'LT') sendBinaryPedals(0.0, currentThrottle);
        else if (trigName === 'RT') sendBinaryPedals(currentBrake, 0.0);
      };

      trig.addEventListener('pointerup', handleTrigRelease);
      trig.addEventListener('pointercancel', handleTrigRelease);
    });
  }

  // ==================== STANDARD THUMBSTICKS ====================
  function initStandardJoysticks() {
    ['LEFT', 'RIGHT'].forEach(side => {
      const stick = sticks[side];
      if (!stick.zone) return;

      const onPointerDown = (e) => {
        if (stick.pointerId !== null) return;
        e.preventDefault();
        stick.pointerId = e.pointerId;
        stick.zone.setPointerCapture(e.pointerId);
        stick.thumb.classList.add('active');
        updateStandardStick(side, e.clientX, e.clientY);
      };

      const onPointerMove = (e) => {
        if (e.pointerId !== stick.pointerId) return;
        e.preventDefault();
        updateStandardStick(side, e.clientX, e.clientY);
      };

      const onPointerUp = (e) => {
        if (e.pointerId !== stick.pointerId) return;
        e.preventDefault();
        stick.pointerId = null;
        try { stick.zone.releasePointerCapture(e.pointerId); } catch(err){}
        stick.thumb.classList.remove('active');
        stick.thumb.style.transform = `translate(0px, 0px)`;
        stick.x = 0;
        stick.y = 0;
        sendBinaryStick(side, 0.0, 0.0);
      };

      stick.zone.addEventListener('pointerdown', onPointerDown);
      stick.zone.addEventListener(moveEventName, onPointerMove);
      stick.zone.addEventListener('pointerup', onPointerUp);
      stick.zone.addEventListener('pointercancel', onPointerUp);
    });
  }

  function updateStandardStick(side, clientX, clientY) {
    const stick = sticks[side];
    const rect = stick.zone.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    let deltaX = clientX - centerX;
    let deltaY = clientY - centerY;
    const distance = Math.hypot(deltaX, deltaY);
    const maxRadius = rect.width / 2 - 10;

    if (distance > maxRadius) {
      deltaX = (deltaX / distance) * maxRadius;
      deltaY = (deltaY / distance) * maxRadius;
    }

    stick.thumb.style.transform = `translate(${deltaX}px, ${deltaY}px)`;

    let normX = deltaX / maxRadius;
    let normY = -(deltaY / maxRadius);

    if (Math.abs(normX) < 0.05) normX = 0;
    if (Math.abs(normY) < 0.05) normY = 0;

    stick.x = Math.round(normX * 1000) / 1000;
    stick.y = Math.round(normY * 1000) / 1000;

    sendBinaryStick(side, stick.x, stick.y);
  }

  // ==================== SETTINGS & CONTROLS ====================
  btnSettings.addEventListener('click', () => {
    settingsModal.classList.add('show');
  });

  btnCloseSettings.addEventListener('click', () => {
    settingsModal.classList.remove('show');
  });

  const sliderSens = document.getElementById('slider-sensitivity');
  const valSens = document.getElementById('val-sensitivity');
  const presetPills = document.querySelectorAll('.preset-pill');
  const btnQuickAngle = document.getElementById('btn-quick-angle');
  const anglePresetsList = [25, 45, 65, 90];

  function updateSteeringMaxAngle(angle) {
    maxSteeringAngle = Math.max(15, Math.min(90, parseInt(angle)));
    if (sliderSens) sliderSens.value = maxSteeringAngle;
    if (valSens) valSens.textContent = `${maxSteeringAngle}°`;
    if (btnQuickAngle) btnQuickAngle.textContent = `📐 ${maxSteeringAngle}° Lock`;

    presetPills.forEach(pill => {
      const pAngle = parseInt(pill.getAttribute('data-angle'));
      if (pAngle === maxSteeringAngle) {
        pill.classList.add('active');
      } else {
        pill.classList.remove('active');
      }
    });
  }

  if (sliderSens) {
    sliderSens.addEventListener('input', (e) => {
      updateSteeringMaxAngle(e.target.value);
    });
  }

  presetPills.forEach(pill => {
    pill.addEventListener('click', () => {
      const angle = pill.getAttribute('data-angle');
      updateSteeringMaxAngle(angle);
      triggerHaptic(20);
    });
  });

  if (btnQuickAngle) {
    btnQuickAngle.addEventListener('click', () => {
      let currentIdx = anglePresetsList.indexOf(maxSteeringAngle);
      if (currentIdx === -1) currentIdx = 1; // default to 45
      const nextIdx = (currentIdx + 1) % anglePresetsList.length;
      updateSteeringMaxAngle(anglePresetsList[nextIdx]);
      triggerHaptic(25);
    });
  }

  // Steering Linearity S-Curve Slider
  const sliderCurve = document.getElementById('slider-curve');
  const valCurve = document.getElementById('val-curve');
  if (sliderCurve && valCurve) {
    sliderCurve.addEventListener('input', (e) => {
      const rawVal = parseInt(e.target.value);
      steeringCurveExponent = rawVal / 10.0;
      if (rawVal === 10) {
        valCurve.textContent = '1.0x (Linear)';
      } else if (rawVal <= 16) {
        valCurve.textContent = `${steeringCurveExponent.toFixed(1)}x (S-Curve)`;
      } else {
        valCurve.textContent = `${steeringCurveExponent.toFixed(1)}x (High Precision)`;
      }
    });
  }

  const sliderDead = document.getElementById('slider-deadzone');
  const valDead = document.getElementById('val-deadzone');
  if (sliderDead && valDead) {
    sliderDead.addEventListener('input', (e) => {
      const dz = parseInt(e.target.value);
      steeringDeadzone = dz / 100;
      valDead.textContent = `${dz}%`;
    });
  }

  if (btnTelemetryDemo) {
    btnTelemetryDemo.addEventListener('click', () => {
      toggleTelemetryDemo();
    });
  }

  document.querySelectorAll('input[name="speed-unit"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      speedUnit = e.target.value;
      if (telemSpeedUnit) telemSpeedUnit.textContent = speedUnit;
    });
  });

  document.querySelectorAll('input[name="pedal-mode"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      pedalMode = e.target.value;
    });
  });

  btnFullscreen.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  document.addEventListener('contextmenu', e => e.preventDefault());

  // Startup
  checkSecureContext();
  initAllButtons();
  setupPedals();
  setupTouchSteering();
  initStandardJoysticks();
  updateSteeringMaxAngle(45); // Default 45°
  connectWebSocket();
})();
