# Build the release APK and install it as the production app (app.fen).
# Use after merging a feature into master.
$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.13.102-hotspot"
}

& (Join-Path $repo "gradlew.bat") ":app:assembleRelease" "-q"

$apk = Join-Path $repo "app\build\outputs\apk\release\app-release.apk"
python (Join-Path $repo "scripts\install.py") --apk $apk --screenshot
