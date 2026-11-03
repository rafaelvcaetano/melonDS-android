# Thumb DTCM literal folding on AArch64

Date: 2 November 2026. Campaign state: **reproduced at shared-core component
level on Android NDK targets; correction not attempted**.

## Result

The selected `CORE-LITERAL-A64` frontier is reproduced. A synthetic ARM9
program enters Thumb state through an ARM `BX`, executes
`LDR r0,[pc,#0xFC]` at `0x02000F02`, and reads DTCM at `0x02001000`. With the
AArch64 JIT and literal optimisation enabled, the first value `0xAAAA1111`
is folded into generated code. After the guest fixture replaces the DTCM word
with `0xBBBB2222` and then `0xCCCC3333`, both the published RAM result and R0
remain `0xAAAA1111`.

The same program reads A, B and C correctly with literal optimisation disabled
and through the interpreter. The existing ARM PC-relative DTCM control also
reads the replacement value correctly. Fast memory is explicitly disabled in
the target and both confutations, so the previously corrected DTCM fast-memory
mapping is not the cause of this result. The failure is identical on Android
API 35 ARM64 targets with 4 KiB and 16 KiB host pages.

This is one root cause and three executions, not three bugs. It does not prove
that any game report is caused by this path.

## Priority and ownership

This frontier was selected because it was the only prepared gap whose missing
case was not already covered by a demonstrated local correction. NAND
replacement, DTCM mapping and RTC have local fixes and historical regressions;
Surface recreation needs a separate Android UI campaign. The minimum is
synthetic, has an ARMv5 architectural oracle and fits the 45-minute/50-MiB
initial mandate.

The frontend responsibility is configuration and reachability:
`EmulatorArgsBuilder.cpp` enables the JIT, literal optimisation and fast memory
for production when the user enables JIT. The core owns Thumb decoding,
localisation, folding and invalidation. The first wrong transition is in the
core AArch64 backend: `T_Comp_LoadPCRel` calls `Comp_MemLoadLiteral` directly,
before the generic `Comp_MemAccess` path can require
`CurInstr.LiteralRegistered`. `Comp_MemLoadLiteral` rejects addresses already
listed in `InvalidLiterals`, but does not reject `LocaliseCodeAddress()==0`.

No Kotlin/JNI lifecycle, SAF, renderer or game-specific behavior participates
in the reproducer. No production core source was changed in this phase.

## Candidate identities

- App checkout: `main` at
  `1cf4fa6adf3c7a8621734754d907ea0d27824fe9`, with all pre-existing dirty
  changes preserved. The reproducer is the only new production-repository
  source change from this campaign.
- Core checkout: `main` at
  `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`; the pre-existing
  `src/DSi_NAND.cpp` diff remained unchanged, SHA-256 of the binary diff
  `2132ef43030161c1d28f124db511b6d0400061241f8bdb1db06a3c0834f3f2c0`.
- Reproducer source SHA-256:
  `4af1fd18eea42710fd7a66db89e5dfce293c4446c99eeaef318b78e97bb949ae`.
- Host ASan/UBSan test binary SHA-256:
  `afa7d591be1d43b276aa229d6ee441c1faf3f02f659b9a2531b54dd2e7aba418`.
- Android NDK API 24 ARM64 static-libc++/strict-UBSan binary SHA-256:
  `3aa9868298a83267aefb46ca9cdc1857bf8403625ce6627844d6f2a510d9a4da`.
- No APK was built, installed or modified. The personal Thor installation,
  signing identity, app data, settings, grants, saves, NAND and firmware were
  not accessed.

The session did not expose a separately inspectable Daybreak Blue/SOL 6
identity. Results are attributed to the recorded source, binaries and targets,
not to that unverified backend label.

## Upstream comparison

