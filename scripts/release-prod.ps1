# Build the release APK and install it as the production app (app.fen).
#
# ONLY run this for an explicit release, after the user asked to ship.
# A routine feature merge updates the master dev app (app.fen.dev.master),
# never the production app that users are already running.
#
# Usage:
#   .\scripts\release-prod.ps1          # asks for confirmation
#   .\scripts\release-prod.ps1 -Yes     # skip the confirmation prompt
param(
    [switch]$Yes
)

$ErrorActionPreference = "Stop"

if (-not $Yes) {
    Write-Host "这会覆盖手机上的正式版 app.fen（用户正在使用的黑色 Fen）。"
    $answer = Read-Host "确认发版请输入 release"
    if ($answer -ne "release") {
        Write-Host "已取消，正式版没有改动。"
        exit 1
    }
}

$repo = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"
}

& (Join-Path $repo "gradlew.bat") ":app:assembleRelease" "-q"

$apk = Join-Path $repo "app\build\outputs\apk\release\app-release.apk"
python (Join-Path $repo "scripts\install.py") --apk $apk --screenshot
