# BUILD_SPEC: Universal Pokemon Randomizer ZX for Android

This is the build specification for Claude Code. Read the whole file before doing anything.

## 0. Mission

Turn this repository (a fork of `Ajarmar/universal-pokemon-randomizer-zx`, v4.6.1, `Version.VERSION = 322`) into an Android app, distributed as a sideloaded APK, with feature parity with the desktop program.

**"From scratch" applies to the Android app, not the randomizer engine.** The Android project, build system, UI and platform layer are all new. The existing Java engine is **reused**. Do not rewrite, translate to Kotlin, "modernise", reformat or tidy it. It is roughly 77,000 lines including the desktop GUI, much of it exact ROM offsets and byte manipulation, where a small mistake can produce a ROM that only breaks hours into a game.

**Definition of correct:** for the same input ROM, settings, custom names and seed, the Android app produces output that is **byte-identical** to the unmodified desktop engine.

## 1. How to work

The owner, Joshua, is vibe coding this: you write all the code. He knows C# and Unity and is learning ASP.NET Core. Do not assume he knows Java, Kotlin, Gradle or Android. When you need him to do something, give exact steps, and use C# comparisons where they help.

- Work through the phases in section 8 **in order**. Do one phase at a time, then stop and report.
- A phase is done only when its exit criteria pass. Show the command you ran and its result.
- Never claim something works if you have not run it. If a check needs a ROM or a physical device you do not have, say **NOT VERIFIED** and tell Joshua exactly how to verify it.
- Never weaken, skip or delete a test to make it pass. A hash mismatch is a bug to investigate.
- Commit at the end of each working step, on a branch per phase. *(2026-11-25: cloud sessions may only push to the branch the session was given; Joshua decides whether to use per-phase branches.)*
- Keep `docs/PROGRESS.md` current: phase, what passed, what is unverified, known issues, next step. Read it at the start of every session.
- If the code contradicts this spec, the code wins. Tell Joshua and update the spec.
- Check current stable versions of Android tooling in official docs before pinning them. Do not rely on memory.

### Hard rules

1. **Never commit ROMs, game updates, saves, keys or the signing keystore.** The fork is a GitHub repository. Before any ROM is placed in the working tree, add `testroms/`, `*.cxi`, `*.3ds`, `*.cia`, `*.jks`, `*.keystore` and `keystore.properties` to `.gitignore`, and cover upper and lower case ROM extensions.
2. Never download ROMs or decryption keys, or write code that fetches or decrypts them. Joshua supplies his own already-decrypted files. Keep the engine's existing encrypted-ROM rejection.
3. The project is **GPLv3**. Keep `LICENSE.txt` and all file headers, keep credits to Dabomstew, Ajarmar and contributors, mark modified files as modified with a date, and publish the matching source for any APK that is shared. Show licence and credits in the app.
4. No ads, analytics, accounts or network calls. Do not request the `INTERNET` permission.
5. Use no official Pokemon artwork or logos for the icon or branding. In-app mascot images decoded from the user's own ROM are fine.

## 2. What is in the repository

There is no Gradle or Maven build. It is a plain Java source tree.

| Path | Contents | Plan |
|---|---|---|
| `src/com/dabomstew/pkrandom/romhandlers/` | `Gen1RomHandler` to `Gen7RomHandler` and base classes, about 38k lines | Reuse |
| `src/com/dabomstew/pkrandom/Randomizer.java` | Orchestrates a run and writes the log | Reuse |
| `src/com/dabomstew/pkrandom/Settings.java`, `SettingsUpdater.java` | All options, `.rnqs` files, settings strings | Reuse |
| `src/com/dabomstew/pkrandom/{constants,pokemon,exceptions}/` | Data and models | Reuse |
| `src/com/dabomstew/pkrandom/{newnds,ctr}/` | DS and 3DS containers | Reuse, path changes |
| `src/com/dabomstew/pkrandom/{config,patches}/` | Offsets, text tables, custom names, patches, loaded as classpath resources | Package unchanged |
| `src/{compressors,cuecompressors,pptxt,thenewpoketext}/` | Compression and game text | Reuse |
| `src/com/dabomstew/pkrandom/cli/CliRandomizer.java` | Headless entry point | Best reference for the engine call sequence |
| `src/com/dabomstew/pkrandom/newgui/` | Swing GUI | Do not compile. Use as the behaviour reference |
| `src/com/dabomstew/pkrandom/newgui/Bundle.properties` | UI labels, tooltips and messages, also used by the engine | **Must be packaged** |
| `src/launcher/`, `launcher/` | Desktop JVM launcher | Do not compile |

