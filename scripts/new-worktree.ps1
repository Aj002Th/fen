# Create a git worktree for a parallel feature branch.
#
# Usage:
#   .\scripts\new-worktree.ps1 budget
#   .\scripts\new-worktree.ps1 fix-search -Base origin/master
#
# The branch is created from the latest master (origin/master when available),
# and the worktree sits next to the main checkout: <parent>\fen-<name>.
#
# Each worktree gets its own debug app:
#   applicationId = app.fen.dev.feat_<name>
#   launcher name = Fen·feat-<name>
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

# Refresh refs when a remote exists; ignore failures on offline machines.
git -C $repo fetch --all --prune 2>$null | Out-Null

if (-not $Base) {
    $hasOrigin = git -C $repo rev-parse --verify --quiet origin/master 2>$null
    $Base = if ($hasOrigin) { "origin/master" } else { "master" }
}

git -C $repo worktree add $target -b $branch $Base

$devId = ("feat/" + $safe) -replace "[^a-z0-9]+", "_"

Write-Host ""
Write-Host "Worktree : $target"
Write-Host "Branch   : $branch  (from $Base)"
Write-Host "Dev app  : app.fen.dev.$devId"
Write-Host "Icon     : Fen·feat-$safe"
Write-Host ""
Write-Host "Next:"
Write-Host "  cd `"$target`""
Write-Host "  .\gradlew.bat :app:assembleDebug"
Write-Host "  python scripts\install.py"
Write-Host ""
Write-Host "验收通过、用户明确同意后才能合并："
Write-Host "  git merge --no-ff $branch"
Write-Host "  .\scripts\remove-worktree.ps1 $safe"
