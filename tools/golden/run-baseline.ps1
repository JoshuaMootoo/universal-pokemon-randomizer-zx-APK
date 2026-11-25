# Golden-master baseline run (BUILD_SPEC 7.1) for Windows PowerShell.
# Run from anywhere:
#   powershell -ExecutionPolicy Bypass -File tools\golden\run-baseline.ps1
# Extra options are passed to the runner, e.g.  -File tools\golden\run-baseline.ps1 --gens 4,5 --only typical
$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Repo
$Work = Join-Path $Repo "testroms\golden-work"
$Label = "baseline"
$Manifest = Join-Path $Repo "tools\golden\manifests\$Label.tsv"

# Java: first JDK that actually starts, from JAVA_HOME, javac on PATH, or Android Studio's bundled JBR.
# (On Joshua's PC "Android Studio" is a broken leftover and the real install is "Android Studio1".)
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($onPath) { $candidates += (Split-Path (Split-Path $onPath.Source)) }
$candidates += "C:\Program Files\Android\Android Studio1\jbr", "C:\Program Files\Android\Android Studio\jbr"
$Jdk = $null
foreach ($c in $candidates) {
    $jc = Join-Path $c "bin\javac.exe"
    if (Test-Path $jc) {
        & $jc -version *> $null
        if ($LASTEXITCODE -eq 0) { $Jdk = $c; break }
    }
}
if (-not $Jdk) { throw "No working JDK found (tried: $($candidates -join '; ')). Set JAVA_HOME to a JDK 17 or newer." }
$Java = Join-Path $Jdk "bin\java.exe"
$Javac = Join-Path $Jdk "bin\javac.exe"
Write-Host "Using JDK: $Jdk"
& $Java -version

# 1. The baseline must be the untouched engine.
git rev-parse -q --verify refs/tags/baseline-engine *> $null
if ($LASTEXITCODE -ne 0) { git fetch -q origin tag baseline-engine }
git diff --quiet baseline-engine -- src
$diffCode = $LASTEXITCODE
$dirty = git status --porcelain -- src
if ($diffCode -ne 0 -or $dirty) { throw "src\ differs from tag baseline-engine. The baseline must run on the untouched engine." }
$Rev = (git rev-parse "baseline-engine^{commit}").Trim()

# 2. Compile: shim (sets ROOT_PATH = $Work\root\), then engine minus GUI/launcher, plus the harness. UTF-8 is required.
foreach ($d in @("root", "classes")) { $p = Join-Path $Work $d; if (Test-Path $p) { Remove-Item -Recurse -Force $p } }
New-Item -ItemType Directory -Force (Join-Path $Work "root\shim"), (Join-Path $Work "classes") | Out-Null
& $Javac -encoding UTF-8 --release 17 -nowarn -d (Join-Path $Work "root\shim") "tools\golden\shim\com\dabomstew\pkrandom\newgui\NewRandomizerGUI.java"
if ($LASTEXITCODE -ne 0) { throw "Shim compile failed" }
$srcs = Get-ChildItem -Recurse -Filter *.java src | Where-Object {
    $_.FullName -notlike "*\src\com\dabomstew\pkrandom\newgui\*" -and $_.FullName -notlike "*\src\launcher\*" }
$srcs += Get-ChildItem -Recurse -Filter *.java tools\golden\java
$list = Join-Path $Work "sources.txt"
($srcs | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }) | Set-Content -Encoding ASCII $list
& $Javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp (Join-Path $Work "root\shim") -d (Join-Path $Work "classes") "@$list"
if ($LASTEXITCODE -ne 0) { throw "Engine compile failed" }
$CP = (Join-Path $Work "classes") + ";" + (Join-Path $Work "root\shim") + ";" + (Join-Path $Repo "src")

# 3. Run.
& $Java "-Djava.awt.headless=true" -cp $CP uprzx.golden.GoldenRunner `
  --roms (Join-Path $Repo "testroms") --work $Work --profiles "tools\golden\profiles.tsv" `
  --manifest $Manifest --label $Label --engine-rev $Rev --exclude-file "tools\golden\exclude.txt" @args
exit $LASTEXITCODE
