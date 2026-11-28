# Engine changes

Every change to the reused Java engine in `src/` (outside `newgui/` and `launcher/`) is listed here. Baseline: tag `baseline-engine` = commit `7f00eb8` (upstream v4.6.1), pinned in `tools/golden/BASELINE_COMMIT`. Each changed file carries a dated "Modified ... for UPR ZX for Android (unofficial)" note under its licence header. Line endings of each file are preserved (some engine files are CRLF, some LF).

`git diff --stat baseline-engine -- src` (2026-11-28): 18 files, 700 insertions, 91 deletions.

| # | Files | Spec item | Change and reason | Verified by |
|---|---|---|---|---|
| 1 | `Utils.java`, `SysConstants.java` | 4.1 + 4.2 | Removed the `NewRandomizerGUI` import and `getExecutionLocation()`. `ROOT_PATH` comes from system property `uprzx.rootPath`; it throws if unset or not a writable folder (no silent `./` fallback, which made DS/3DS extraction stay in RAM). Call sites unchanged. | Manual check: unset, missing folder and valid folder (2026-11-28). Golden run: pending. |
| 2 | `PixelImage.java` (new), `GFXFunctions.java`, `ctr/BFLIM.java`, `romhandlers/RomHandler.java`, `Gen1`–`Gen7RomHandler.java` | 4.3 | `java.awt` `BufferedImage`/`Graphics` replaced by `PixelImage` (ARGB `int[]`, `getRGB`/`setRGB`, unscaled `drawImage` with SrcOver). Android has no `java.awt`. Random-number call order unchanged. | `PixelImageTest`: Gen 5 and Gen 6 mascot draw sequences (incl. out-of-bounds) and 2000 random rectangles match `java.awt` pixel for pixel. `core.jar` has no class referencing `java/awt` or `javax/swing`. Mascot pixels vs desktop: pending (`--mascot` runs). |
| 3 | `cuecompressors/BLZCoder.java` | 4.2a (approved 2026-11-25) | `EXIT()` throws `IllegalStateException` instead of `System.exit(0)`, which would kill the app on a malformed DS file. | `BlzCoderTest`. |
| 4 | `ctr/NCCH.java`, `ctr/RomfsFile.java` | 5.3, memory change **M1** (approved 2026-11-28) | `rebuildRomfs` copies unchanged romfs files in 1 MB chunks and streams changed files from their temp file; LayeredFS output streams too. Gen 6 ran out of memory there at a 512 MB heap. | Golden run (Gen 6 outputs identical) and 512 MB memory run: pending. |
| 5 | `ctr/GARCArchive.java`, `ctr/RomfsFile.java`, `ctr/NCCH.java`, `romhandlers/Abstract3DSRomHandler.java` | 5.3, memory change **M2** (approved 2026-11-28) | `readGARC(subpath, compressThese)` (only used for the Gen 7 encounter archive) is disk-backed: the file is extracted in chunks (same temp file, status and `originalCRC`, so ROM diagnostics in the log are unchanged), only the directories in `compressThese` are loaded, the rest are read from disk when written. `writeGARC` writes it back through a file with `GARCArchive.writeTo`, which reproduces `getBytes()` exactly. All other archives keep the original in-memory code. | `GarcArchiveTest`: disk-backed and in-memory archives give identical files and identical written bytes, unchanged and edited, two padding sizes. Golden run (Gen 7 outputs identical) and 512 MB memory run: pending. |

Not changed (spec option not needed): 4.4 `Settings.read/write` stay on `FileInputStream`/`FileOutputStream`; the facade passes files. 4.5 temp cleanup is done in the facade (`uprzx.core.Engine`/`LoadedRom`), not in the engine.

## Known engine limits found (not changed)
- `Gen2RomHandler.writeEvosAndMovesLearnt` has no space check (Gen 1 has one); Every Level evolutions overflow it on Crystal. The desktop GUI hides Every Level for Gen 1–2.
- `BLZCoder.LZSS_Encode` overflows its output buffer on data that does not compress (found by `GarcArchiveTest` with synthetic data; game data compresses).
- `File.deleteOnExit()` registrations accumulate for the life of the process (the facade deletes the files itself).
