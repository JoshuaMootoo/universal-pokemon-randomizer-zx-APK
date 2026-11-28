# Shared setup for the golden-master scripts (dot-sourced). Sets $Repo, $Jdk, $Java, $Javac, $Pinned.
$ErrorActionPreference = "Stop"
$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Repo

# Java: first JDK that actually starts, from JAVA_HOME, javac on PATH, or Android Studio's bundled JBR.
# (On Joshua's PC "Android Studio" is a broken leftover and the real install is "Android Studio1".)
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
$onPath = Get-Command javac.exe -ErrorAction SilentlyContinue
if ($onPath) { $candidates += (Split-Path (Split-Path $onPath.Source)) }
$candidates += "C:\Program Files\Android\Android Studio1\jbr", "C:\Program Files\Android\Android Studio\jbr"
$Jdk = $null
# Windows PowerShell 5.1 turns redirected native stderr into errors under "Stop"; relax it for probing.
$ErrorActionPreference = "Continue"
foreach ($c in $candidates) {
    $jc = Join-Path $c "bin\javac.exe"
    if (Test-Path $jc) {
        & $jc -version *> $null
        if ($LASTEXITCODE -eq 0) { $Jdk = $c; break }
    }
}
$ErrorActionPreference = "Stop"
if (-not $Jdk) { throw "No working JDK found (tried: $($candidates -join '; ')). Set JAVA_HOME to a JDK 17 or newer." }
$Java = Join-Path $Jdk "bin\java.exe"
$Javac = Join-Path $Jdk "bin\javac.exe"
Write-Host "Using JDK: $Jdk"
& $Java -version

# The tag may not exist on GitHub (cloud sessions cannot push tags), so fall back to the pinned commit.
$Pinned = (Get-Content (Join-Path $Repo "tools\golden\BASELINE_COMMIT")).Trim()
$ErrorActionPreference = "Continue"
git rev-parse -q --verify refs/tags/baseline-engine *> $null
if ($LASTEXITCODE -ne 0) {
    git fetch -q origin tag baseline-engine *> $null
    if ($LASTEXITCODE -ne 0) { git tag baseline-engine $Pinned }
}
$ErrorActionPreference = "Stop"
if ((git rev-parse "baseline-engine^{commit}").Trim() -ne $Pinned) { throw "Tag baseline-engine does not point at tools\golden\BASELINE_COMMIT ($Pinned)" }

function Write-SourceList($Paths, $ListFile) {
    ($Paths | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }) | Set-Content -Encoding ASCII $ListFile
}
