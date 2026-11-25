# Engine changes

Every change to the reused Java engine in `src/` (outside `newgui/` and `launcher/`) is listed here. Baseline: tag `baseline-engine` (to be created in Phase 1 at commit `7f00eb8`, upstream v4.6.1).

Changes allowed by BUILD_SPEC section 4: 4.1 remove GUI dependency, 4.2 configurable root path, 4.3 replace `java.awt`, 4.4 stream widening, 4.5 temp file cleanup. Anything else needs a written justification and Joshua's approval.

| # | File | Spec item | Reason | Golden tests after change | Date |
|---|---|---|---|---|---|
| — | *(none yet)* | | | | |

## Approved, not yet made (Joshua approved 2026-11-25; to be made in Phase 2, after `baseline-engine`)

| File | Proposal | Why |
|---|---|---|
| `src/cuecompressors/BLZCoder.java` | `EXIT()` throws an unchecked exception instead of calling `System.exit(0)` | `BLZ_Decode` is reached from `NDSRom` and `NDSY9Entry` with ROM data. On a malformed DS ROM, `System.exit` would kill the Android app with no error message. See BUILD_SPEC 4.2a. |
