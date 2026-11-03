# Action Replay payload length experiment — 2026-11-03

## Fixed experiment before execution

- Frontier: Android cheat import/enable → JNI word-vector construction → `AREngine::RunCheats` through the real ARM7 VBlank IRQ callback → emulated ARM7 RAM. Previous DSi WMI unit is closed and pushed.
- Initial app/core `main`: `5cc5eb2b1afc5ee8ae89f7656dae9a9ba11ac8bc` / `23700602ab0aac27be60db6a92f591d0f47d4dce`, both confirmed at their fork remote heads. Unrelated local edits are preserved.
- Origin: canceled Daybreak Blue scan's `native-memory-safety.action-replay-payload-bounds` lead; static source only. The official [shared core](https://github.com/melonDS-emu/melonDS/blob/master/src/AREngine.cpp) blob `abb7146b5b87eadca87fa4481c0c9c3dfdff510c` and [Android core](https://github.com/rafaelvcaetano/melonDS-android-lib/blob/master/src/AREngine.cpp) blob `2a5233cbc36d1acfa79e8b4b7051bfe715f0e834` still read E-code words without a remaining-vector bound. Targeted official issue/PR searches for Action Replay bounds and E0 length found no matching correction.
- [App issue #1264](https://github.com/rafaelvcaetano/melonDS-android/issues/1264), reported by mikicast01 on 24 December 2023 and closed 31 December 2023, concerned an Android 14 import crash. The reporter confirmed the then-current nightly worked after rafaelvcaetano identified Android 14 import changes. This is a different reported trigger; its closed state is not evidence about E-code execution.
- Authored test inputs: enabled E-code header with a declared 8-byte payload and no payload words; exact 8-byte payload control; disabled identical code; one-word and empty vectors; conditional skip. Destination is authored emulated ARM7 RAM. Seed fixed, no ROM, save, NAND, network or personal data.
- Host Apple Clang C++17, target `AREngine.cpp` with ASan/UBSan linked to the existing host shared-core archive, no full archive rewrite. If the host caller reaches the contract, Android NDK API35 ARM64 target-source UBSan on isolated 4 KiB and 16 KiB AVDs. No physical device or APK install.
- Initial budget: 45 minutes from 07:45 CET, at most 50 MiB new output, free space about 17 GiB, floor 10 GiB. Predicted host ELF under 25 MiB and NDK ELF under 5 MiB. Stop at proof/confutation, budget or real prerequisite. Report any deviation, and mark unavailable levels SKIP/BLOCKED.

## Contract and planned confutations

The parser must not read beyond `ARCode::Code` or write host-adjacent bytes into emulated RAM when an E-code byte count exceeds its remaining words. A complete E-code must copy its declared bytes exactly. The first suspected wrong transition is accepting the outer instruction header without proving two words remain, then trusting E-code `b` without proving the payload exists. The production ARM7 VBlank IRQ invokes `RunCheats`, so the reproducer will use that callback rather than a private interpreter method.

Negative controls: exact 8-byte E-code, same malicious vector disabled, a conditionally skipped E-code, and no code. Confutations: the IRQ may be masked or no game may be running; the test will require an exact positive control before interpreting a sanitizer failure. A host result is shared-core evidence only, not Android ART or whole-app validation.

## Red reproduction and controls before product edit

The first sandboxed attempt aborted during `NDS::Reset` because the host core could not allocate its shared-memory mapping with `shm_open`. That is a test-environment prerequisite failure, not an Action Replay result. The same test executable then ran with access to its isolated host shared memory; `NDS::Current` was set as `NDS::RunFrame` does before reset. No device, user file or application data was accessed.

The production `ARM::TriggerIRQ` checks ARM7 VBlank interrupt flags and invokes the private `AREngine::RunCheats`. With a complete 8-byte E-code, emulated ARM7 RAM contains exactly `12345678 9ABCDEF0`. The disabled malicious code and a false conditional surrounding an E-code leave RAM zero. These three controls exit 0 and show that the real IRQ path and output oracle work.

The same path yields three independent malformed-vector failures before any core edit: a two-word E-code with `b=8` but no payload produces host ASan `heap-buffer-overflow` read at `AREngine.cpp:366`; a one-word vector reads the absent second header word at line 66; an empty enabled vector triggers UBSan in `vector::operator[]` from line 44. All exit via abort (`-6` in the Python runner). The first incorrect transition is reading header or E-code payload without proving enough words remain; a conditionally skipped E-code also advances its pointer by the untrusted byte count and requires the same bound.

Source SHA-256 before: `b9f235f31baa2268d50dd49d0b9c0a9de05b5d2c716836db4df8408d66f4c4e5`. Test source SHA-256 `2f8c4c81695f7531f9006dfde577bfd3549fc080262ceaf4915e569c284f8c86`; runner `fb064de97c857c7b82ca4102ac12259f6a159b4ac123b161bfb74a73222cf81f`.

Raw files under `local/reports/ds-2026-11-03/action-replay/`: `pre-status.csv` SHA-256 `1bf273b9787e644cf4f62b8719b824422098cc26d1e6acbe3b9f3b5f3c42d8f2`; `pre-truncated.log` `17909393e2fa43a7f3251c4c8eee3fbc210d99f76e84dbe980c175c9161af78c`; `pre-one-word.log` `b759c5929bdda42156cf5325eab17fb60405c4b83858c65e689766286d5e1719`; `pre-empty.log` `c1e7c49320bf274516993aaa331264e5dfdffb725be64eed2ee459cce6d119`; control logs are separately retained. Proof level: host ASan/UBSan on the production IRQ/interpreter path, with Android execution pending. No host-memory disclosure to a guest or exploit was demonstrated; the sanitizer proves the read beyond the vector.

## Correction and verification

`AREngine::RunCheat` now returns before forming a pointer for a vector shorter than two words, checks that each opcode retains its parameter, and bounds the rounded E-code payload against the remaining vector before both active copy and conditional skip. The calculation widens `b` before adding seven, so `0xFFFFFFFF` cannot wrap the required-word count. This is one parser-length cause; no cheat format, persisted data or Android frontend behavior was changed. Current official upstream shared-core and Android-core files both lack these bounds; no matching PR or issue correction was found. App #1264 remains a separate historical Android 14 import case.

The same host ASan/UBSan IRQ runner passes all ten modes after the patch: exact eight-byte copy; one-byte and five-byte tails; disabled malformed code; conditional skip with malformed code; conditional skip followed by a valid write; one-word, empty and two-word truncated vectors; and maximum `u32` claimed length. The complete payload controls compare exact emulated ARM7 RAM values. The actual `AREngine.cpp` translation unit is compiled with sanitizers and linked to the existing host core archive. The Android API35 ARM64 executable compiles that same patched translation unit with UBSan and links to the existing app core archive. It passes the same ten modes on isolated 4 KiB and 16 KiB AVDs, with SDK, ABI and page size checked before execution. Both AVDs were stopped; each remote ELF copy was hash-checked and removed after a path-specific cleanup manifest.

The Android test prints a nonfatal `ftruncate` allocation warning during synthetic setup; the valid copy and all ten oracles still complete. A first cross-build attempt referenced `NDS::Current` directly and failed to link because that symbol is not exported by the existing Android archive. The runner instead resets the core normally and uses the real VBlank IRQ callback. A transient attempt to call `RunFrame` before reset hit an unrelated SPU zero-length VLA; it was discarded before the accepted run and is not counted as a cheat result. The host run requires permission for the core's shared-memory allocation; its sandbox-denied setup is retained as a prerequisite failure, not a red cheat test.

This is host sanitizer RED/GREEN and Android NDK target-source EXECUTED evidence. The host archive and the Android app-core archive were not fully rebuilt for this unit. JVM/Robolectric, lint, full APK assembly, ART/instrumentation, real-game cheat execution, TSan and physical device are SKIP. The prior DS Wi-Fi APK predates this correction. Building another 68 MB APK would exceed the unit's 50 MiB new-output ceiling, so no APK containing this fix is claimed and no app was installed. No private user input was opened. Free space remained above 10 GiB.

### Reproduction commands

```sh
# Requires the host core's shared-memory allocation to be available.
bash tools/host-tests/cheats/run.sh

# Builds a component executable with the actual patched AREngine.cpp.
bash tools/host-tests/cheats/build-android.sh

# On each isolated API35 ARM64 AVD, verify SDK/ABI/page size, copy only this
# component ELF to a unique /data/local/tmp/melonds-ar.XXXXXXXX directory,
# then execute each mode with UBSAN_OPTIONS=halt_on_error=1:
for mode in valid one-byte five-byte disabled conditional-skip conditional-follow \
            one-word empty truncated huge-length; do
  ./ActionReplayLengthTest "$mode"
done
# Hash the remote copy, write an exact deletion manifest, remove that copy,
# and stop the named AVD. No APK is installed.
```

Source SHA-256 after `5e25984bd37a6e8f58ca52b09ff59e8fdf001cce473d24bc87080270acd14032`; final test source `a1a4ff0c887053844cfac79f00132006b3aeae564132063f9831506c49f5db3c`; host runner `135601a5a5343be8a9a9333baf52e7d9a7cc51c6a06181233ad564a3a9046876`; NDK build runner `6d69b6bf6441980d9888adebb4b91aaf55ee199470d94928d61ae93870bbb510`. Current 20,584,952-byte host ELF and 1,113,040-byte Android ELF; Android ELF SHA-256 `9a0f327f66c45d2226f70080cbcf77d8ae61011d856f2ecf3f3f2772a7ba2969`.

| Raw file under `local/reports/ds-2026-11-03/action-replay/` | SHA-256 |
| --- | --- |
| `post-host-full.log` | `da12e3feb38bf76e41703830625367df21f8cd7e94f00fcbb3f2ab2a934a4c60` |
| `post-android-4k.log` | `2f0b07e0232c21d02dad5bf30f08ff90dda19903425b54207b218661ead17561` |
| `post-android-16k.log` | `2f0b07e0232c21d02dad5bf30f08ff90dda19903425b54207b218661ead17561` |
| `post-android-4k-status.csv` | `37abb519512ee1819fa436a53ab97a41b34d13db1dc43520cfb5600eebdd82c8` |
| `post-android-16k-status.csv` | `37abb519512ee1819fa436a53ab97a41b34d13db1dc43520cfb5600eebdd82c8` |
| `cleanup-android-4k.csv` | `52744ff57e818442922a9c304bd77ea946411c94ac0513634a847c6a76b2b9e7` |
| `cleanup-android-16k.csv` | `9d923d9bc20e5fd6fd3cc05e3e4252cbb3d6f3de2cff018b07b833628f442dcb` |

The private receipt is `local/reports/ds-2026-11-03/action-replay/receipt.json`; the canonical `verifiche` tool at `d8a11722f5cc0535f4d58ea6a1ab52c214797fdb` returned `PASS=1`, a consistency check rather than security certification. Local core commit `4e989e46` (`Reject truncated Action Replay code payloads`). The paired app gitlink/reproducer/report and canonical-document commits follow separately. No upstream PR was opened.
