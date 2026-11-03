# UD Nightly release draft — not published

Proposed title: **melonDS Android UD Nightly**

Proposed immutable tag: `ud-nightly-2.1.8-<app-sha>`, not created. Source: the
accepted app `main` revision and its pinned core `main` commit. Prerelease.
The signed UD release APK is not built yet: a persistent fork release key is
not provisioned. Complete the [build and acceptance plan](NIGHTLY-PLAN.md),
fill the actual binary identity below, then obtain explicit publication approval.

---

UD Nightly is our tested fork of melonDS Android Nightly. It includes guided
setup, cheat search, configuration backup/restore and input controls, plus
save, lifecycle, JNI, file parsing, native memory and state-validation fixes.
Original contributors and licenses remain preserved in the source and PRs.

It uses the upstream `gitHubNightlyRelease` build variant, application ID
`me.magnum.melonds.nightly`, optimized native code, R8 and resource shrinking.
Its own persistent UD signing identity is different from the author's: it
updates only UD builds with the same package and signer. Installing over the
author's signed Nightly is not supported. Existing Thor `.nightly.perf` users
need their matching update or a separately validated export/import migration.
No uninstall or data reset is part of the update procedure.

Download one APK. The core is checked out recursively at the exact gitlink
revision and compiled into that APK; no separate library installation is needed.

Publication fields to fill from the **UD release APK**, never from the Thor
profiling candidate:

| Field | Required before publication |
| --- | --- |
| App/core source commits | Exact accepted pair |
| Version name/code | `2.1.8-UD (<app-sha>) GH (NIGHTLY)` / 49 or a later monotonic code |
| APK SHA-256 and bytes | Read from the accepted signed release file |
| Certificate SHA-256 | Persistent owner-controlled UD key; verify against the recorded fingerprint |
| ABI, API, debuggable/testOnly | Inspect the APK; run acceptance on each claimed runtime target |
| Physical/AVD evidence | Exact build identity, tests, skips and limits |

Planned assets: `melonDS-UD-nightly-2.1.8.apk`, `SHA256SUMS` and the public
build manifest. None has been uploaded. ROMs, BIOS, firmware, NAND, saves,
private evidence and signing credentials are excluded.

The [delivery report](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-thor-2.1.8/README.md)
and [per-finding evidence](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)
distinguish source fixes, host checks, Android-native execution, ART and whole-app
runtime. Existing profiling-APK results do not certify this release binary.
No all-game, zero-bug or performance improvement is claimed.