Verified on JDK 21: with `newgui/*.java` and `src/launcher` removed, the only compile errors come from `Utils.java` importing `NewRandomizerGUI`. This does not prove that it runs on Android.

## 3. Architecture

Gradle with Kotlin DSL and a version catalog.

```
settings.gradle.kts
core/            plain JVM library, no Android dependencies
app/             Android app: Kotlin, Jetpack Compose, Material 3
src/             existing Java, left where it is
testroms/        gitignored, supplied by Joshua
docs/
```

- `:core` points its Java source set at `../src`, excluding `com/dabomstew/pkrandom/newgui/**/*.java` and `launcher/**`. Its resources also come from `../src`, excluding `**/*.java`, `**/*.form` and `launcher/**`. Confirm the built JAR contains `config/`, `patches/` and `newgui/Bundle.properties` at their original package paths. Leaving the source in place makes upstream diffs easy.
- `:core` targets Java 17 bytecode and holds a small Kotlin or Java facade, the only API `:app` should call: `inspect`, `load`, `applyGameUpdate`, `validateSettings`, `randomize(settings, seed, output, progress)`, `close`.
- `:app` uses MVVM: Compose screens, ViewModels, `StateFlow`, coroutines. Add dependencies only when needed.
- Application ID: `com.joshuamootoo.uprzx`. App name: "UPR ZX for Android (unofficial)". *(Confirmed by Joshua 2026-11-25.)*
- Suggested `minSdk 26`. Use the latest stable `compileSdk` and `targetSdk` that the chosen Android Gradle plugin supports. In late 2026, AGP 9.5 supports up to API 37 and needs Gradle 9.6.0 or later and JDK 17 or later. Re-check this.
  - *(Checked 2026-11-25 against developer.android.com.)* AGP 9.5.x (9.5.1 listed) — Gradle 9.6.0 minimum, JDK 17, SDK Build Tools 36.0.0 minimum, max API 37. The SDK repository has `platforms;android-37.0` stable and `build-tools;37.0.0`. Android's own example still applies `org.jetbrains.kotlin.android` (Kotlin 2.4.10); confirm the Kotlin plugin setup in Phase 2. `minSdk 26` matters for the engine: it uses `java.nio.file.Paths`/`Files` (API 26) and streams (API 24). Phase 2 runs Android Lint's API check over `:core` to find anything else.
  - Android 17 memory limits are based on device RAM and apply to all apps; for apps targeting API 36 or lower a violation is only logged. `adb shell am memory-limiter status` reports them. Phase 3 measures against them.
- Leave R8 minification **off** until every generation passes on a device. Resources and bundles are looked up by name.

## 4. Required engine changes

Keep these minimal. List each one in `docs/ENGINE_CHANGES.md` with the file and reason. Anything beyond this list needs a written justification.

### 4.1 Remove the GUI dependency
In `Utils.java`, remove the `NewRandomizerGUI` import and `getExecutionLocation()`.

*(Corrected 2026-11-25, Phase 0.)* `SysConstants.getRootPath()` calls `Utils.getExecutionLocation()`, so 4.1 and 4.2 must be made in the same change; 4.1 on its own does not compile.

### 4.2 Make the working root configurable
`SysConstants.ROOT_PATH` is a `static final` derived from the location of the desktop JAR. It is used by `FileFunctions` (custom names overrides), `NDSRom`, `NCCH` and `BatchRandomizationSettings`. Replace it with a value the host sets once at startup, **before** anything reads it, pointing to app-private storage. Fail loudly if it is unset.

