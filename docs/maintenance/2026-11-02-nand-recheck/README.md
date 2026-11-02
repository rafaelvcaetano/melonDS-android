# NAND replacement/import recheck

Date: 2 November 2026. Unit state: **closed at Android native component
level; no new correction**.

## Identity and contract

- App integration base: `fd40a2bf`, whose gitlink selects core `f76940fc`.
- Consolidated NAND fixes under test remain core commit `403c4683`
  (`Preserve DSi NAND data across failed imports`) and app commit `1cc24ead`
  (`Update core for reliable DSi NAND imports`). Neither was amended or
  reapplied.
- Input: only authored/synthetic fixtures embedded by the four existing tests.
  No personal NAND, firmware, BIOS, save, database, APK installation or
  physical device was used.
- Runtime: isolated read-only API35 ARM64 AVDs, first 4 KiB and then 16 KiB;
  one frontier at a time. Each runner used and removed its own random
  `/data/local/tmp/melonds-core-tests.*` directory.
- Expected: JNI title enumeration has bounded local refs; invalid/missing/short
  inputs fail without partial title state; successful imports persist exact
  bytes; failed existing-save replacements retain the old generation and allow
  retry/recovery.

## Commands

```text
./tools/android-tests/build.sh --abi arm64-v8a
./tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test NandJniTest
./tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test StorageNandTest
./tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test NandFatTest
./tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test ExistingSaveReplacementTest
# identical four commands on emulator-5556
```

## Results and controls

| Target | NandJni | StorageNand | NandFat | ExistingSaveReplacement |
| --- | --- | --- | --- | --- |
| API35 ARM64, 4096-byte pages | PASS, exit 0, local-ref peak 6 at 1500 titles | PASS, exit 0 | PASS, 0 failures | PASS, 0 failures |
| API35 ARM64, 16384-byte pages | PASS, exit 0, local-ref peak 6 at 1500 titles | PASS, exit 0 | PASS, 0 failures | PASS, 0 failures |

Controls include missing NAND, cleanup after failed import, metadata mismatch,
short metadata/header, full-space executable/save cases, normal import,
existing-save preservation and retry. A post-run enumeration on both AVDs
found no residual runner directory before shutdown.

The fresh host suite in the companion repository campaign also passes
`nand-fat` and `ExistingSaveReplacementTest`. Host results support the shared
core but are not Android execution.

## Artifacts and limits

- `NandJniTest` ELF SHA-256:
  `d1d6ecadfc9af6902c0f7f10e2c9f0ef0e621fa76b6d3a7980e149942e2fe059`.
- `StorageNandTest` ELF SHA-256:
  `61e7c113808d007b7bd021562458b28809e9e0b83b445eaaf5842dfb567d1f02`.
- `NandFatTest` ELF SHA-256:
  `0cd2014d86bdacb5418dcf4c0e250ebc4b9490e77bfe35c68e78b734ef20b439`.
- `ExistingSaveReplacementTest` ELF SHA-256:
  `6f437d272e5dfe35500702117f96914c4c4fde2611c386dbb8326415d8893375`.
- Private receipt:
  `local/reports/ds-2026-11-02/nand-recheck/receipt.json`.

This is not ART/SAF validation: instrumentation would require installing an
APK, forbidden by the mandate. Faults during FatFs rename, rollback metadata
write and backup cleanup are not injected by these cases. Concurrent close,
task removal/`onCleared`, usage count, DSi HOME boot and logical quota remain
open. These omissions are SKIP/BLOCKED by scope or missing fixture, never PASS.
