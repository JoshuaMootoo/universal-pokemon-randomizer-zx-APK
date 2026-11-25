#!/usr/bin/env bash
# Golden-master baseline run (BUILD_SPEC 7.1), Linux/macOS version of run-baseline.ps1.
# Usage: tools/golden/run-baseline.sh [extra GoldenRunner options, e.g. --gens 4,5 --only typical]
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
WORK="${GOLDEN_WORK:-$REPO/testroms/golden-work}"
LABEL="${GOLDEN_LABEL:-baseline}"
MANIFEST="${GOLDEN_MANIFEST:-$REPO/tools/golden/manifests/$LABEL.tsv}"

# 1. The baseline must be the untouched engine.
git rev-parse -q --verify refs/tags/baseline-engine >/dev/null || git fetch -q origin tag baseline-engine
if ! git diff --quiet baseline-engine -- src || [ -n "$(git status --porcelain -- src)" ]; then
  echo "ERROR: src/ differs from tag baseline-engine. The baseline must run on the untouched engine." >&2
  exit 1
fi
REV="$(git rev-parse baseline-engine^{commit})"

# 2. Compile: shim (sets ROOT_PATH = $WORK/root/), then engine minus GUI/launcher, plus the harness. UTF-8 is required.
rm -rf "$WORK/root" "$WORK/classes"
mkdir -p "$WORK/root/shim" "$WORK/classes"
javac -encoding UTF-8 --release 17 -nowarn -d "$WORK/root/shim" tools/golden/shim/com/dabomstew/pkrandom/newgui/NewRandomizerGUI.java
find src -name '*.java' -not -path 'src/com/dabomstew/pkrandom/newgui/*' -not -path 'src/launcher/*' > "$WORK/sources.txt"
find tools/golden/java -name '*.java' >> "$WORK/sources.txt"
javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp "$WORK/root/shim" -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 | grep -v '^Note:' || true
CP="$WORK/classes:$WORK/root/shim:$REPO/src"

if [ "${1:-}" = "--regenerate-profiles" ]; then
  shift
  java -Djava.awt.headless=true -cp "$CP" uprzx.golden.Profiles tools/golden/profiles.tsv
fi

# 3. Run.
java -Djava.awt.headless=true -cp "$CP" uprzx.golden.GoldenRunner \
  --roms "$REPO/testroms" --work "$WORK" --profiles tools/golden/profiles.tsv \
  --manifest "$MANIFEST" --label "$LABEL" --engine-rev "$REV" \
  --exclude-file tools/golden/exclude.txt "$@"
