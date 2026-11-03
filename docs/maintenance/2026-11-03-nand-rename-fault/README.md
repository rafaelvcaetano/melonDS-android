# DSi save replacement under a NAND metadata write fault

Date: 3 November 2026. Unit: NAND replacement. Initial budget: 45 minutes,
50 MiB of new output. Only authored FAT/NAND and save bytes are used.

## Identity, reachability and prior art

- App `03feb66ef321e466fb533804f9a86530cfdb7a33`, `main`; core
  `f76940fcc0077c904d09dd9039373f6514d3f8b0`, `main`. The consolidated
  earlier fix remains core `403c4683` and app `1cc24ead`; neither is amended.
- Local finding `NAND-RENAME-FAULT`, discovered by this campaign. There is no
  matching upstream issue or PR in targeted searches for `NAND save import`
  and `NAND rename` across melonDS-android, melonDS-android-lib and
  melonDS-emu/melonDS on 3 November. Upstream issue
  [melonDS #1651](https://github.com/melonDS-emu/melonDS/issues/1651) is open,
  reported by MavisCelus19201 in March 2023; its two comments by RSDuck and
  MavisCelus19201 concern title installation and do not establish this fault.
- Caller: `AndroidDSiNandManager.importTitleFile` after the DSiWare manager's
  SAF picker, then `MelonDSiNand.importTitleFile` and JNI in
  `MelonDSNandJNI.cpp`. Owner: `NANDMount::ImportTitleData` in core
  `src/DSi_NAND.cpp`. Consumer: the DSi title's `public.sav`, `private.sav`
  or `banner.sav` and the Boolean shown to the import UI.
- Input: an existing save and a replacement from a readable provider, with a
  single underlying NAND sector write error during FatFs metadata update.
  Synthetic 4 MiB encrypted NAND, 64 KiB old save byte `0x51`, replacement
  byte `0x62`, same title metadata and source path throughout. No personal
  storage or device was touched.

## Contract and red reproduction

When import returns false, the committed old save must remain reachable and
byte exact after remount. A successful return must leave the replacement
reachable and exact. A one-shot write fault is injected in the host-file
boundary while production `NANDMount`, encryption and FatFs execute unchanged.

```text
bash tools/host-tests/nand-fat/run.sh
clang++ -std=c++17 -g -O1 -fsanitize=address,undefined \
  -fno-sanitize-recover=all -DJIT_ENABLED -DARCHITECTURE_ARM64=1 \
  -I melonDS-android-lib/src -I app/src/main/cpp \
  tools/host-tests/nand-fat/ReplacementFaultSweepTest.cpp \
  tools/host-tests/nand-fat/FaultPlatform.cpp \
  app/src/main/cpp/FileModeString.cpp \
  app/build/core-host/src/libcore.a \
  app/build/core-host/src/teakra/src/libteakra.a \
  -o build/host-tests/nand-fat/ReplacementFaultSweepTest
ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=halt_on_error=1 \
  build/host-tests/nand-fat/ReplacementFaultSweepTest 131 135
```

The existing host NAND suite passes with no fault; the ordinary replacement
uses 135 physical writes. The bounded sweep 115–135 and two independent runs
of 131–135 were red in the same way (exit 1). `FR_OK` is 0 and `FR_NO_FILE`
is 4 in the raw output:

| Failed write | Import | Current after remount | Backup after remount | Finding |
| --- | --- | --- | --- | --- |
| 131 | false | old exact | absent | healthy fault control |
| 132 | false | absent | old exact | old save not reachable at normal path |
| 133 | false | new exact | old exact | false return after new bytes committed |
| 134 | true | new exact | old exact | stale backup; cleanup failure ignored |
| 135 | true | new exact | absent | healthy late fault control |

At ordinal 132 the first incorrect transition is the current-to-backup
`f_rename`: it returns an error after moving the old entry, and the method
exits with only `*.melonds-old` present. At 133 the failed staged-to-current
rename has already made the new entry visible; the original rollback cannot
replace it, so the method returns false with new bytes at the committed path.
A later call can recover the backup, but that does not satisfy the immediate
false-return contract. A temporary error-branch probe confirmed that ordinal
132 never enters the staged-to-current failure branch.

Confutations: (1) ordinal 131 and 135 retain the expected generation; (2)
the targeted 131–135 run repeated byte-for-byte with the same identities and
setup; (3) no-fault replacement and the earlier read/write and recovery
controls pass. This is a **reproduced host shared-core and Android NDK
finding**. The exact 131–135 matrix also reproduced on isolated API35 ARM64
4 KiB and 16 KiB AVDs, before any production change. Rollback fault
persistence, ART/SAF and DSi HOME remain separate checks. The test has not
modified production code at the point of this red record.

## Raw evidence

- Host ASan/UBSan test ELF SHA-256:
  `393f0487095bdc1a4713fbf75f6a2f35c35436c0b49da0192633c8d029b73cad`.
- Identical targeted logs: `build/host-tests/nand-fat/sweep-131-135-a.log`
  and `sweep-131-135-b.log`, each SHA-256
  `079b520978f7fbb7cc4971a03e14cfcf627185fbedccdbb1243fc4af49f73455`.
- Earlier 115–135 sweep is `build/host-tests/nand-fat/sweep-115-135.log`.
- Android NDK test ELF SHA-256:
  `25d6d5936a11bf308c754b052b48d250c9b6f04b80848d78a8ec4df65c098624`.
- API35 4 KiB red log: `build/host-tests/nand-fat/ndk-4k-red.log`, SHA-256
  `300ec6574264c8225ff436457baf313789fbfd774caa4d6071c33664053abd23`.
- API35 16 KiB red log: `build/host-tests/nand-fat/ndk-16k-red.log`, SHA-256
  `4573b204605f135da7862f75db2731f5f057050d73aae2fac92d2e7619f80943`.

## Correction and verification

The [upstream shared-core implementation](https://github.com/melonDS-emu/melonDS/blob/master/src/DSi_NAND.cpp)
(retrieved 3 November, blob `a896ff33`) still removes the destination before
import; it has no equivalent staging or rename rollback patch. The local
staging change from `403c4683` is retained. The new correction addresses
only two ambiguous FatFs rename outcomes: after a failed current-to-backup
rename it restores an already-moved backup; after a failed staged-to-current
rename it removes an already-visible destination and restores the backup.
Both paths leave the backup in place if further I/O prevents rollback, so the
existing next-import recovery remains available. There is no claim of
power-loss atomicity or of recovery from a persistent hardware failure.

The first attempted patch fixed ordinal 133 but not 132. An internal branch
probe showed that 132 never entered the staged-to-current error branch;
the corrected first-rename branch made the same test green. Temporary probe
logging was removed from final core source. The final source is SHA-256
`51ddc44f7c1ad7e6e26d0958ee3449eff6f1742e9e395bc442b5dd4bf7550c5c`.
The local core commit is `851d7c399d0fbcc65ab0aab4931f27503b923403`
(`Restore DSi saves after failed NAND renames`), on `main`; no upstream
publication was performed.

| Layer | Final result |
| --- | --- |
| Host AArch64 ASan/UBSan, exact 131–135 regression | PASS, 0 failures; old bytes exact at 131–133, new bytes exact at 134–135 |
| Host one-shot physical write sweep, ordinal 1–135 | PASS, 0 failures; independent fresh encrypted NAND per ordinal |
| Host neighboring `ExistingSaveReplacementTest` | PASS, 0 failures; source, data write, space, recovery and retry controls |
| Android NDK API35 ARM64, 4096-byte AVD | RED before, PASS after, exit 0; final cleanup retry also checked |
| Android NDK API35 ARM64, 16384-byte AVD | RED before, PASS after, exit 0; final cleanup retry also checked |
| JVM/Robolectric, Android lint, GitHubProdDebug assemble | Gradle exit 0; 116 tasks, 10 executed and 106 up-to-date |
| ART/SAF, installed APK, physical device, DSi HOME | SKIP: APK installation and personal device use barred; no full NAND fixture/guest |

The one-shot fault at ordinal 134 can make backup removal fail after the new
save is committed. The method still returns true with exact new bytes and an
old backup. The next import attempt removes that backup while preserving the
new save, as the final test now asserts. Persistent backup-cleanup failure is
not covered and remains a separate lead.

Final test source SHA-256:
`407ff43c8ef03463a268025ad71f13ee6132a2cce50ef072f7af11d51573df8c`.
The final host test ELF SHA-256 is
`b704506d039be8d30cb9faa53566e4d9476f9ec71532fa0be2074791b147986c`;
the Android NDK ELF is
`1c0eff484a447ebeaac5eb4f746af7afc7b605db233cc3c49bc8970f3821434c`.
Raw final logs and SHA-256:

| File under `build/host-tests/nand-fat/` | SHA-256 |
| --- | --- |
| `sweep-1-135-final.log` | `05c3d234eeb505f3a9cce02d13d48a099d43ce779167b677f7ffe28c99cb193f` |
| `sweep-final-cleanup.log` | `a88b936fb895e58f54afd29c1b948a69a1c1a224bd9874739a61c22cea8e2325` |
| `existing-green-final.log` | `900c234796198375007907211b14470e91c900ac20ede7e61bc94e18733931c1` |
| `ndk-4k-final.log` | `03ae18abf83682efe20cc4c40cd165642b134d379eeebd514905d500b6e1df79` |
| `ndk-16k-final.log` | `7bee90bf204853a5022625a8e2ac9362ff778a721dd72f6db5f8d76f1815322c` |
| `app-gradle-final.log` | `148c148cb82b36978b32e95fae79ed524bb307c0e72e8c2cbbf77bbc7c6a909a` |

App package after the core change: `app/build/outputs/apk/gitHubProd/debug/app-gitHub-prod-debug.apk`,
SHA-256 `9174db6b930f6653809954fc780de91e0fa0f93410099d4cb937ae56a8e782fe`.
`aapt` reports `me.magnum.melonds.dev`, versionCode 47, versionName
`2.1.6 GH`, minSdk 24 and target/compile SDK 37. Archive contents contain
`libmelonDS-android-frontend.so` for arm64-v8a, armeabi-v7a and x86_64.
`apksigner` verifies v2 with the existing Android Debug certificate SHA-256
`41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.
The APK was inspected, not installed.

All AVDs were stopped and `adb devices -l` returned no attached device. Free
space after the runs was 18 GiB. The initial 50 MiB new-output ceiling was
**exceeded**: the newly built NDK debug test executable alone is 79,088,736
bytes, and the host test binaries total more. The test was already running
when this was measured; this is a process deviation, not a PASS on the output
budget. Later units must estimate debug-symbol size before compiling and use
a smaller build configuration or stop before that ceiling.

The test uses the production core and FatFs on synthetic NAND, but host test
platform hooks inject the physical write failure. This is Android NDK native
execution, not whole-app ART/SAF or guest behavior. No firmware, BIOS, NAND,
save, ROM or database from the user was read or modified.
