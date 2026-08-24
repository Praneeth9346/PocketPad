@echo off
title PocketPad - 1-Click Standalone EXE Compiler
cd /d "%~dp0"

echo ================================================================
echo    🏎️  BUILDING POCKETPAD STANDALONE WINDOWS EXECUTABLE (.EXE) 🎮
echo ================================================================
echo.
echo [1/3] Checking Python and PyInstaller...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python is not installed or not in PATH!
    pause
    exit /b 1
)

echo [2/3] Compiling PocketPad.exe with embedded web assets...
python -m PyInstaller --noconfirm --clean PocketPad.spec

if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed! Check output above.
    pause
    exit /b 1
)

echo.
echo [3/3] Copying runtime web directory next to dist\PocketPad.exe for live editing...
if not exist "dist\web" mkdir "dist\web"
xcopy /E /I /Y "web" "dist\web" >nul

echo.
echo ================================================================
echo    ✅ BUILD COMPLETE! Standalone Executable Ready:
echo    👉 dist\PocketPad.exe
echo.
echo    💡 Note: You can edit files in 'web\' anytime without rebuilding!
echo ================================================================
pause