- [Android-core PR #12](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/12)
  is closed and unmerged. Its patch aligns the retry after literal
  invalidation; it is not textually present and does not guard this first
  untracked-DTCM folding attempt.
- [Android-core PR #11](https://github.com/rafaelvcaetano/melonDS-android-lib/pull/11)
  is closed and unmerged. Its selective invalidation-mask correction is already
  present locally and is a different contract.
- [Shared-core PR #2746](https://github.com/melonDS-emu/melonDS/pull/2746) and
  [#2747](https://github.com/melonDS-emu/melonDS/pull/2747) are closed and
  unmerged. Their reported Thumb reload evidence motivated controls, but is not
  substituted for this fresh result on the current fork.
- Local commits `3ad4e940` and `137c4ba7` correctly prevent the generic
  backend from folding unregistered literals and skip registration of
  unlocalisable addresses. The direct Thumb helper call bypasses the former
  guard; those fixes must not be reapplied blindly.

## Reproducer and controls

The retained source reproducer is
[`JitInvalidationTest.cpp`](../../../tools/host-tests/jit/JitInvalidationTest.cpp).
It uses no commercial ROM, BIOS or personal state. The generated synthetic ROM
exists only in process memory.

| Level | Target and configuration | Target | Controls | Result |
| --- | --- | --- | --- | --- |
| Host support | macOS arm64, ASan+UBSan, AArch64 backend, fastmem OFF | A then stale A after B/C | folding OFF and interpreter read A/B/C; ARM control updates | RED, exit 1 |
| Android native | AVD API 35 ARM64, 4 KiB, NDK binary above, strict UBSan | same stale A | same controls green | RED, exit 1 |
| Android native | AVD API 35 ARM64, 16 KiB, same NDK binary | same stale A | same controls green | RED, exit 1 |

The Android runner created a fresh random directory under `/data/local/tmp`,
pushed the one ELF, and removed that directory after each run. Independent
post-run enumeration found no `melonds-core-tests.*` directory. The AVDs were
stopped after use.

The repeated `ftruncate(EINVAL)` diagnostic is an AVD fast-memory allocation
attempt. It is not used as the oracle and cannot explain the chosen case,
which asserts fast memory is disabled; neighboring ARM and mapping cases remain
green. The synthetic ROM-size and game-code warnings predate this case and do
not prevent direct boot or the controls.

## Evidence and receipt

Bounded raw logs and the validated receipt are retained privately under
`local/reports/ds-2026-11-02/core-literal-thumb/`. Before the receipt, retained
raw output was 22,131 bytes. The common method checkout was exactly
`d8a11722f5cc0535f4d58ea6a1ab52c214797fdb`; its offline receipt verifier is
used against the exact ELF and reproducer hashes. No ROM, BIOS, save, NAND,
credential, personal log or APK is in the evidence bundle.

Key commands:

```text
clang++ ... -DJIT_ENABLED -DARCHITECTURE_ARM64=1 ... JitInvalidationTest.cpp ...
cmake --build app/build/android-native-tests/arm64-v8a --target JitInvalidationTest --parallel 8
tools/android-tests/run.sh --serial emulator-5554 --abi arm64-v8a --test JitInvalidationTest
```

## Proof level, skipped checks and stop

State transition: `reported/static hypothesis` → `applicable` → `reproduced`
for the current shared core at component level. The Android NDK executions prove
the native ARM64 backend behavior on the two recorded emulator targets. They do
not establish APK/ART integration, a particular game manifestation, a physical
device result or upstream acceptance.

JVM, Gradle Android build, lint, APK packaging, ART/instrumentation and physical
device checks were not run: no frontend/product correction was made, and those
levels do not answer whether the isolated core instruction is folded. A future
fix must start from this red test, make one root-cause change, rerun the same
three configurations and then run the broader JVM/build/lint/native checks
required by the changed layer. Publication and upstream PRs remain blocked.

The campaign stopped before correction as required. It remained under 45
minutes, retained far less than 50 MiB, left more than 20 GiB free after the AVD
run, and created no additional retained build tree.