This is critical. If the fallback `./` is not writable, `NDSRom` and `NCCH` set `writingEnabled = false` and keep extracted files **in RAM**, which is likely to cause out-of-memory crashes. Test that temp extraction really goes to disk.

*(Added 2026-11-25, Phase 0.)* Also: `NDSRom` uses `mkdir()` (not `mkdirs()`), so `ROOT_PATH` must already exist and end with a separator. The temp folder is `ROOT_PATH + "tmp_" + <filename without extension, non-`[A-Za-z0-9_]` removed>`, so different ROM names can map to the same folder (for example `Pokémon B.nds` and `Pokmon B.nds`), and a name with no `.` throws. `ROOT_PATH/customnames.rncn`, if present, overrides the bundled custom names **and its checksum is written into the settings string**, so the host controls which custom names file is in `ROOT_PATH`.

### 4.2a Stop `System.exit` in the BLZ decoder *(added 2026-11-25, Phase 0; approved by Joshua 2026-11-25)*
`cuecompressors/BLZCoder.BLZ_Decode` calls `EXIT()`, which calls `System.exit(0)`, on a bad header or length. It is reached from `NDSRom` (ARM9) and `NDSY9Entry` (overlays) with ROM data, so a malformed or modified DS ROM would silently kill the Android app. Proposed minimal change: make `EXIT` throw an unchecked exception instead. Desktop behaviour for valid ROMs is unchanged; golden tests must still pass.

### 4.3 Replace `java.awt`
Android has no `java.awt`. These engine files use it, for mascot and sprite images: `GFXFunctions`, `ctr/BFLIM`, `RomHandler`, and `Gen1RomHandler` to `Gen7RomHandler`.

- Add a platform-neutral `PixelImage(width, height, argb: IntArray)` with `getRGB` and `setRGB`.
- Change `getMascotImage()` and the drawing helpers to return it.
- Gen 5 and Gen 6 also use `Graphics.drawImage` to rearrange tiles. Add a tested rectangle copy with the same source and destination coordinate behaviour, including transparency and out-of-bounds regions.
- `:app` converts `PixelImage` to an Android `Bitmap`.
- Preserve the order of random number calls. Some `getMascotImage()` implementations consume randomness.
- *(Added 2026-11-25, Phase 0.)* Desktop calls `getMascotImage()` only after loading, before `Randomizer.randomize` reseeds `RandomSource`, so it cannot change the ROM output. It does use the unseeded main random source, so the mascot differs between runs; a mascot pixel test must call `RandomSource.seed(x)` first. Gen 6 imports `java.awt.*` (wildcard), so check it for other AWT types, not only `Graphics`.

### 4.4 Streams
`Settings.read(FileInputStream)` and `Settings.write(FileOutputStream)` may be widened to `InputStream` and `OutputStream`. Check partial-read handling if the stream type changes.

*(Confirmed 2026-11-25.)* `Settings.read` reads the version and length with single `in.read(byte[4])` calls and treats fewer than 4 bytes as an error. That is safe for a local file but not for every `InputStream`; either keep passing a `FileInputStream` of the working copy, or read those 8 bytes fully.

### 4.5 Temp file cleanup
The engine relies on `deleteOnExit()`, which cannot be relied on in an Android process. Close the ROM and delete its working directory after success, failure and cancellation. Also sweep stale directories at app start.

## 5. Android platform layer

### 5.1 Files
The engine needs real paths and `RandomAccessFile`. Android's file picker returns `content://` URIs. Therefore:

1. Pick with the Storage Access Framework. Do **not** request `MANAGE_EXTERNAL_STORAGE`.
2. Stream the input into an app-private, non-backed-up working directory. Never load a whole file into memory just to copy it. Preserve the display name and extension, sanitised: `NDSRom` and `NCCH` derive temp folder names from the filename and assume it has an extension.
3. Randomize into a separate output path in the working directory.
4. Stream the result to a destination the user picks, then delete the working copies.

