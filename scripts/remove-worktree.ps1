# Remove a feature worktree and clean up everything it left behind.
#
# Usage:
#   .\scripts\remove-worktree.ps1 delete-flow
#
# Refuses to run when the branch is not merged into master.
# Removes the worktree, deletes the merged branch, and uninstalls the
# matching dev app (app.fen.dev.feat_<name>) from every connected device.
param(
    [Parameter(Mandatory, Position = 0)]
    [string]$Name
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$safe = ($Name.ToLower() -replace "[^a-z0-9]+", "-").Trim("-")
if (-not $safe) { throw "Invalid worktree name: $Name" }

$branch = "feat/$safe"
$target = Join-Path (Split-Path -Parent $repo) ("fen-" + $safe)
$devId = ("feat/" + $safe) -replace "[^a-z0-9]+", "_"
$package = "app.fen.dev.$devId"

$merged = git -C $repo branch --merged master --format "%(refname:short)"
if ($merged -notcontains $branch) {
    throw "Branch $branch is not merged into master yet. Refusing to clean up."
}

if (Test-Path $target) {
    git -C $repo worktree remove $target --force
}
git -C $repo branch -d $branch

$sdk = $env:ANDROID_HOME
if (-not $sdk -or -not (Test-Path $sdk)) { $sdk = "C:\Android\sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (Test-Path $adb) {
    $rows = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice\s*$' }
    foreach ($row in $rows) {
        $serial = ($row -split '\s+')[0]
        & $adb -s $serial uninstall $package 2>$null | Out-Null
        Write-Host "Uninstalled $package from $serial"
    }
}

Write-Host ""
Write-Host "Cleaned up: $target"
Write-Host "Deleted branch: $branch"
Write-Host "Removed dev app: $package"
