# Progress

**Current phase:** 1 — Baseline harness: **complete** (2026-11-27), waiting for Joshua's approval to start Phase 2
**Last updated:** 2026-11-25
**Branch:** `claude/wizardly-wozniak-5zrlnc` (one branch for all phases)
**Where work runs:** Claude Code session on Joshua's PC, `E:\Local\Documents\universal-pokemon-randomizer-zx-APK`

## Phase 0 — finished on Joshua's PC (2026-11-25)
- Branch `claude/wizardly-wozniak-5zrlnc`, up to date with `origin` after `git fetch` (head `8f0f362` before this commit).
- ROMs in `testroms\` (directly in the folder, not in `testroms\nds\`). `git status --porcelain --ignored testroms` shows `!! testroms/`; `git ls-files testroms` is empty. Files:
  - `Pokemon - Diamond Version (USA) (Rev 5).nds` (64 MB, Gen 4)
  - `Pokemon - Platinum Version (USA) (Rev 1).nds` (128 MB, Gen 4)
  - `Pokemon - HeartGold Version (USA).nds` (128 MB, Gen 4)
  - `Pokemon - Black Version (USA, Europe) (NDSi Enhanced).nds` (256 MB, Gen 5)
  - `Pokemon - Black Version 2 (USA, Europe) (NDSi Enhanced) Randomizer.nds` (275 MB, Gen 5). **Already randomized** (Joshua confirmed 2026-11-25), so it is not a valid baseline input. Phase 1 skips it and lists Black 2 as NOT VERIFIED until Joshua supplies a clean dump. Gen 5 is covered by Black.
- Phone: Samsung Galaxy Z Fold8 Ultra, **12 GB RAM, Android 17**.
- Engine compile check on this PC (Git Bash, Temurin `javac 21.0.7`, `-encoding UTF-8`): 105 files, exactly 2 errors, both in `Utils.java` (lines 41 and 119, `newgui`). Matches the cloud result.

### Toolchain on this PC (checked 2026-11-25)
| Item | Found | Status |
|---|---|---|
| git | 2.45.2.windows.1 | OK |
| Java on PATH | Temurin 21.0.7 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot`) | OK for Phase 1 |
| Android Studio | `C:\Program Files\Android\Android Studio1` (build 253.31033.145, bundled JBR 21.0.10) | OK. `C:\Program Files\Android\Android Studio` is a broken leftover (no `product-info.json`, its JBR does not start) |
| `JAVA_HOME`, `ANDROID_HOME` | not set | Phase 2 writes `sdk.dir` in `local.properties` instead |
| Android SDK | `C:\Users\joshu\AppData\Local\Android\Sdk` | platforms 34, 35; build-tools 34.0.0, 35.0.0, 35.0.1, 36.0.0 |
| API 37 platform, build-tools 37.0.0 | **missing** | Needed before Phase 2/3, not Phase 1 |
| SDK command-line tools | **missing** | Optional, lets Claude install SDK packages from the shell |
| adb | SDK `platform-tools` 35.0.2; the `adb` on PATH is scrcpy's 36.0.0 | Two versions can fight over the adb server; update platform-tools |
| Emulator | 36.4.10, AVD `Pixel_9a`; system images up to API 36 | API 37 and API 26 images needed later (Phase 3/7.2) |

## Phase 0 — cloud session results

## Phase 1 — status (2026-11-25)
Joshua approved starting Phase 1 (phone details can follow before Phase 3). ROM upload to the cloud session was too big, so the harness runs on his PC (manual route): Claude builds and pushes, Joshua runs one command and sends back the manifest.

Done:
- Tag `baseline-engine` = `7f00eb8` (upstream v4.6.1; `src/` identical to HEAD at tagging). The cloud session could not push the tag (the push is cut off; it may only push its branch), so `tools/golden/BASELINE_COMMIT` pins the hash and the scripts create the tag locally if it is missing.
- The randomized Black 2 file is skipped through `tools/golden/exclude.txt` and recorded as NOT VERIFIED. The PowerShell script picks the first working JDK (JAVA_HOME, `javac` on PATH, `Android Studio1\jbr`, `Android Studio\jbr`).
- Harness in `tools/golden/` (see its README): shim outside `src/`, UTF-8 compile, GUI-style call sequence, 11 profiles / 12 cases per ROM, each case twice in fresh JVMs plus all cases consecutively in one JVM, manifest `tools/golden/manifests/baseline.tsv`.
- Verified here (no ROM): compiles; profiles generate and round trip; no ROMs → `SKIPPED`, exit 2; a junk `.nds`/`.gba` file → rejected as unsupported, `NOTHING VERIFIED`, exit 2; `ROOT_PATH` check passes in the child JVM.

NOT VERIFIED:
- `run-baseline.ps1` has never run (no PowerShell here). Joshua runs it first with `--only none,typical`.
- Any randomization of a real ROM. Determinism, timings, memory and temp-disk use are unknown until Joshua's run.

