# NAND cartridge guest read span experiment — 2026-11-03

## Fixed experiment before execution

- Frontier: Android ROM load → core `CartRetailNAND` for a NAND-save title → authenticated cartridge B2 window and B7 guest read → guest transfer buffer. The Action Replay unit is closed and pushed.
- Initial app/core `main`: `1806ed1f` / `4e989e4634073950cb048f5c5b3df94fcb41342d`, with prior commits pushed to fork `main`. Preserve all unrelated local edits.
- Source: canceled Daybreak Blue scan `nds-nand-cart-oob-read`, a static lead. The [Android core upstream](https://github.com/rafaelvcaetano/melonDS-android-lib/blob/master/src/NDSCart.cpp) blob `bfc07f9394c2ad4a985ff9281be9fb5341f0bb09` contains the same start-only guard and `memcpy(..., len)`. Shared-core master moved the class to [CartRetailNAND.cpp](https://github.com/melonDS-emu/melonDS/blob/master/src/NDSCart/CartRetailNAND.cpp), blob `19346faa56fc11ed61c5078b11770ddd3d653908`, and now masks save offsets before per-word reads. Targeted official issue/PR searches for this exact out-of-bounds report found no matching ticket; the broader upstream cartridge refactor is reconciled below.
- Authored inputs only: 128 KiB ROM with a NAND base header, 128 KiB save buffer filled by a fixed pattern, B2 window at save base, B7 read starting 16 bytes before the end with length 256; controls fully inside save, invalid window and address outside save. No personal NAND, ROM, firmware, BIOS, save or APK is opened.
- Host Apple Clang C++17, ASan/UBSan on the actual `NDSCart.cpp` translation unit, linked to the retained host core archive. The public `SetupDirectBoot` method establishes the data-command state; the public B2/B7 handlers, save allocation and guest transfer are production code. If the host contract is proved, compile the same cart source for Android NDK API35 ARM64 and execute on isolated 4 KiB/16 KiB AVDs.
- Budget: 45 minutes from 07:55 CET, at most 50 MiB new output; initial free space about 17 GiB, floor 10 GiB. Predicted host executable about 21 MiB, NDK executable under 5 MiB, bounded logs. No full APK build within this unit. Stop when the contract is proved/confuted, budget is exhausted or a real prerequisite fails. SKIP/BLOCKED is never PASS.

## Contract and planned confutations

For a B7 read in the active NAND save window, the cart may return only bytes within the save allocation. Bytes beyond its end must remain the existing `0xFF` fill. The reported first wrong transition is `CartRetailNAND::ROMCommandStart` accepting a start address within the window and copying the full guest transfer length without comparing it to remaining save bytes. A complete in-range read must retain exact pattern bytes.

The experiment will distinguish a true source over-read from a too-small destination, invalid window, and out-of-save starting address. The latter three are independent negative controls. Public direct boot establishes the same mode normally reached through cartridge protocol; the result will be labeled component evidence until full guest command and Android runtime paths are separately exercised.

## Red reproduction and controls before product edit

The first harness placed the large `NDS` owner on the host stack and ASan reported stack overflow before any B2/B7 command. Moving only that owner to the heap removed the bench failure. The component uses a generated 128 KiB ROM, an 8 MiB NAND-save allocation selected by `SaveMemType=8`, fixed save bytes, public `CartCommon::SetupDirectBoot`, B2 and B7. `NDS` is constructed but never reset or run; the host sandbox emits a nonfatal `shm_open`/`ftruncate` warning, while the exact valid and invalid-window controls complete.

Before changing core source, four controls exit 0: a 32-byte in-range read reproduces exact authored save bytes; an invalid B2 window returns `0xFF`; a B7 address starting beyond the save returns `0xFF`; and a 16-byte read ending exactly at the allocation boundary returns exact final bytes. The near-end 256-byte read has a correctly sized destination and begins at the save's last 16 bytes. It aborts under ASan with `heap-buffer-overflow READ of size 256` in `CartRetailNAND::ROMCommandStart`, reading immediately after the 8,388,608-byte save allocation. No guest-visible host bytes were collected. The first incorrect transition is copying `len` after checking only the source start and window.

Source SHA-256 before `cef87705767ae1fe4642e7f7ef454c96a9a59fb2a199c348619359e62524dfe8`; test source `a293bf6da84dab199aa1af4b15acf67ec02fbc50c42f1abbf0db10faaceb26f8`; runner `f41ac6208e48dce163b30d9e76eb2eb1cfb72bc901bb262779dde99059bfe7e8`. Raw files under `local/reports/ds-2026-11-03/nand-cart-read/`: `pre-status.csv` SHA-256 `06f1ba94a649d01088e031da486a1cd6ef32d1b87b946a215096178b29ccd4a7`; `pre-near-end.log` `19853eb61932507ce4af5623b9a0c1520ae712309244b9693a3d523bfa060753`; control logs retained individually. Proof level: host ASan on real cart code through public data-mode setup and command handlers. Android execution pending.

### Hardware-contract correction before final acceptance

The initial plan inferred that the cart would leave `0xFF` after the save's last byte, based on the old handler's default fill. Further official-source review found [melonDS-emu/melonDS PR #2595](https://github.com/melonDS-emu/melonDS/pull/2595), authored by Arisotura and merged 8 March 2026 as commit `1db5729f45616fba712f9b72bd05a59746b90a0b`. Its replacement `src/NDSCart/CartRetailNAND.cpp` uses `SRAMRead32`, masking the starting save offset by `SRAMLength - 1` and wrapping the low twelve address bits within the current 4 KiB page after each word. KostaSaizo7's PR comment concerns a separate game bug (#380), and Arisotura's cleanup comment does not claim this bounds defect specifically. The upstream code is a broader cartridge refactor, not an Android-fork patch to apply wholesale.

This evidence supersedes the initial `0xFF` prediction for a **valid** near-end window crossing. The corrected output oracle must require the remaining 16 final save bytes followed by 240 bytes from the beginning of that last 4 KiB page, with no read outside the allocation; an invalid window or start still returns `0xFF`. The first local bounded-copy implementation passed the memory check, but a strengthened interim oracle failed at byte 16 (`FF != 40`). Rechecking `SRAMRead32` clarified that the destination is the beginning of the **same 4 KiB page**, not the beginning of the whole save. The final test gives that page start a distinct pattern and also checks a separate mid-save page crossing. Neither interim implementation was committed or accepted. The original red logs and initial hypothesis remain above for auditability.

## Final correction, verification and limits

The local `CartRetailNAND::ROMCommandStart` B7 SRAM branch now requires the guest start to fall inside the save allocation and copies bounded chunks within the selected 4 KiB NAND page. Crossing the page end resumes at that page's start, matching the upstream `SRAMRead32` address behavior without importing PR #2595's broad cart API refactor. The old `0xFF` fill remains for invalid windows or starts. The in-range read, invalid window, outside-start, mid-save page wrap, exact final 16 bytes and near-end 256-byte transfer all pass on the same host ASan/UBSan component. The previously red near-end read is green and exact, with the final 240 bytes checked against a distinct last-page pattern.

The actual patched `NDSCart.cpp` translation unit was cross-compiled with Android API35 ARM64 UBSan and linked to the existing app-core archive. The same six modes pass on isolated 4 KiB and 16 KiB AVDs, with SDK/ABI/page size verified; each remote executable was hash-checked against the canonical local ELF, removed under an exact manifest and its AVD stopped. A nonfatal synthetic setup `ftruncate` warning appears in Android logs, while all output checks complete. An initial AVD runner invocation before the 16 KiB device appeared in ADB failed at boot polling; the runner was corrected to wait for this transient state and the test then passed. This is a bench startup error, not a cart result.

Host proof is ASan RED/GREEN with exact data controls. Android proof is **NDK target-source EXECUTED** on both page sizes, not a rebuilt app-core archive, ART, full APK or a running NAND game. The test constructs a real `CartRetailNAND` and uses public `SetupDirectBoot` and B2/B7 handlers; it does not run a guest CPU or prove which Android ROM title selects the type. App JVM/Robolectric, lint, full APK assembly, ART/instrumentation, TSan, real guest NAND behavior and physical device are SKIP for this unit. The earlier APK predates this core change; no APK was installed. No personal ROM, save, NAND, firmware or BIOS was accessed.

### Reproduction commands

```sh
bash tools/host-tests/cart/run-nand.sh
bash tools/host-tests/cart/build-android.sh

# Start each named diagnostic AVD without a snapshot, one at a time, then run:
python3 tools/host-tests/cart/run-android-component.py --serial emulator-5554 --pagesize 4096
python3 tools/host-tests/cart/run-android-component.py --serial emulator-5556 --pagesize 16384
# The runner checks identity and all six cases, hashes its remote ELF, writes a
# deletion manifest, then removes only its unique /data/local/tmp copy.
# Stop each named AVD after its run. No app or APK is installed.
```

Final source SHA-256 `598c36d2ad9b61bae898d90aae4b2cf4ff8ca9b78cb149308d038c68e26559ae`; test `ab0fb57465f342b895e7a472a17b236bc43134ece87aa5eca8608cd202ab7492`; host runner `2e4b884f65ff9bb1a2e91d87a2a04f392fd767ccd39818549f3545d28d5412b7`; Android build runner `1c589d14fe088fd7f10539192e5cba42a843bb7d57f44cc304be83db1593ec85`; Android execution runner `5cc4fe85a2b040f433cf51c0f2c22c9d8dcddaee10a622769ccbd486fbba2504`. Current host ELF 20,358,960 bytes; current Android ELF 1,201,488 bytes, SHA-256 `17b783536dc8a63c5c49c476df4f8eef985b5522748a09118cf5d555f3360924`.

| Raw file under `local/reports/ds-2026-11-03/nand-cart-read/` | SHA-256 |
| --- | --- |
| `pre-status.csv` | `06f1ba94a649d01088e031da486a1cd6ef32d1b87b946a215096178b29ccd4a7` |
| `pre-near-end.log` | `19853eb61932507ce4af5623b9a0c1520ae712309244b9693a3d523bfa060753` |
| `post-host-full.log` | `1d2d370d8ce27fe4314fa7e9e38355095befcff60dadafd0c1195d7cdc96b621` |
| `post-host-status.csv` | `912657401a4f4959a6fe91c15a2104958a49480aa8f5a5335c476b341f1e2164` |
| `post-android-4k.log` and `post-android-16k.log` | `68788c52118f1783757a8e96e90796d62df20f05687df700cef66985e59b6fba` each |
| `post-android-4k-status.csv` and `post-android-16k-status.csv` | `912657401a4f4959a6fe91c15a2104958a49480aa8f5a5335c476b341f1e2164` each |
| `cleanup-android-4k.csv` | `d86af0d17425274474f57cfd2a95b1ce9ffc405929c3d58f9d34916ba6250c55` |
| `cleanup-android-16k.csv` | `15ab2eb7c6395f69b20193ac4b1a733e5ba6e2bc90e917bc6131d42a08da0c12` |

Interim AVD logs/status/manifests were retained with `intermediate-` and `intermediate-savewrap-` prefixes, never relabeled as final PASS. The two interim Android ELF files have byte-exact gzip copies under those prefixes, with `intermediate-replacement.csv` and `intermediate-savewrap-replacement.csv` recording producer, size, hash, reason and canonical copy before overwriting the output path. This refinement repeatedly rewrote the roughly 20 MiB host executable; cumulative generated bytes **exceeded the initial 50 MiB cap**, although final retained candidate and small interim evidence occupy far less. This budget deviation is explicit; no further large build was launched, and free space remained about 16 GiB, above the 10 GiB floor. No original data or generic build directory was deleted.

Private receipt: `local/reports/ds-2026-11-03/nand-cart-read/receipt.json`; the canonical `verifiche` tool at `d8a11722f5cc0535f4d58ea6a1ab52c214797fdb` returned `PASS=1` for evidence consistency, not security certification. Local core commit `907849b3` (`Bound NAND cartridge save reads to their page`). Paired app gitlink/reproducer/report and canonical-document commits follow separately. No upstream PR was opened.
