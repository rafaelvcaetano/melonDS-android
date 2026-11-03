# DS Wi-Fi frame length audit — 2026-11-03

## Fixed experiment before execution

- Frontier: DS `Wifi::TXSendFrame` → `WifiAP::SendPacket` → Android `Platform::Net_SendPacket`; DSi WMI is a separate later frontier.
- Initial identities: app `927554c658b4231ce85a7df36a4d4553b7017233`, core `851d7c399d0fbcc65ab0aab4931f27503b923403`, both `main`; app and core working-tree state retained.
- Input: deterministic open authentication and association; channel 6; data frame with 24-byte IEEE 802.11 header, 8-byte LLC, 3-byte payload and 4-byte FCS. Additional short total length 44, oversized payload 2035, wrong channel, missing association and invalid LLC. No ROM, firmware, NAND, save, personal device or network transmission.
- Seed: fixed byte sequence starting `0x40`; no random input. Host Apple Clang C++17 `-O1`, ASan+UBSan (alignment disabled after the initial unrelated management-frame alignment trap); Android API35 ARM64 NDK `-O1 -g0` with UBSan and isolated 4 KiB/16 KiB AVDs if within budget.
- Budget: 45 minutes from 07:12 CET; 50 MiB new output, expected peak under 20 MiB for two small executables and bounded logs; free space initially 18 GiB, minimum 10 GiB.
- Stop: red production-source reproducer plus controls, then smallest fix and green rerun; otherwise BLOCKED/SKIP with reason.

## Lead and contract

- ID: `WIFI-DS-GUEST-LENGTH`; source: canceled Daybreak Blue deep scan `0abee950-c616-43b2-9913-27def27aab63`, static rule `guest-wifi-length-underflow`.
- Upstream search: no matching issue or PR found in `rafaelvcaetano/melonDS-android`, `rafaelvcaetano/melonDS-android-lib` or `melonDS-emu/melonDS` by `WifiAP`, `packet length`, `overflow`; current upstream `WifiAP.cpp` still uses the same unbounded copy. Links: <https://github.com/melonDS-emu/melonDS/blob/master/src/WifiAP.cpp>, <https://github.com/rafaelvcaetano/melonDS-android-lib/blob/master/src/WifiAP.cpp>. This search is not evidence of exploitability.
- Owner: shared core `WifiAP`; caller: DS guest TX slot in `Wifi::TXSendFrame`; consumer: Android `Platform::Net_SendPacket` and the network driver. Guest-controlled TX length is capped near 8 KiB but the LAN buffer is 2048 bytes.
- Expected: reject incomplete or too-large Ethernet conversion, preserve exact 3 payload bytes and send length 17 for a valid frame.
- Observed before execution: static arithmetic uses the 12-byte TX header in the payload calculation and does not bound the copy; dynamic outcome pending.
- First suspected wrong transition: `WifiAP::SendPacket` computes `lan_len` from the total TX length and copies `lan_len - 14` bytes from the guest frame into `LANBuffer`.
- Confutations planned: wrong channel, unassociated client, invalid LLC, and a valid bounded frame. Level before execution: static lead only.

## Results

Initial control command `bash tools/host-tests/wifi/run.sh wrong-channel unassociated bad-llc` aborted before any scenario at `WifiAP.cpp:217` on a pre-existing misaligned `u16` store in management-frame reply creation. This does not test the packet-length contract. The repeat disables only the unrelated alignment sanitizer; ASan and other UBSan checks remain active.

The test first used a vector sized exactly to the advertised frame. ASan flagged a read beyond the source for `valid` and `short`, and a read beyond the source for `oversized`. In the real caller, the source is a fixed 8192-byte `TXBuffer`; the test now models that backing capacity and passes the separate guest-advertised length. This is a falsification of the assumption that the ASan source read alone demonstrated a native write overflow.

Before product change, `bash tools/host-tests/wifi/run.sh wrong-channel unassociated bad-llc` reports three `PASS` controls. With the same binary and fixed backing buffer:

| Mode | Command argument | Result | Contract failure |
| --- | --- | --- | --- |
| Valid associated frame, 3 payload bytes | `valid` | exit 4 | Sent length differs from expected 17; `lan_len` includes 14 extra bytes. |
| Missing FCS, total length 44 | `short` | exit 5 | Invalid frame was sent instead of rejected. |
| 2035 payload bytes | `oversized` | exit 134, ASan stack-buffer-overflow | `WifiAP::SendPacket` writes 2049 bytes at `LANBuffer+14`, 15 beyond the 2048-byte buffer. |
| Wrong channel | `wrong-channel` | PASS | No send. |
| Unassociated client | `unassociated` | PASS | No send. |
| Invalid LLC | `bad-llc` | PASS | No send. |

