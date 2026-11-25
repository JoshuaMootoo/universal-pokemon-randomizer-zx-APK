# Progress

**Current phase:** 0 — Setup (in progress, waiting for Joshua's answers)
**Last updated:** 2026-11-25
**Branch:** `claude/wizardly-wozniak-5zrlnc` (cloud session branch)

## Phase 0 — what passed
- Spec checked against the code. Corrections and additions written into `BUILD_SPEC.md` (each marked with a date). Summary below.
- Engine without `newgui/` and `src/launcher/` compiles on JDK 21 with exactly 2 errors, both in `Utils.java` (`NewRandomizerGUI`). Same with `--release 17`.
- `.gitignore` updated with case-insensitive ROM, update, save, key and keystore patterns plus Gradle/Android output. Checked with `git check-ignore`; no engine resource (`.ips` patches) is newly ignored.
- Created `CLAUDE.md`, `docs/PROGRESS.md`, `docs/ENGINE_CHANGES.md`, `docs/PARITY.md`, `docs/SETUP.md`.
- Toolchain in the cloud session: OpenJDK 21.0.13, system Gradle 8.14.3 (the project will use a Gradle wrapper pinned to 9.6.x), 4 CPUs, 15 GB RAM, no `/dev/kvm`. Google's SDK repository and Maven are reachable.

## Spec review findings (2026-11-25)
Confirmed correct: version 4.6.1 / 322; ~77k lines (76,735 total, 70,843 excluding GUI and launcher; romhandlers 38,070); the `java.awt` file list; `ROOT_PATH` users; `writingEnabled` RAM fallback; `deleteOnExit` reliance; launcher `-Xmx4608M`/`-Xmx4096M`; form control counts; all named `NewRandomizerGUI` methods exist; `Bundle.properties` is loaded by `MiscTweak` and `CliRandomizer`, and passed into `Randomizer`.

Wrong or incomplete in the spec (now corrected there):
1. `BUILD_SPEC.md` was not in the repository; now added.
2. 4.1 and 4.2 must be done together: `SysConstants.getRootPath()` calls `Utils.getExecutionLocation()`.
3. `Randomizer.randomize` is an instance method needing `settings, romHandler, bundle, saveAsDirectory`.
4. CLI and desktop GUI differ: GUI calls `romHandler.setLog(...)` (extra log lines) and builds settings through its controls. The golden harness will follow the GUI.
5. `BLZCoder` calls `System.exit(0)` on a malformed DS binary; reachable from ROM loading. Proposed engine change 4.2a (needs approval).
6. Temp folder naming can collide and crashes on names without `.`; `NDSRom` needs `ROOT_PATH` to already exist. A `customnames.rncn` in `ROOT_PATH` changes the settings string (its checksum is embedded).
7. Mascot images use the unseeded main random source (desktop calls it before reseeding), so mascot pixel tests must seed first.
8. Tooling: AGP 9.5.x / Gradle 9.6.0 / JDK 17 / API 37 confirmed; Android 17 RAM-based memory limits confirmed (logged only for targetSdk ≤ 36). Foreground service type still to choose in Phase 4 (`dataSync` describes "local file processing").
9. Per-phase branches conflict with the cloud session's single allowed branch.

## NOT VERIFIED
- Android SDK install in the cloud session: not done, because installing requires accepting Google's SDK licence, which is Joshua's decision.
- Anything involving ROMs, a phone or an emulator.

## Known issues
- None in new code (there is none yet).

## Next step
Joshua answers the Phase 0 questions (app name and ID, ROMs, phone, OS, branch policy, licence acceptance, BLZ change). Then Phase 0 exit → Phase 1 (baseline harness).
