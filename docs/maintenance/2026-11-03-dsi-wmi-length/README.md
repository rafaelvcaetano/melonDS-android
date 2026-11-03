# DSi WMI guest packet length audit — 2026-11-03

## Fixed experiment before execution

- Frontier: DSi `DSi_SDHost::Write`/CMD52 → `DSi_NWifi::F1_Write` mailbox → `WMI_Command` → `WMI_SendPacket` → Android `Platform::Net_SendPacket`. The prior DS `WifiAP` unit is closed and committed.
- Initial identities: app/core `main` at `7426836fb4e0187e00354194f0054ffc83cb5cb7` / `c8d2f94aa52e43f71043982b4402d84a093cbece`, both pushed to their fork `main` and tracking refs aligned. Existing unrelated local edits remain unstaged.
- Source: canceled Daybreak Blue scan rule `guest-wifi-length-underflow`, a static lead, not a confirmed DSi bug. The official shared-core and Android-core upstream `src/DSi_NWifi.cpp` still contain the same `plen > len-16` check and unbounded `LANBuffer[14+i]` write; targeted issue/PR searches for `DSi_NWifi`, packet length and overflow found no matching correction. Full source URL: <https://github.com/melonDS-emu/melonDS/blob/master/src/DSi_NWifi.cpp>.
- Authored inputs: default generated core firmware and broken placeholder DSi BIOS from `DSiArgs`, no NAND file/ROM/save. Public SDIO CMD52 writes send BMI_DONE, HTC setup complete, valid WMI connect (`melonAP` BSSID), then data endpoint 2. Fixed cases: valid 3-byte payload; missing connect; invalid LLC; `plen=7`; forged `plen=2208` with `len=2224` and only one actual payload byte; oversized `plen` with short advertised `len`. Seed is fixed, payload starts `0x40`, no fuzzing or network output.
- Host: Apple Clang C++17, `-O1`, ASan+UBSan except the core's unrelated intentional alignment accesses; real `libcore.a` and a small platform network-capture adapter. Android target if host reaches the contract: API35 ARM64 NDK, isolated AVDs only. No personal device or APK installation.
- Budget: 45 minutes from 07:25 CET, maximum 50 MiB new output; initial free space about 18 GiB and required floor 10 GiB. Predicted host executable and bounded logs under 20 MiB; inspect size before any Android build. Stop at proved/confuted contract, budget, or real prerequisite failure. A missing prerequisite is BLOCKED/SKIP, never PASS.

## Contract and reachability hypothesis

`WMI_SendPacket` owns a 2048-byte `LANBuffer`; the guest controls the `u16 len` WMI header and big-endian `u16 plen`. The mailbox holds 0x600 bytes. A connected guest can submit a short actual message while claiming a larger `plen`; `DynamicFIFO::Read` returns a stored byte even after depletion. The existing check only compares two guest fields, then writes `plen - 8` bytes from the mailbox at `LANBuffer+14`. Expected behavior is to reject a packet unless its advertised LLC/payload is present and fits the LAN buffer; a valid 3-byte payload should produce exactly 17 Ethernet bytes. The suspected first wrong transition is trusting the pair of guest lengths without checking buffered bytes and capacity. Public CMD52 setup and connection are required in the reproducer, so a direct private-method call is not proof.

Planned confutations: disconnected state, invalid LLC, oversized `plen` with too-short `len`, exact valid frame, and both logical maximum and first-over-limit if the actual mailbox capacity permits. Proof level before execution: source/static only. A host test will be shared-core evidence, not Android runtime validation.

## Results

The first compilation could not default-construct `DSiArgs` because `NANDImage` requires an owner. The corrected harness supplies a sparse, anonymous authored NAND file with the standard synthetic footer and zero key; no original NAND or user firmware is opened. The host's sandbox denies `shm_open` during core allocation, but the valid public SDIO/connection/data sequence completes with exact 17-byte Ethernet output. This diagnostic warning is retained in the raw logs, not counted as a Wi-Fi failure.

Before changing product code, the same host ASan/UBSan executable yields:

| Input | Result | Discrimination |
| --- | --- | --- |
| Fully present 3-byte payload after public BMI/HTC/WMI connection | PASS | Exact 17 output bytes, so the command route and capture adapter work. |
| No WMI connection | PASS | No network send. |
| Invalid LLC | PASS | No network send. |
| Sync frame | PASS | No network send. |
| `plen=2208`, `len=27` | PASS | Existing `plen > len-16` gate rejects it. |
| `plen=7`, `len=23` | exit 4 | A 13-byte incomplete Ethernet packet is sent. |
| `plen=2208`, `len=2224`, only one actual payload byte | exit 134 | UBSan reports `DSi_NWifi.cpp:1333:9`: index 2048 out of bounds for `u8[2048]`. |