The minimum reproducible path authenticates and associates through public `SendPacket`/`RecvPacket` calls, then sends a DS guest frame through production `WifiAP.cpp`; only the host networking and MAC lookup are test adapters. No ROM/game or Android runtime has yet been exercised. The first wrong transition is the conversion `lan_len = (len - 30 - 4) + 14`: `len` still includes the 12-byte TX header, and the code neither checks a complete data frame nor the 2048-byte output capacity. The oversized case proves an actual native out-of-bounds write. The valid and short cases prove incorrect length handling; network disclosure beyond guest payload remains an inference, since this adapter does not send a real packet.

Source SHA-256 before change: `WifiAP.cpp` `38d3a977a422a43422ce96a0db0d7e55bf79365b207795a491619282fac4584d`; reproducer `895ae7674d732c394c1be83e7e9dd848a7b94f9e51423828d3906d507ea3da4f`. Raw control log: `2002b2e9fb3237d600be072fa1d9641e215a8624077cc86a1676c79b7895e0fc`; oversized ASan log: `77e8fac6cb9b3d839f81378bb1d4b886a8efded4205e400e537c4f22f77c3399`. Empty valid/short stdout logs have the SHA-256 of an empty file; their exit statuses are captured above.

Level: production source executed on host with ASan/UBSan (except alignment), with three clean controls and a fixed-size caller-model confutation. Android NDK execution, ART, AVD and real game remain pending. Next experiment: minimum length and output-capacity checks in the core, then the same host modes and Android NDK execution if feasible. No product edit preceded this record.

## Correction and independent verification

The upstream official `melonDS-emu/melonDS` `src/WifiAP.cpp` blob `ccb50e4a1f323fd5876e8866bbd78bf543c4e1b5` and Android core upstream blob `6001da1b3423df904048081c2d41ded3d4699453` still contain the same calculation and copy as of this audit. No matching upstream fix was identified; no upstream code or contributor credit was silently claimed. The local correction in `src/WifiAP.cpp` rejects TX packets shorter than a basic 24-byte Wi-Fi header, rejects data frames shorter than the 12-byte TX header plus 24-byte Wi-Fi header plus 8-byte LLC plus 4-byte FCS, rejects Ethernet payloads exceeding the 2048-byte LAN buffer, and computes the sent Ethernet length from that complete frame size. It changes no persisted format.

After the correction, ten modes pass in the host ASan/UBSan executable: valid 3-byte payload, zero payload, exact maximum 2034-byte payload, 11-byte total packet, 44-byte truncated packet, 47-byte missing-FCS packet, 2035-byte oversized payload, wrong channel, unassociated client, and invalid LLC. The host build of the full shared `core` target also succeeds. Source SHA-256 after: `bb18bbbcaee86bb719d40b6c3726c653c5d539e2f5ea1ded213998f2b935e6dd`; final test source `e5b9cde11f65174e6446e91978ab2657120f05427e68082f80beded324aeb48f`; runner `02777a5425fb2c41898eaa823619c86486f40728706fa37f26b8a6558eb7dd10`.

The test was separately compiled with Android NDK 28.0.13004108 for API35 ARM64, `-O1 -g0 -fsanitize=undefined -fno-sanitize=alignment -static-libsan -static-libstdc++`, using the actual `WifiAP.cpp` translation unit. Its ELF is 3,385,432 bytes, SHA-256 `8cb1d1c8ad8957373947cf396c205a87ad9cd78ee686b9cf9085e0507de1a097`. The same ten modes exit zero on the isolated `melonds-buglab-api35-arm64-4k` AVD (SDK35, arm64-v8a, page size 4096) and `melonds-buglab-api35-arm64-16k` AVD (SDK35, arm64-v8a, page size 16384). The test adapters capture `Net_SendPacket`; they do not operate Android's actual network driver or ART. Both AVDs were stopped. Each remote test ELF was inspected (path, 3,385,432 bytes, matching SHA-256), recorded in `cleanup-4k.csv`/`cleanup-16k.csv`, and then removed from its own temporary directory. No APK or personal device was used for these native runs.

The app's `testGitHubProdDebugUnitTest`, `lintGitHubProdDebug` and `assembleGitHubProdDebug` Gradle tasks complete offline with exit 0 (116 tasks: 17 executed, 99 up-to-date). The JVM task was **UP-TO-DATE**: its previous results were reused, not freshly executed against the native change. The package was rebuilt and checked without installation: `me.magnum.melonds.dev`, versionCode 47, versionName `2.1.6 GH`, minSdk 24, target/compile SDK 37, `libmelonDS-android-frontend.so` for arm64-v8a, armeabi-v7a and x86_64, Android Debug certificate SHA-256 `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`. APK SHA-256 `bc6973568d630cc326c3778e73edee2eff070c8c170653aa0c66760df0b111eb`, 68,142,245 bytes. An initial Gradle attempt was blocked by absent default Java; a second by the sandbox's `~/.gradle` lock. The successful command set `JAVA_HOME` to the existing Homebrew OpenJDK 21 and used the existing Gradle cache with permission escalation; no dependency was downloaded.

