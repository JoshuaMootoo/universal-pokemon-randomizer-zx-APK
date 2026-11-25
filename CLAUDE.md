# CLAUDE.md

Unofficial Android port of Universal Pokemon Randomizer ZX (v4.6.1, `Version.VERSION = 322`).

**Read first, every session:** `docs/PROGRESS.md` (current phase, status, next step), then `BUILD_SPEC.md` (the full specification).

## Hard rules (summary — BUILD_SPEC.md section 1 is authoritative)
1. Never commit ROMs, game updates, saves, decryption keys, keystores or `keystore.properties`. ROMs live only in `testroms/` (gitignored). Run `git status` before every commit.
2. Never download ROMs or keys, or write code that fetches or decrypts them. Keep the engine's encrypted-ROM rejection.
3. GPLv3: keep `LICENSE.txt` and file headers, credit Dabomstew, Ajarmar and contributors, mark modified engine files with a dated note.
4. No ads, analytics, accounts, network calls or `INTERNET` permission.
5. No official Pokemon artwork or logos in the icon or branding.

## Working rules
- One phase at a time (BUILD_SPEC section 8). Stop at the end of a phase, report (section 9) and wait for Joshua's approval.
- Do not rewrite, reformat or "modernise" the Java engine in `src/`. Every engine change is listed in `docs/ENGINE_CHANGES.md` with a reason.
- Correct = byte-identical output to the unmodified desktop engine (tag `baseline-engine`) for the same ROM, settings, custom names and seed.
- Never claim something works without running it. Otherwise write **NOT VERIFIED** and say how Joshua can check it. ROM tests with no ROM report as skipped, never passed.
- Joshua knows C#/Unity, not Java/Kotlin/Gradle/Android: give exact steps and C# comparisons.

## Layout
- `src/` — existing Java engine (left in place). `newgui/` and `src/launcher/` are the Swing desktop GUI: not compiled, but the behaviour reference. `newgui/Bundle.properties` IS needed by the engine.
- `core/` (Phase 2) — Gradle JVM library built from `../src`. `app/` (Phase 3+) — Android app.
- `docs/` — PROGRESS, ENGINE_CHANGES, PARITY, SETUP.

## Commands
Until Phase 2 adds Gradle, the engine (minus GUI) can be compile-checked with:
```bash
find src -name '*.java' -not -path 'src/com/dabomstew/pkrandom/newgui/*' -not -path 'src/launcher/*' > /tmp/files.txt && javac -nowarn -d /tmp/engine-classes @/tmp/files.txt
```
(Expected at baseline: exactly 2 errors, both in `Utils.java`, from the `NewRandomizerGUI` import.)

Golden master (needs ROMs in `testroms/`; see `tools/golden/README.md`):
```bash
tools/golden/run-baseline.sh --only none,typical      # Linux/macOS smoke test
tools/golden/run-baseline.sh                          # full baseline
```
Windows: `powershell -ExecutionPolicy Bypass -File tools\golden\run-baseline.ps1 [same options]`.
Exit 0 = pass, 1 = mismatch, 2 = nothing verified (no ROMs). Compile the engine with `-encoding UTF-8` always.

Build and test commands will be added here as each phase creates them.