Before copying, check free space for the input copy, temp extraction, local output and exported copy. Start with a conservative requirement of four times the input size (plus the update, if any), then tune from measurements. Never modify or overwrite the source ROM. Report a failed or partial export clearly. Exclude ROMs from backup.

For 3DS LayeredFS output the engine writes a directory tree under a title ID folder. Offer export to a chosen folder (`OpenDocumentTree`) or as a ZIP. `NCCH.saveAsLayeredFS` deletes the contents of an existing title folder, so the engine must only ever write LayeredFS into a fresh working directory.

### 5.2 Running jobs
- `RandomSource` is global static state and ROM handlers are mutable. Allow **one** engine job at a time, on a single dedicated background dispatcher.
- After a successful run the handler has been modified. Reload the ROM from the clean copy before another run, as desktop `reinitializeRomHandler` does.
- Run long saves and batches in a foreground service with a progress notification. Check the current rules for service types and notification permission.
- Do not invent percentages. Show the current stage with an indeterminate indicator. Batch progress can show "3 of 10".
- Do not allow cancel in the middle of a write unless it is proven safe. For batches, cancel between ROMs.
- Handle rotation, backgrounding and process death. An interrupted job must never be reported as successful.

### 5.3 Memory: the main project risk
The desktop launcher scripts run Java with `-Xmx4608M` (`Launcher.java` uses `-Xmx4096M`). An Android app gets a far smaller Java heap. Android 17 also introduces per-app memory limits.

Enable `android:largeHeap="true"`, but do not assume it is enough. Measure peak Java heap and total process memory per generation (Phase 3). If a generation does not fit:

1. Profile to find the large allocations.
2. Propose targeted streaming changes, mainly in `ctr/` and `newnds/`, protected by golden tests.
3. Report to Joshua with the numbers before starting a large refactor. Shipping Gen 1 to 5 first and marking 3DS as unsupported is an acceptable outcome. A crashing or corrupting 3DS mode is not.

## 6. Features and UI

### 6.1 Sources of truth
Read these in `NewRandomizerGUI.java` before building any UI. They contain rules that exist nowhere else:

- `romLoaded()`: which options exist for the loaded game
- `initialState()`, `enableOrDisableSubControls()`: defaults and dependencies between controls
- `createSettingsFromState()`, `restoreStateFromSettings()`: mapping between UI and `Settings`
- `loadROM()`, `saveROM()`, `saveRandomizedRom()`, `performRandomization()`, `reinitializeRomHandler()`: workflow and validation
- `populateDropdowns()`, and the game update methods
- `NewRandomizerGUI.form`: layout and grouping

The form declares 127 checkboxes, 67 radio buttons, 10 sliders, 7 combo boxes and 4 spinners. Some are not randomization settings. Miscellaneous tweaks are also defined in `MiscTweak.java`, so check how each one reaches the UI.

### 6.2 Option model
Do not hand-write hundreds of disconnected composables. Build a typed, declarative option model in `:core` or a pure Kotlin layer: ID, section, `Bundle.properties` label and tooltip keys, control type, range, visibility per ROM, enabled condition, and getter and setter on `Settings`. Compose renders it. Unit test the dependency rules. Reuse the bundle text, stripping any Swing HTML safely.

Create `docs/PARITY.md` listing **every** control from the form and every dynamically created one, each marked `done`, `not applicable (reason)` or `todo`. Add a script or test that fails when a control is missing from the list.

### 6.3 Screens
- **Home:** open ROM, ROM info (name, code, support status, mascot), apply or remove game update, load settings.
- **Options:** the eight desktop categories: Pokemon Traits; Starters, Statics & Trades; Moves & Movesets; Foe Pokemon; Wild Pokemon; TMs/HMs & Tutors; Items; Misc. Tweaks. Include the general options. Add search. Long-press or an info button shows the tooltip.
- **Randomize:** summary, warnings, output type, progress, then export the ROM and optionally the log.
- **Settings and about:** custom names editor, app preferences, version, licence, credits.

