# Mirror the phone/emulator screen on the PC (scrcpy).
# Usage:
#   .\scripts\mirror.ps1
#   .\scripts\mirror.ps1 -Serial beb0cde7
param([string]$Serial)

$ErrorActionPreference = "Stop"

$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = "C:\Android\sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"

function Resolve-Serial {
    $rows = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s*$' }
    $serials = @($rows | ForEach-Object { ($_ -split '\s+')[0] })
    if ($serials.Count -eq 0) { throw "No device online. Connect a phone or boot the emulator first." }
    $physical = @($serials | Where-Object { $_ -notlike 'emulator-*' })
    if ($physical.Count -gt 0) { return $physical[0] }
    return $serials[0]
}

if (-not $Serial) { $Serial = Resolve-Serial }

$scrcpy = (Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter scrcpy.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1).FullName
if (-not $scrcpy) { throw "scrcpy not found. Run: winget install Genymobile.scrcpy" }

Write-Host "Mirroring: $Serial"
Start-Process -FilePath $scrcpy -ArgumentList @(
    "-s", $Serial,
    "--window-title", "Fen - $Serial",
    "--max-size", "1024",
    "--stay-awake",
    "--no-audio"
)
Write-Host "scrcpy window opened. Click/swipe with the mouse; close the window to disconnect."
