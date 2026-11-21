# Create a git worktree for a parallel feature branch.
#
# Usage:
#   .\scripts\new-worktree.ps1 budget
#   .\scripts\new-worktree.ps1 fix-search -Base main
#
# Each worktree gets its own debug app:
#   applicationId = app.fen.dev.<branch>   (coexists with the release app.fen)
#   launcher name = Fen·<branch>
#   icon background colour = derived from the branch name
param(
    [Parameter(Mandatory, Position = 0)]
    [string]$Name,
    [string]$Base = ""
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent $PSScriptRoot
$safe = ($Name.ToLower() -replace "[^a-z0-9]+", "-").Trim("-")
if (-not $safe) { throw "Invalid worktree name: $Name" }

$branch = "feat/$safe"
$target = Join-Path (Split-Path -Parent $repo) ("fen-" + $safe)

if (Test-Path $target) { throw "Target already exists: $target" }

if ($Base) {
    git -C $repo worktree add $target -b $branch $Base
} else {
    git -C $repo worktree add $target -b $branch
}

Write-Host ""
Write-Host "Worktree : $target"
Write-Host "Branch   : $branch"
$devId = ("feat/" + $safe) -replace "[^a-z0-9]+", "_"
Write-Host "Dev app  : app.fen.dev.$devId"
Write-Host ""
Write-Host "Next:"
Write-Host "  cd `"$target`""
Write-Host "  .\gradlew.bat :app:assembleDebug"
Write-Host "  python scripts\install.py --apk app\build\outputs\apk\debug\app-debug.apk"