Design for phones first: one column, large touch targets, a ROM and Randomize bar that stays reachable, dark mode. Unsupported options are hidden or disabled exactly when desktop does so.

### 6.4 Parity list
- Load and validate ROMs, with specific errors for ZIP, RAR, IPS, too short, unreadable, unsupported and encrypted files
- Warnings for unrecognised or modified ROMs (`isRomValid`) and ROM diagnostics
- All randomization options and misc tweaks
- Load and save `.rnqs`, including upgrades of old versions and the `tweakForRom` warnings, interchangeable with desktop
- Premade seed and settings strings, and `.rndp` presets (match `PresetMakeDialog` and `PresetLoadDialog`, including ROM and custom names checks)
- Limit Pokemon dialog (`NewGenerationLimitDialog`)
- Custom names editor with import and export of `customnames.rncn`
- Race mode and check value, with the desktop restrictions
- Batch randomization (`BatchRandomizationSettings` and its dialog)
- Log export, matching desktop encoding including the UTF-8 BOM
- 3DS game updates, remembered per ROM code (desktop uses `config.ini`; the app must keep its own persistent copy of the file or a persisted URI permission), and LayeredFS output
- The equivalent of the desktop "unload game on success" preference

Out of scope: auto-update, the desktop launcher, desktop file choosers, and migration of the very old custom names text files unless Joshua asks.

### 6.5 Errors
Catch engine exceptions at the facade and map them to typed results with a plain message and expandable details. Handle `OutOfMemoryError` separately: discard the handler and partial output, and recommend restarting the app. Offer a shareable error report with app version, device, Android version, generation, seed, settings string and stack trace. Never include ROM contents or full file paths.

## 7. Testing

### 7.1 Golden master (build this first)
Create a desktop JVM harness **before changing any engine code**, built against the untouched source at the baseline commit (tag it `baseline-engine`). The only allowed shim is whatever is needed to compile without launching the GUI, kept outside `src/`.

`CliRandomizer` picks its own seed, so the harness must call `Randomizer.randomize(filename, log, seed)` directly, following the setup in `CliRandomizer` (custom names, `tweakForRom`, update handling).

*(Corrected 2026-11-25, Phase 0.)* `randomize` is an instance method: `new Randomizer(settings, romHandler, bundle, saveAsDirectory).randomize(filename, log, seed)`, where `bundle` is `ResourceBundle.getBundle("com/dabomstew/pkrandom/newgui/Bundle")`. The CLI and the desktop GUI differ in ways that matter here; the harness follows the **GUI** (`NewRandomizerGUI.performRandomization`), because that is what desktop users run:
- The GUI calls `romHandler.setLog(verboseLog)` before randomizing; the CLI does not. Handler messages (for example patch failures) appear only in the GUI log.
- The GUI calls `RandomSource.seed(seed)` before building settings, and `randomize` reseeds with the same value.
- The GUI builds `Settings` from its controls (`restoreStateFromSettings` then `createSettingsFromState`), not straight from the file. The harness uses `Settings.read` + `tweakForRom` and records the resulting settings string; Phase 5 checks that the Android option model produces the same string.
- Log lines use `line.separator` (`\r\n` on Windows, `\n` on Android), which the log normalisation already ignores.

For each case record: input SHA-256, settings string, seed, custom names SHA-256, output SHA-256 (per file for LayeredFS), check value and normalised log (ignore elapsed time and line ending differences; justify any other exclusion).

- First run the baseline **twice** per case in fresh processes. If the results differ, investigate before using them as a reference.
- Use several settings profiles: nothing changed, typical, as many options on as are compatible, and each mutually exclusive mode covered at least once. Add targeted profiles for patches and misc tweaks.
- Run the same cases against `:core` on the JVM, then as instrumented tests on Android. Also run two consecutive jobs in one process to catch leftover static state.
- Hash manifests may be committed. ROMs and outputs may not. Tests that need a missing ROM must report as **skipped**, never passed.
- A green build with all ROM tests skipped proves nothing about randomization. Say so.

