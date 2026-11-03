# Real NAND/FatFs import regressions

Run `bash tools/host-tests/nand-fat/run.sh`. It incrementally builds the actual
core with ASan/UBSan, then links it into the test. No ROM, BIOS, firmware, NAND
dump or save fixture is required. Anonymous backing files disappear on close;
generated named input is removed after each import.

The fixture formats a 4 MiB FAT volume, constructs an authored nocash identity,
and writes the volume through NANDMount's actual encrypted disk callbacks.
Production NANDMount, crypto, FatFs and title/save creation run unchanged. The
fixture is deliberately not a bootable DSi system image.

Sixteen scenarios cross file/memory inputs, no/public/private/banner save, and
sufficient/exhausted physical space. After remount, successful imports must match
the supplied executable exactly; failed imports are explicitly deleted, then a
retry must succeed with exact bytes. Ticket/directory removal, unrelated sentinel
data, save lengths and independent FAT header constants are checked.

Three additional cases adapt only Platform file-error delivery: source read,
direct sector write and the final buffered sector's write during `f_close`.
FatFs and encryption remain real. Errors must reach the return value; unrelated
data/source bytes survive, and retries persist exact bytes after remount. The
original source falsely returns success in all three cases.

To rerun an immutable original after changing the core:

```sh
git -C melonDS-android-lib show fd758240:src/DSi_NAND.cpp > /your/evidence/DSi_NAND-original.cpp
bash tools/host-tests/nand-fat/run.sh /your/evidence/DSi_NAND-original.cpp --io-only
```

The optional original translation unit resolves NAND symbols before the archive.
Omit `--io-only` for all cases; strict UBSan stops on the original unaligned
save-header stores. `UBSAN_OPTIONS=halt_on_error=0` permits the archived core's
recoverable diagnostics to continue; a directly compiled original instead uses
`-fno-sanitize-recover=all`.

Android also builds `NandFatTest`. Optionally configure CMake with
`-DNAND_FAT_BASELINE_SOURCE=/absolute/path/DSi_NAND-original.cpp` and explicitly
build/run `NandFatBaselineTest` for Android RED evidence. Clear the option after
use. This is not a second implementation of the production algorithm.

This proves physical-space/error propagation, not HOME logical quota/boot,
JNI locking, ART behavior, transactional replacement of an existing save or
rollback after power loss. Direct core callers still own cleanup. The Android
JNI caller already deletes a new failed title; its separate boundary regressions
do not establish combined ART/real-NAND execution.