### Reproduce

```sh
# Host; compile the exact production Wi-Fi parser, then run all ten scenarios.
bash tools/host-tests/wifi/run.sh
bash tools/core-host-sanitize.sh

# Android cross-build of the same parser; no APK installation.
"$HOME/Library/Android/sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android35-clang++" \
  -std=c++17 -O1 -g0 -fsanitize=undefined -fno-sanitize=alignment \
  -fno-sanitize-recover=all -static-libsan -static-libstdc++ \
  -I melonDS-android-lib/src \
  tools/host-tests/wifi/WifiAPLengthTest.cpp melonDS-android-lib/src/WifiAP.cpp \
  -o build/host-tests/wifi/WifiAPLengthTest-android35-arm64

# On each named isolated AVD, after checking API/ABI/page size, create a unique
# /data/local/tmp/melonds-wifi-ds.XXXXXXXX directory, push only the ELF above,
# and run these arguments in order; inspect/hash it before removing only that file.
for mode in valid zero-payload max-payload tiny short missing-fcs oversized \
            wrong-channel unassociated bad-llc; do
  UBSAN_OPTIONS=halt_on_error=1 ./WifiAPLengthTest "$mode"
done

env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  bash ./gradlew --offline :app:testGitHubProdDebugUnitTest \
  :app:lintGitHubProdDebug :app:assembleGitHubProdDebug
```

| Raw evidence under `local/reports/ds-2026-11-03/wifi-ds/` | SHA-256 |
| --- | --- |
| `pre-status.csv` (red exit codes and controls) | `cb0281eb06d24f36e694900bcc56fbad37e30470f5b8525efda46612b8724d10` |
| `pre-fixed-oversized.log` (ASan write) | `77e8fac6cb9b3d839f81378bb1d4b886a8efded4205e400e537c4f22f77c3399` |
| `post-host-full.log` (ten modes) | `e086b36bd61ed3c42bdc0e7b487ef9c6fdc1f38e9ce9824273fbe9814850340a` |
| `post-android-4k.log` | `e086b36bd61ed3c42bdc0e7b487ef9c6fdc1f38e9ce9824273fbe9814850340a` |
| `post-android-16k.log` | `e086b36bd61ed3c42bdc0e7b487ef9c6fdc1f38e9ce9824273fbe9814850340a` |
| `post-core-host-build.log` | `00402d0b6e9b2193c30ab5bd989ff6b59a68dc60c267b154f23c1013ea725837` |
| `post-app-gradle.log` | `2c41a337cf4026fb2fe5a8dfdbc29a27d9ecec281f7ad5c421a40566e740cd2d` |
| `apk-identity.txt` | `d440afe54185425e95abad9bb80008a326d37b2a9842c62844646c96a921f185` |

The isolated native test proves the parser's local memory and length contract on ARM64 Android; host ASan proves the old write and corrected behavior. Whole-app ART, actual Android networking, an installed APK, a guest game, DSi WMI, physical devices and malicious external traffic remain **SKIP/unverified**. The separate `DSi_NWifi::WMI_SendPacket` static lead and a possible negative DS TX length near the end of Wi-Fi RAM are next-frontier candidates, not confirmed by this unit. No new scan was started.

Storage deviation: the small host and NDK executables used under 4 MiB, but required app assembly rewrote a 68,142,245-byte APK, exceeding the unit's initial 50 MiB new-output ceiling. The current candidate APK is retained as the only new package; free space remains about 18 GiB, above the 10 GiB floor. The build directory grew about 1.2 MiB across `.cxx`, native intermediates and APK paths after replacing old outputs. No generic cleanup occurred.

Local core commit: `c8d2f94aa52e43f71043982b4402d84a093cbece` (`Bound DS Wi-Fi frame conversion to LAN buffer`). App gitlink, reproducer and dated report: `9cb77bd512f27eb3157d75aa8bf5a12029a40992`; canonical index, coverage register and PR draft deltas: `e3bae3f5` (full hash in local Git history). No PR was opened.

Private receipt: `local/reports/ds-2026-11-03/wifi-ds/receipt.json`, checked with the canonical `verifiche` validator at `d8a11722f5cc0535f4d58ea6a1ab52c214797fdb` as `PASS=1` for consistency and evidence integrity only. This is not a security certification.