Results so far (Joshua's PC, 2026-11-26): smoke test PASS (12/12); first full run PASS, 48/48 deterministic, 0 differ in the same process, but `max` crashed the engine on all 4 ROMs (see Known issues). Cases took 1–11 s; peak heap up to 362 MB for normal profiles (HeartGold modes-1).

**Final baseline (commit 47afd71, Joshua's PC, Windows 11, Temurin 21.0.7, 2026-11-26):** `tools/golden/manifests/baseline.tsv`, 52 cases (Diamond, Platinum, HeartGold, Black × 12 profiles + 2nd typical seed), 52 OK, 52 deterministic in fresh JVMs, 52 identical when run consecutively in one JVM, 52 distinct output ROMs. Peak Java heap 502–750 MB (max profile), cases ≤ 8.5 s, temp extraction on disk ≤ 17 MB. **Phase 1 exit met for NDS.** NOT VERIFIED (no clean ROM supplied): Black 2, Gen 1–3, Gen 6–7.

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
2. Wants every supported generation eventually; **start with NDS** (Gen 4 and 5). Clean ROMs: Diamond, Platinum, HeartGold, Black. The Black 2 file is already randomized (see above).
3. Phone: Samsung Galaxy Z Fold8 Ultra, 12 GB RAM, Android 17.
4. PC: Windows 11, Android Studio already installed.
5. ROMs are on his PC (`E:\Local\Documents\Roms`), not in the cloud.
6. Claude may accept the Android SDK licence for him. Done: the cloud session has cmdline-tools, `platform-tools`, `platforms;android-37.0` and `build-tools;37.0.0` in `~/android-sdk`. The container is temporary, so later sessions reinstall it.
7. `BLZCoder` `System.exit` fix: approved ("fix it if it would cause problems"). Scheduled for Phase 2, because Phase 1 must run on untouched source.
8. One session branch for all phases is fine; may push the `baseline-engine` tag; may commit.

## Proposed phase reorder (Joshua asked to start with NDS)
Phase 1 records NDS manifests first. Phase 4 (first usable app) targets **Gen 4 and 5** instead of Gen 1 to 3, and Phase 6 covers Gen 1 to 3. The flow is the same; NDS adds disk extraction and memory risk earlier, which Phase 3 measures anyway.

## NOT VERIFIED
- Anything that randomizes a ROM, or runs on a phone or an emulator (starts in Phase 1 / Phase 3).
- Black 2: no clean dump available (the file in `testroms\` is a randomizer output).
- The phone connecting over `adb` (not tried yet).

## Known issues
- **Desktop engine crash found by the baseline (2026-11-26):** Evolutions "Random Every Level" combined with "follow evolutions" options makes the engine loop on evolution cycles: `AbstractRomHandler.setIllegalEvos` StackOverflowError (HeartGold, Platinum) and `copyUpEvolutionsHelper` OutOfMemoryError at a 4.6 GB heap (Black, Diamond). Deterministic. The desktop GUI prevents the combination (`enableOrDisableSubControls`, "If Evolve Every Level is enabled"); the Android option model must enforce the same rule (Phase 5), and the app must survive OutOfMemoryError (BUILD_SPEC 6.5). The `max` profile now uses Random evolutions; new profile `max-everylevel` covers Every Level with the GUI's restrictions.
- **Second full run (2026-11-26):** 52/52 deterministic; `max` failed with an NPE in `randomizeEvolutions` (alt formes allowed in evolutions) and `max-everylevel` with a StackOverflowError (Elite Four unique + rival carries starter, which the GUI disables with Every Level) and, on Platinum, starter index 505 of 494 in `Gen4RomHandler.setStarters` (starter alt formes). The engine fails on options the GUI hides for Gen 4/5 (`createSettingsFromState` stores `isSelected() && isVisible()`, 38 such options). Both max profiles now switch off alt-forme/Mega options and follow the GUI's Every Level rules. **Lesson for Phase 5:** the option model must apply the per-ROM visibility mask and enabled rules exactly, or the engine crashes.

## Handover to a session on Joshua's PC (2026-11-25, done — kept for history)
The cloud session could not reach Joshua's PC: Remote Control lists `universal-pokemon-randomizer-zx-APK` on SpartanPC as "not available" to cloud sessions, with no reason given. From here on, work runs in a Claude Code session on the PC, in `E:\Local\Documents\universal-pokemon-randomizer-zx-APK` (Windows 11, PowerShell, Android Studio installed).

First actions for that session:
1. `git pull` on branch `claude/wizardly-wozniak-5zrlnc`.
2. Confirm `testroms\nds\` holds Joshua's NDS ROMs and `git status` shows none of them.
3. Check the toolchain: `git --version`, `java -version` (Android Studio's bundled JBR is fine), `adb version`, and API 37 in the SDK Manager (`docs/SETUP.md`).
4. Get the two missing answers: phone RAM and Android version (Galaxy Z Fold8 Ultra), and the list of ROM files.
5. Then Phase 0 is complete: report it, and start Phase 1 after Joshua approves.

## Next step
Joshua approves Phase 2 (`:core` Gradle module, engine changes from BUILD_SPEC 4 incl. the approved BLZCoder fix, facade). Phase 2 exit: no `java.awt`/`javax.swing` in compiled sources, resource tests pass, and `:core` reproduces `tools/golden/manifests/baseline.tsv` exactly (run on Joshua's PC).
Before Phase 2, install in Android Studio's SDK Manager: Android API 37 SDK platform, Build-Tools 37.0.0, latest Platform-Tools and Command-line Tools.
