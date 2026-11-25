# Progress

**Current phase:** 1 — Baseline harness (harness built; waiting for Joshua to run it on his PC)
**Last updated:** 2026-11-25
**Branch:** `claude/wizardly-wozniak-5zrlnc` (cloud session branch)

## Phase 1 — status (2026-11-25)
Joshua approved starting Phase 1 (phone details can follow before Phase 3). ROM upload to the cloud session was too big, so the harness runs on his PC (manual route): Claude builds and pushes, Joshua runs one command and sends back the manifest.

Done:
- Tag `baseline-engine` = `7f00eb8` (upstream v4.6.1; `src/` identical to HEAD at tagging).
- Harness in `tools/golden/` (see its README): shim outside `src/`, UTF-8 compile, GUI-style call sequence, 11 profiles / 12 cases per ROM, each case twice in fresh JVMs plus all cases consecutively in one JVM, manifest `tools/golden/manifests/baseline.tsv`.
- Verified here (no ROM): compiles; profiles generate and round trip; no ROMs → `SKIPPED`, exit 2; a junk `.nds`/`.gba` file → rejected as unsupported, `NOTHING VERIFIED`, exit 2; `ROOT_PATH` check passes in the child JVM.

NOT VERIFIED:
- `run-baseline.ps1` has never run (no PowerShell here). Joshua runs it first with `--only none,typical`.
- Any randomization of a real ROM. Determinism, timings, memory and temp-disk use are unknown until Joshua's run.

Phase 1 exit: deterministic manifests for every ROM supplied (NDS first), unavailable ones listed as NOT VERIFIED.

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

## Joshua's answers (2026-11-25)
1. App name **"UPR ZX for Android (unofficial)"**, application ID **`com.joshuamootoo.uprzx`**.
2. Wants every supported generation eventually; **start with NDS** (Gen 4 and 5). Exact ROM list still to come.
3. Phone: Samsung Galaxy Z Fold8 Ultra. RAM and Android version still to come.
4. PC: Windows 11, Android Studio already installed.
5. ROMs are on his PC (`E:\Local\Documents\Roms`), not in the cloud.
6. Claude may accept the Android SDK licence for him. Done: the cloud session has cmdline-tools, `platform-tools`, `platforms;android-37.0` and `build-tools;37.0.0` in `~/android-sdk`. The container is temporary, so later sessions reinstall it.
7. `BLZCoder` `System.exit` fix: approved ("fix it if it would cause problems"). Scheduled for Phase 2, because Phase 1 must run on untouched source.
8. One session branch for all phases is fine; may push the `baseline-engine` tag; may commit.

## Proposed phase reorder (Joshua asked to start with NDS)
Phase 1 records NDS manifests first. Phase 4 (first usable app) targets **Gen 4 and 5** instead of Gen 1 to 3, and Phase 6 covers Gen 1 to 3. The flow is the same; NDS adds disk extraction and memory risk earlier, which Phase 3 measures anyway.

## NOT VERIFIED
- Joshua's Android Studio SDK versions and `adb` on PATH.
- Anything involving ROMs, a phone or an emulator.

## Known issues
- None in new code (there is none yet).

## Handover to a session on Joshua's PC (2026-11-25)
The cloud session could not reach Joshua's PC: Remote Control lists `universal-pokemon-randomizer-zx-APK` on SpartanPC as "not available" to cloud sessions, with no reason given. From here on, work runs in a Claude Code session on the PC, in `E:\Local\Documents\universal-pokemon-randomizer-zx-APK` (Windows 11, PowerShell, Android Studio installed).

First actions for that session:
1. `git pull` on branch `claude/wizardly-wozniak-5zrlnc`.
2. Confirm `testroms\nds\` holds Joshua's NDS ROMs and `git status` shows none of them.
3. Check the toolchain: `git --version`, `java -version` (Android Studio's bundled JBR is fine), `adb version`, and API 37 in the SDK Manager (`docs/SETUP.md`).
4. Get the two missing answers: phone RAM and Android version (Galaxy Z Fold8 Ultra), and the list of ROM files.
5. Then Phase 0 is complete: report it, and start Phase 1 after Joshua approves.

## Next step
Joshua: `git pull`, run `tools\golden\run-baseline.ps1 --only none,typical` (smoke test), paste the output; then the full run and commit/push `tools/golden/manifests/baseline.tsv` (or paste it). Remote Control from this cloud session to SpartanPC did not work ("not available", no reason given).
