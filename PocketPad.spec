# -*- mode: python ; coding: utf-8 -*-
import sys
from pathlib import Path
from PyInstaller.utils.hooks import collect_all

block_cipher = None

# Collect all dynamic libraries and assets
vg_datas, vg_binaries, vg_hidden = collect_all('vgamepad')
qr_datas, qr_binaries, qr_hidden = collect_all('qrcode')
wv_datas, wv_binaries, wv_hidden = collect_all('webview')
pp_datas, pp_binaries, pp_hidden = collect_all('pocketpad')

all_datas = [('web', 'web')] + vg_datas + qr_datas + wv_datas + pp_datas
all_binaries = vg_binaries + qr_binaries + wv_binaries + pp_binaries
all_hidden = [
    'vgamepad',
    'qrcode',
    'cryptography',
    'websockets',
    'websockets.asyncio',
    'websockets.asyncio.server',
    'aiohttp',
    'pocketpad',
    'pocketpad.config',
    'pocketpad.core',
    'pocketpad.core.controller',
    'pocketpad.core.network',
    'pocketpad.core.telemetry',
    'pocketpad.core.utils',
    'controller_bridge',
    'ssl_helper',
    'usb_setup',
    'server',
    'desktop_app',
    'webview',
    'clr_loader',
    'pythonnet',
] + vg_hidden + qr_hidden + wv_hidden + pp_hidden

a = Analysis(
    ['desktop_app.py'],
    pathex=[],
    binaries=all_binaries,
    datas=all_datas,
    hiddenimports=all_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='PocketPad',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