`WMI_Command` reads both `len` and `plen` from the guest mailbox. After the existing comparison passes, `WMI_SendPacket` uses `plen` to bound its loop even though the mailbox has no matching payload; `DynamicFIFO::Read` returns a stored slot on empty. The first incorrect transition is accepting two self-consistent guest lengths without checking `plen >= 8`, actual buffered bytes and Ethernet capacity before the `LANBuffer[14+i]` write. The 0x600-byte mailbox capacity does not rule out this case because the loop keeps reading after depletion. Source and owner are reachable from public SDIO CMD52 guest writes; no private method or fake WMI implementation was invoked. Android's `Net_SendPacket` is replaced only with a test capture boundary, after the erroneous write. This is a reproduced shared-core native out-of-bounds write on host, with Android runtime still untested.

Source SHA-256 before patch `0f03b20d468bd1f101181c9cadd42cace3fbb92b1a1be17960bda0d9ea8d0e6a`; reproducer `91fe1e453776130a85b2b59bc2aa488887c589ab7dc561f29204382a9108aeaa`; network adapter `88150023ac1f41e73ea8bd11f92ab152c5d1ab5e8e0a82252f473d8e4c50d10e`; runner `9ae9f62642b70909bfc4e65d1747d40b2ad64d943fd54e1c09007cc0afb47e2d`. Raw `pre-controls.log` SHA-256 `0fce1aa490327d90b47a24de8c25afe4a2640a1c739f334b19731938e56e32c8`, `pre-short-plen.log` `01a9a049e1758874e90a12e1c9abb8a9ccec235f2937b1fedee0d6fe4d90b397`, `pre-oversized.log` `98a2c40f3be9d90ffee7d36f97568df8ca7526455d2b933f684bccb6badd22e2`. `pre-status.csv` retains all exit codes. The host test ELF is about 20 MiB; no Android build or product edit preceded this evidence.

Next experiment: reject incomplete/overlarge WMI data frames before reading LLC or writing `LANBuffer`, preserve the valid/control routes, and run Android ARM64 component execution if the size budget permits. No security or whole-app claim follows from the host run alone.

## Correction, tests and limits

The local change in `DSi_NWifi::WMI_SendPacket` checks the minimum two-byte WMI header, then the minimum 24-byte data form, then the LLC-inclusive `plen` against the advertised WMI length, the actual mailbox occupancy and the 2048-byte LAN capacity. It preserves the existing early return for sync and other special frames. The error message now names all relevant lengths rather than claiming only `plen > len-16`. No NAND, firmware, save or other persisted format changes. Current upstream shared-core `DSi_NWifi.cpp` blob `27feac15d4f0d525e619f12776bc4ffda3e74771` and Android-core blob `269d69ac14836f9581b3583e500544e930a3c172` have no equivalent guard as retrieved on 3 November. No matching issue/PR surfaced in targeted official GitHub searches. No attribution is assigned to an upstream fix that does not exist.

The same public CMD52 host reproducer now passes nine modes: valid exact 3-byte payload, the largest complete message accepted by the 0x600-byte mailbox (1506 payload bytes, exact 1520-byte Ethernet output), disconnected, invalid LLC, sync, `plen=7`, missing actual payload, oversized `plen` with short WMI `len`, and forged oversized `plen` with self-consistent WMI `len`. The real shared core was rebuilt with host ASan/UBSan. A separate Android API35 ARM64 executable was cross-compiled by linking the production `DSi_NWifi.cpp` with the existing app-core archive and instrumenting the target parser. It passes all nine modes on both isolated API35 ARM64 AVDs, 4096-byte and 16384-byte pages. The adapter captures only `Platform::Net_SendPacket`; it does not test the Android network driver, ART or a game. Both AVDs were stopped, and remote copies were inspected and removed under their exact cleanup manifests.

An attempted full-core UBSan NDK variant **did not reach the WMI test**. On the 4 KiB AVD it aborts during synthetic DSi construction at `SPI.cpp:459`: UBSan reports a downcast from an `NDS` vptr to `DSi`. This is a separate pre-existing constructor lead, not a WMI PASS or a reason to alter the WMI oracle. The successful NDK variant uses the already-built Android app core without that full-core UBSan instrumentation, while the exact patched WMI translation unit and test are UBSan-instrumented. Both variants reported a nonfatal `ftruncate` allocation warning on the synthetic setup; valid data output and nine post-fix contracts still completed in the target-instrumented variant. The failed 13,486,848-byte ELF remains byte-exact in a gzip canonical copy, and its source output was removed only after a manifest and SHA check. The 2,872,376-byte successful ELF is retained as the current ARM64 component candidate.

App JVM/Robolectric, lint and full APK assembly are **SKIP for this unit**. No app source changed, and the prior Wi-Fi DS unit's Gradle run is historical for this new core commit. Rebuilding a 68 MB APK would exceed the unit's 50 MiB new-output cap; no new APK was installed or claimed to contain this DSi fix. The app gitlink can be updated and pushed for coordination, but a full package/ART integration run remains required before an upstream PR or release. Host TSan, physical device, DSi HOME and real guest Wi-Fi are also SKIP. The evidence level is host sanitizer RED/GREEN plus Android NDK target-source EXECUTED on two AVD page sizes; it is not a whole-app security conclusion.

