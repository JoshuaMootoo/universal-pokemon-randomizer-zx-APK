#!/usr/bin/env bash
# Golden-master run against :core, compared with the baseline (BUILD_SPEC 7.1). Linux/macOS version of
# run-core.ps1. Usage: tools/golden/run-core.sh [runner options]
source "$(dirname "$0")/common.sh"
WORK="${GOLDEN_WORK:-$REPO/testroms/golden-work/core}"
MANIFEST="${GOLDEN_MANIFEST:-$REPO/tools/golden/manifests/core.tsv}"
mkdir -p "$WORK"

# 1. Build :core.
./gradlew :core:jar --console=plain -q 2>&1 | grep -v '^Note:\|^Picked up' || true
CORE_JAR="$REPO/core/build/libs/core.jar"
[ -f "$CORE_JAR" ] || { echo "ERROR: Gradle build of :core failed" >&2; exit 1; }
REV="$(git rev-parse HEAD)"
[ -z "$(git status --porcelain -- src core)" ] || REV="$REV+uncommitted"

# 2. Compile the harness against core.jar.
rm -rf "$WORK/harness-classes"; mkdir -p "$WORK/harness-classes"
find tools/golden/java -name '*.java' > "$WORK/sources.txt"
javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp "$CORE_JAR" -d "$WORK/harness-classes" @"$WORK/sources.txt" 2>&1 | grep -v '^Note:' || true
CP="$CORE_JAR:$WORK/harness-classes"

# 3. Run and compare.
java -Djava.awt.headless=true -cp "$CP" uprzx.golden.GoldenRunner \
  --roms "$REPO/testroms" --work "$WORK" --profiles tools/golden/profiles.tsv \
  --manifest "$MANIFEST" --label core --engine-rev "$REV" \
  --exclude-file tools/golden/exclude.txt --compare-to tools/golden/manifests/baseline.tsv "$@"
