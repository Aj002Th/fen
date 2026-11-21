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

# Keep every argument free of spaces: Start-Process does not quote array items.
# A title like "Fen - 123" would otherwise be split and scrcpy would exit.
$args = @(
    "-s", $Serial,
    "--window-title=Fen-$Serial",
    "--max-size=1024",
    "--stay-awake",
    "--no-audio"
)

$log = Join-Path $env:TEMP "fen-mirror.log"
Remove-Item $log -ErrorAction SilentlyContinue

Start-Process -FilePath $scrcpy -ArgumentList $args `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"

Start-Sleep -Seconds 2
$proc = Get-Process scrcpy -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $proc) {
    Write-Host "scrcpy exited. Log:"
    Get-Content $log -ErrorAction SilentlyContinue | Select-Object -Last 20
    Get-Content "$log.err" -ErrorAction SilentlyContinue | Select-Object -Last 20
    exit 1
}

Write-Host "Mirroring $Serial (pid $($proc.Id)). Close the scrcpy window to disconnect."
Write-Host "Log: $log"