### Reproduction commands

```sh
bash tools/host-tests/wifi/run-dsi.sh

# NDK build with the patched parser as an explicit source and the existing
# Android app-core archive; linker strips the output to keep storage bounded.
"$HOME/Library/Android/sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang++" \
  -std=c++17 -O1 -g0 -Wl,-s -fdata-sections -ffunction-sections -Wl,--gc-sections \
  -fsanitize=undefined -fno-sanitize=alignment -fno-sanitize-recover=all \
  -static-libsan -static-libstdc++ -DANDROID -DARCHITECTURE_ARM64=1 \
  -DJIT_ENABLED -DXXH_NO_INLINE_HINTS=1 -DOGLRENDERER_ENABLED \
  -DEGL_EGLEXT_PROTOTYPES '-DMELONDS_GL_HEADER=<GLES3/gl32.h>' \
  -I melonDS-android-lib/src -I app/src/main/cpp \
  -I app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src \
  -I melonDS-android-lib/src/teakra/include \
  tools/host-tests/wifi/DsiWmiLengthTest.cpp \
  tools/host-tests/wifi/DsiNetPlatform.cpp \
  app/src/main/cpp/FileModeString.cpp tools/android-tests/AndroidDynamicLibrary.cpp \
  melonDS-android-lib/src/DSi_NWifi.cpp \
  app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src/libcore.a \
  app/.cxx/Debug/532z4415/arm64-v8a/melonDS-android-lib/src/teakra/src/libteakra.a \
  -ldl -lEGL -lGLESv3 \
  -o build/host-tests/wifi/DsiWmiLengthTest-android35-arm64-appcore

# On each named isolated AVD, verify SDK/ABI/page size, create a unique
# /data/local/tmp/melonds-dsi-wmi.XXXXXXXX directory, push only the ELF above,
# run the modes below, inspect/hash the remote ELF, then remove that exact copy.
for mode in valid max-buffered disconnected bad-llc sync short-plen \
            missing-payload oversized-short-len oversized; do
  UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1 ./DsiWmiLengthTest-appcore "$mode"
done
```

Source SHA-256 after: `DSi_NWifi.cpp` `42d88953010304082d9abf9203e03ed4438edbfb72a97c2b399b8a274905c909`; final test source `c52540b6f05c15fcfaf80e446064a6e9a36db7a35ed69608be18d3d1ac6d2708`; platform adapter `88150023ac1f41e73ea8bd11f92ab152c5d1ab5e8e0a82252f473d8e4c50d10e`; host runner `df325cf0a981fa7239daa549cdc704733ae400ea7f59e9c6bdd42eda80434eca`. Successful NDK ELF SHA-256 `a2475cf097c14631e08c5e49d0019a2a9f0afec3775649e0453f973902e19ee1`.

| Raw local file under `local/reports/ds-2026-11-03/dsi-wmi/` | SHA-256 |
| --- | --- |
| `pre-status.csv` | `d59e313460fa2f633400867148b96af313c0335efa65139433512a81fa574ec4` |
| `pre-oversized.log` (UBSan index 2048) | `98a2c40f3be9d90ffee7d36f97568df8ca7526455d2b933f684bccb6badd22e2` |
| `post-host-full.log` | `f432f115c66c4e798c2749fbcf43d9032999aaf877b2f44277cc5337cbd2f566` |
| `post-android-appcore-4k.log` | `9de3f1366e11dd52e8b0e21b3735d1f5140166728ad467c38f39394474aa8ebb` |
| `post-android-appcore-16k.log` | `9de3f1366e11dd52e8b0e21b3735d1f5140166728ad467c38f39394474aa8ebb` |
| `post-android-4k.log` (separate SPI UBSan abort) | `af103322ff32349543de8e6a55f813ac1ea72fc04a665e1d1b4f6b0f6a5bacee` |
| `cleanup-local.csv` | `3cef7d7a9395a06b2078dda8bb6415244ecc4dbcd402598487e6a4e173ec514b` |

Storage: retained small report/log/manifest files, one 20 MiB host executable, one 2.7 MiB current Android ELF and a 2.3 MiB compressed copy of the full-core UBSan abort artifact. Rebuilding the host `libcore.a` also rewrote a large existing archive, so counting replacement bytes exceeds the initially planned 50 MiB ceiling; this is reported as a deviation. No further large build was launched. Free space stayed about 18 GiB, above the 10 GiB floor. No generic cleanup or original-data deletion occurred.

Private receipt: `local/reports/ds-2026-11-03/dsi-wmi/receipt.json`, validated by the canonical `verifiche` tool at `d8a11722f5cc0535f4d58ea6a1ab52c214797fdb` as `PASS=1` consistency only, not security certification. Local core commit: `23700602ab0aac27be60db6a92f591d0f47d4dce` (`Reject invalid DSi Wi-Fi payload lengths`). The paired app gitlink/reproducer/report commit follows separately. No PR was opened.
