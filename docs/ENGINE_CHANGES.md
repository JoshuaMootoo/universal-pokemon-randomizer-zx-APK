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

## Proposed for 3DS memory, awaiting Joshua's decision (2026-11-28)
Evidence: Joshua's phone caps the Java heap at 512 MB (`dalvik.vm.heapsize`); at `-Xmx512M` every 3DS game runs out of memory on desktop Java (`tools/golden/manifests/mem-512.tsv`). Each change must keep every baseline hash identical.

| # | File | Proposal | Expected effect |
|---|---|---|---|
| M1 | `src/com/dabomstew/pkrandom/ctr/NCCH.java` (`rebuildRomfs`) | Copy unchanged romfs files from the source ROM to the output in fixed-size chunks instead of one `byte[]` per file. | Removes the Gen 6 failure point. Small, local change. Other Gen 6 peaks may then show up and need measuring. |
| M2 | `src/com/dabomstew/pkrandom/ctr/GARCArchive.java`, `romhandlers/Abstract3DSRomHandler.java` (`readGARC`/`writeGARC`) and possibly `ctr/RomfsFile.java` | Keep GARC sub-files the randomizer does not touch on disk (offsets into the extracted file) instead of in the heap, and write a rebuilt GARC straight to its temp file instead of through a growing `ByteArrayOutputStream`. | Targets the Gen 7 failure while loading and saving. Larger change across the 3DS file layer; needs profiling first. |
