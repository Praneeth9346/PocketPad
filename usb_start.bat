@echo off
title PocketPad - USB 0.2ms Ultra-Speed Launcher
cd /d "%~dp0"
echo ========================================================
echo   PocketPad - USB 0.2ms Ultra-Speed Mode Setup
echo ========================================================
echo.
python usb_setup.py
echo.
echo Starting PocketPad Server...
python server.py
pause
