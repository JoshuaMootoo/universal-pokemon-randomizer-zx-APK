#!/usr/bin/env bash
# Golden-master baseline run (BUILD_SPEC 7.1): the UNTOUCHED engine (tag baseline-engine). Linux/macOS
# version of run-baseline.ps1. Usage: tools/golden/run-baseline.sh [--regenerate-profiles] [runner options]
source "$(dirname "$0")/common.sh"
WORK="${GOLDEN_WORK:-$REPO/testroms/golden-work}"
LABEL="${GOLDEN_LABEL:-baseline}"
MANIFEST="${GOLDEN_MANIFEST:-$REPO/tools/golden/manifests/$LABEL.tsv}"
BASE="$WORK/baseline-src"

# 1. Untouched engine source from the tag, in a git worktree (src/ itself has Android changes).
mkdir -p "$WORK"
[ -e "$BASE/.git" ] || git worktree add -q --detach "$BASE" baseline-engine
if [ "$(git -C "$BASE" rev-parse HEAD)" != "$PINNED" ] || [ -n "$(git -C "$BASE" status --porcelain)" ]; then
  echo "ERROR: $BASE is not a clean checkout of baseline-engine. Delete it, run 'git worktree prune', retry." >&2; exit 1
fi
SRC="$BASE/src"

# 2. Compile: shim (sets ROOT_PATH = $WORK/root/), then engine minus GUI/launcher, plus the harness. UTF-8 is required.
rm -rf "$WORK/root" "$WORK/classes"
mkdir -p "$WORK/root/shim" "$WORK/classes"
javac -encoding UTF-8 --release 17 -nowarn -d "$WORK/root/shim" tools/golden/shim/com/dabomstew/pkrandom/newgui/NewRandomizerGUI.java
find "$SRC" -name '*.java' -not -path '*/src/com/dabomstew/pkrandom/newgui/*' -not -path '*/src/launcher/*' > "$WORK/sources.txt"
find tools/golden/java -name '*.java' >> "$WORK/sources.txt"
javac -encoding UTF-8 --release 17 -nowarn -Xlint:none -cp "$WORK/root/shim" -d "$WORK/classes" @"$WORK/sources.txt" 2>&1 | grep -v '^Note:' || true
CP="$WORK/classes:$WORK/root/shim:$SRC"

if [ "${1:-}" = "--regenerate-profiles" ]; then
  shift
  java -Djava.awt.headless=true -cp "$CP" uprzx.golden.Profiles tools/golden/profiles.tsv
fi

# 3. Run.
java -Djava.awt.headless=true -cp "$CP" uprzx.golden.GoldenRunner \
  --roms "$REPO/testroms" --work "$WORK" --profiles tools/golden/profiles.tsv \
  --manifest "$MANIFEST" --label "$LABEL" --engine-rev "$PINNED" \
  --exclude-file tools/golden/exclude.txt "$@"
