<#
.SYNOPSIS
    Automated DroidBot UI Screenshot Capture System for PocketPad Android App.

.DESCRIPTION
    1. Validates adb environment and connected devices.
    2. Builds the PocketPad debug APK via Gradle wrapper (:app:assembleDebug).
    3. Installs the debug APK onto the target Android device/emulator.
    4. Executes DroidBot automated UI exploration and precision UI state capture.
    5. Normalizes and indexes screenshots into ui_screenshots/droidbot/ runs & latest.
    6. Generates screenshots_manifest.json and screenshot_report.md.

.PARAMETER DeviceSerial
    Optional ADB device serial number if multiple devices are connected.

.PARAMETER SkipBuild
    Skip running gradlew assembleDebug if APK is already built.

.PARAMETER EventCount
    Number of DroidBot exploration events (default: 80).

.PARAMETER TimeoutSeconds
    DroidBot timeout in seconds (default: 120).
#>

[CmdletBinding()]
param (
    [string]$DeviceSerial = "",
    [switch]$SkipBuild,
    [int]$EventCount = 80,
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Resolve-Path "$PSScriptRoot\.."

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   POCKETPAD AUTOMATED DROIDBOT SCREENSHOT CAPTURE" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 1. Validate ADB
try {
    $adbVersion = adb version 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB not found" }
    Write-Host "[*] ADB OK: $($adbVersion[0])" -ForegroundColor Green
} catch {
    Write-Host "[!] ERROR: Android Debug Bridge (adb) is not found in PATH." -ForegroundColor Red
    exit 1
}

# 2. Check Connected Devices
$devicesOutput = adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" }
$activeDevices = @()

foreach ($line in $devicesOutput) {
    if ($line -match "^\s*([^\s]+)\s+device\b") {
        $activeDevices += $matches[1]
    }
}

if ($activeDevices.Count -eq 0) {
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host "NO ANDROID DEVICE/EMULATOR DETECTED" -ForegroundColor Red
    Write-Host "==================================================" -ForegroundColor Red
    Write-Host "[!] No Android physical device or emulator is currently attached in 'device' state." -ForegroundColor Yellow
    Write-Host "[!] Please connect an Android device via USB (with USB Debugging enabled)" -ForegroundColor Yellow
    Write-Host "[!] or launch an Android emulator, then rerun this script." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

if ([string]::IsNullOrEmpty($DeviceSerial)) {
    $DeviceSerial = $activeDevices[0]
}

Write-Host "[*] Target Device Selected: $DeviceSerial (Total attached: $($activeDevices.Count))" -ForegroundColor Green

# 3. Build Debug APK
$ApkPath = "$ProjectRoot\app\build\outputs\apk\debug\app-debug.apk"

if (-not $SkipBuild) {
    Write-Host "[*] Building PocketPad debug APK (:app:assembleDebug)..." -ForegroundColor Yellow
    Set-Location $ProjectRoot
    $buildOutput = .\gradlew :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[!] ERROR: Gradle build failed!" -ForegroundColor Red
        Write-Host $buildOutput
        exit 1
    }
}

if (-not (Test-Path $ApkPath)) {
    Write-Host "[!] ERROR: Debug APK not found at: $ApkPath" -ForegroundColor Red
    exit 1
}

Write-Host "[*] Debug APK ready: $ApkPath" -ForegroundColor Green

# 4. Verify Python & Virtual Environment
$VenvPython = "$ProjectRoot\.venv-droidbot\Scripts\python.exe"
if (-not (Test-Path $VenvPython)) {
    Write-Host "[*] Initializing .venv-droidbot environment..." -ForegroundColor Yellow
    python -m venv "$ProjectRoot\.venv-droidbot"
    & "$ProjectRoot\.venv-droidbot\Scripts\pip.exe" install "setuptools<70" "droidbot" "opencv-python"
}

# 5. Run Python Screenshot Capture Controller
$CaptureScript = "$ProjectRoot\tools\droidbot_ui_capture.py"
Write-Host "[*] Launching DroidBot Screenshot Capture Controller..." -ForegroundColor Cyan

& $VenvPython $CaptureScript --serial $DeviceSerial --apk $ApkPath --count $EventCount --timeout $TimeoutSeconds

if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Screenshot capture execution returned exit code $LASTEXITCODE" -ForegroundColor Yellow
}
