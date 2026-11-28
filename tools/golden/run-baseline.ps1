# Golden-master baseline run (BUILD_SPEC 7.1): the UNTOUCHED engine (tag baseline-engine), on Windows.
#   powershell -ExecutionPolicy Bypass -File tools\golden\run-baseline.ps1 [--gens 4,5] [--only typical] [...]
# The untouched engine is checked out from the tag into testroms\golden-work\baseline-src (a git worktree),
# so this works even though src\ has been changed for Android.
. (Join-Path $PSScriptRoot "common.ps1")
$Work = Join-Path $Repo "testroms\golden-work"
$Label = "baseline"
$Manifest = Join-Path $Repo "tools\golden\manifests\$Label.tsv"
$Base = Join-Path $Work "baseline-src"

# 1. Untouched engine source from the tag.
New-Item -ItemType Directory -Force $Work | Out-Null
$ErrorActionPreference = "Continue"
if (-not (Test-Path (Join-Path $Base ".git"))) { git worktree add --detach $Base baseline-engine *> $null }
$ErrorActionPreference = "Stop"
if (-not (Test-Path (Join-Path $Base ".git"))) { throw "Could not create the baseline worktree in $Base" }
$head = (git -C $Base rev-parse HEAD).Trim()
$dirty = git -C $Base status --porcelain
if ($head -ne $Pinned -or $dirty) { throw "$Base is not a clean checkout of baseline-engine ($Pinned). Delete that folder, run 'git worktree prune', and try again." }
$Rev = $Pinned
$Src = Join-Path $Base "src"

# 2. Compile: shim (sets ROOT_PATH = $Work\root\), then engine minus GUI/launcher, plus the harness. UTF-8 is required.
foreach ($d in @("root", "classes")) { $p = Join-Path $Work $d; if (Test-Path $p) { Remove-Item -Recurse -Force $p } }
New-Item -ItemType Directory -Force (Join-Path $Work "root\shim"), (Join-Path $Work "classes") | Out-Null
& $Javac -encoding UTF-8 --release 17 -nowarn -d (Join-Path $Work "root\shim") "tools\golden\shim\com\dabomstew\pkrandom\newgui\NewRandomizerGUI.java"
if ($LASTEXITCODE -ne 0) { throw "Shim compile failed" }
$srcs = Get-ChildItem -Recurse -Filter *.java $Src | Where-Object {
    $_.FullName -notlike "*\src\com\dabomstew\pkrandom\newgui\*" -and $_.FullName -notlike "*\src\launcher\*" }
$srcs += Get-ChildItem -Recurse -Filter *.java tools\golden\java
$list = Join-Path $Work "sources.txt"
Write-SourceList $srcs $list
& $Javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp (Join-Path $Work "root\shim") -d (Join-Path $Work "classes") "@$list"
if ($LASTEXITCODE -ne 0) { throw "Engine compile failed" }
$CP = (Join-Path $Work "classes") + ";" + (Join-Path $Work "root\shim") + ";" + $Src

# 3. Run.
& $Java "-Djava.awt.headless=true" -cp $CP uprzx.golden.GoldenRunner `
  --roms (Join-Path $Repo "testroms") --work $Work --profiles "tools\golden\profiles.tsv" `
  --manifest $Manifest --label $Label --engine-rev $Rev --exclude-file "tools\golden\exclude.txt" @args
exit $LASTEXITCODE
