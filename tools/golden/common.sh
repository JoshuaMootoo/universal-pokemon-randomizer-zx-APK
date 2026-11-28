# Shared setup for the golden-master scripts (sourced). Sets REPO, PINNED.
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO"
PINNED="$(cat tools/golden/BASELINE_COMMIT)"
# The tag may not exist on GitHub (cloud sessions cannot push tags), so fall back to the pinned commit.
if ! git rev-parse -q --verify refs/tags/baseline-engine >/dev/null; then
  git fetch -q origin tag baseline-engine 2>/dev/null || git tag baseline-engine "$PINNED"
fi
if [ "$(git rev-parse 'baseline-engine^{commit}')" != "$PINNED" ]; then
  echo "ERROR: tag baseline-engine does not point at tools/golden/BASELINE_COMMIT" >&2; exit 1
fi
