# Golden-master run against :core (the Android engine library), compared with the baseline (BUILD_SPEC 7.1).
#   powershell -ExecutionPolicy Bypass -File tools\golden\run-core.ps1 [--gens 4,5] [--only typical] [...]
# Builds core\build\libs\core.jar with Gradle, runs every case twice plus consecutively in one JVM, writes
# tools\golden\manifests\core.tsv and fails if any case differs from tools\golden\manifests\baseline.tsv.
. (Join-Path $PSScriptRoot "common.ps1")
$Work = Join-Path $Repo "testroms\golden-work\core"
$Label = "core"
$Manifest = Join-Path $Repo "tools\golden\manifests\$Label.tsv"

# 1. Build :core with the Gradle wrapper, using the JDK found above.
$env:JAVA_HOME = $Jdk
& (Join-Path $Repo "gradlew.bat") ":core:jar" "--console=plain" "-q"
if ($LASTEXITCODE -ne 0) { throw "Gradle build of :core failed" }
$CoreJar = Join-Path $Repo "core\build\libs\core.jar"
$Rev = (git rev-parse HEAD).Trim()
$dirty = git status --porcelain -- src core
if ($dirty) { $Rev = "$Rev+uncommitted" }

# 2. Compile the harness against core.jar.
$Classes = Join-Path $Work "harness-classes"
if (Test-Path $Classes) { Remove-Item -Recurse -Force $Classes }
New-Item -ItemType Directory -Force $Classes | Out-Null
$list = Join-Path $Work "sources.txt"
Write-SourceList (Get-ChildItem -Recurse -Filter *.java tools\golden\java) $list
& $Javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp $CoreJar -d $Classes "@$list"
if ($LASTEXITCODE -ne 0) { throw "Harness compile failed" }
$CP = $CoreJar + ";" + $Classes

# 3. Run and compare.
& $Java "-Djava.awt.headless=true" -cp $CP uprzx.golden.GoldenRunner `
  --roms (Join-Path $Repo "testroms") --work $Work --profiles "tools\golden\profiles.tsv" `
  --manifest $Manifest --label $Label --engine-rev $Rev --exclude-file "tools\golden\exclude.txt" `
  --compare-to "tools\golden\manifests\baseline.tsv" @args
exit $LASTEXITCODE