### 7.2 Other tests
- Unit tests that run without ROMs: `.rnqs` and settings string round trips, option dependency rules, rectangle copy and `PixelImage`, resource packaging, filename sanitising, temp cleanup, storage checks.
- Compare mascot pixels with desktop output for the same seed.
- Compose UI tests for the main flow.
- Run the Android tests on a `minSdk` emulator and the latest API, to catch Java APIs missing from Android. A JVM compile does not catch these.
- Manual: Joshua boots each output in an emulator, starts a new game, and checks starters, an early wild encounter, a trainer battle and a save and reload.

## 8. Phases

### Phase 0: Setup
Inspect the repo and check this spec against it. Update `.gitignore`. Create a short `CLAUDE.md` (hard rules, build and test commands, pointers to this spec and `docs/PROGRESS.md`). Create the other docs. Check the JDK and Android SDK on this machine, and give Joshua exact install steps for anything missing. Confirm app name and ID. Ask which ROMs and which phone (model, RAM, Android version) he has.
**Exit:** he has answered, the toolchain is confirmed, and the docs are committed.

### Phase 1: Baseline harness
Build section 7.1 against untouched source.
**Exit:** deterministic manifests for every ROM supplied, or unavailable ones listed as NOT VERIFIED.

### Phase 2: `:core`
Gradle setup, source sets and the changes in section 4. Add the facade.
**Exit:** no `java.awt` or `javax.swing` imports in compiled sources; resource tests pass; `:core` matches the baseline.

### Phase 3: Android feasibility spike (go or no-go gate)
A bare debug app that loads a ROM, applies a `.rnqs` file with a given seed, saves and shows the hash and peak memory. Test the smallest and then the largest games available, up to Gen 7, on Joshua's physical phone. Confirm classpath resources and `ResourceBundle` load on Android, and that DS and 3DS extraction uses disk.
**Exit:** a table per generation of pass or fail, time, memory, disk use and hash match. **Stop and get Joshua's decision** about anything that failed.

### Phase 4: First usable app (Game Boy, GBC, GBA)
Real file flow, foreground work, ROM info, load `.rnqs` made on desktop, seed, randomize, export ROM and log, errors.
**Exit:** installable debug APK; Gen 1 to 3 hashes match on device; Joshua has booted one output.

### Phase 5: Full options UI
Option model, all categories, dependency rules, `.rnqs` saving, Limit Pokemon, validation.
**Exit:** no `todo` rows in `PARITY.md` for options; desktop and Android `.rnqs` files round trip with identical settings strings; for each test profile, settings built through the Android option model equal the desktop reference.

### Phase 6: Nintendo DS
Gen 4 and 5 through the full UI, with storage and memory handling.
**Exit:** golden and boot tests pass for each available game.

### Phase 7: 3DS
Gen 6 and 7, CXI output, LayeredFS and game updates, subject to the Phase 3 decision. Check each accepted input container separately.
**Exit:** golden tests pass for CXI and LayeredFS, or the limits are documented and enforced in the UI.

### Phase 8: Remaining parity
Presets, custom names, race mode, batch, preferences, mascot, accessibility.
**Exit:** every `PARITY.md` row and every item in 6.4 is done or has an approved reason.

### Phase 9: Release
Signed release APK. Walk Joshua through creating a keystore, storing it outside the repo and backing it up (losing it means later APKs cannot update the installed app). Add `versionCode` and `versionName` showing the engine version, a README with install steps and device limits, and licence notices. Only enable R8 if all tests still pass on the release build. Add CI that builds and runs the tests needing no ROMs.
**Exit:** the release APK installs cleanly on Joshua's phone and passes a smoke test, and the matching source is tagged.

## 9. End of phase report

Keep it short:
1. What now works
2. Evidence (commands and results)
3. NOT VERIFIED items, and how Joshua can check them
4. Engine files changed
5. Decisions needed
6. Next phase

## 10. Start here

Do **Phase 0 only**. Do not write app code yet.
