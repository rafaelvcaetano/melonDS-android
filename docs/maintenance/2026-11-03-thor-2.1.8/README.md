# Thor 2.1.8 update and upstream PR handoff

Prepared 3 November 2026. Status: candidate ready for a separately authorized
physical update; no Thor installation or upstream publication performed.
This is a delivery procedure, not a new finding catalogue or device acceptance.

## Candidate to use

The canonical local APK is
`local/notes/pr-review-2026-11-03/candidate/melonDS-2.1.8-nightly-profiling.apk`.
Use that file without rebuilding or copying a second APK tree.

| Field read from the APK | Value |
| --- | --- |
| SHA-256 | `195b5748fc59bac74eadee148f0a1a7b440e47b51dcc5f70fdcd384e855932cb` |
| Size | 85,676,746 bytes |
| Application ID | `me.magnum.melonds.nightly.perf` |
| Version | `2.1.8 GH (NIGHTLY) (PROFILING)`, versionCode 49 |
| Certificate SHA-256 | `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1` |
| Signing | APK v2; preserved local Android Debug certificate; not the upstream release identity |
| Runtime/build | Optimized RelWithDebInfo, not debuggable, not testOnly; minimum API24 |
| ABI | Emulator frontend ARM64; small third-party libraries also carry other ABIs |
| Executable source | app `0018cddb04278f2bca5054804de501a459eb1de9`, core `ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8` |

The [candidate manifest](CANDIDATE.json) records the same identity. Read-only
hash, aapt and apksigner verification was repeated during this handoff.
The certificate name does not make this optimized APK debuggable. It also does
not make it an upstream-signed or Play Store update.

## Before the physical update

The [last physical acceptance](../2026-11-01-thor-update/README.md) records Thor
Android13/API33, ARM64/4KiB, this package/certificate, version 2.1.6/47 and UID
10126. This is historical data: reread all values before installation. Do not
assume 2.1.7 was installed. The planned jump is 47 to 49 if that observation
is unchanged. The separate standard `.nightly` package is not interchangeable.

1. Obtain explicit authorization for the physical update and agree exclusive
   ADB/device ownership with any active ROM work. Keep at least 10GiB free on
   the Mac after the planned backup peak and adequate free space on the Thor.
2. Select the Thor by an explicitly verified serial. Record device model, API,
   page size, installed package/versionCode, APK hash, certificate, UID,
   firstInstallTime, active ROM/save paths and persisted URI grants.
3. Stop gameplay normally after SAVE has completed. Capture fresh private and
   external backups using the previously reviewed same-signer snapshot
   procedure. Record helper identity before use; do not clear data or uninstall
   the target. The previous helper and private scripts remain local under
   `local/notes/thor-update-2026-10-29/backup-helper/` and
   `local/notes/thor-update-2026-11-01/`; old snapshots do not replace fresh ones.
4. Verify ZIP CRC, file sizes/hashes, readable preferences/layout/controller
   configuration, SQLite integrity and logical row hashes, cheat counts and
   selections, and every save/ROM/BIOS/firmware/NAND file. Reuse an existing
   canonical backup only after matching its full hash to the current file.
5. Preserve the APK actually installed as the device-specific rollback
   reference. Android may refuse a version downgrade: an older APK alone is
   not a guaranteed rollback. Preserve data snapshots independently.
6. Stop if package, signer, installed version, backups, available storage or
   ownership differs from the planned conditions. Resolve the difference
   before installation; preserve the newer data generation.

After these gates and authorization, the installation command is:

```sh
adb -s THOR_SERIAL install -r --no-incremental local/notes/pr-review-2026-11-03/candidate/melonDS-2.1.8-nightly-profiling.apk
```

Replace `THOR_SERIAL` with the verified device serial. This document does not
execute the command. Never use uninstall, clear-data, signer replacement or a
downgrade override as part of this update.

## Physical acceptance to record

Before first normal launch, compare private snapshots and installed APK,
certificate, versionCode, UID, firstInstallTime and grants. After launch,
compare preferences, layout, controller mappings, library, databases, cheat
counts/selections and external hashes. Record expected version/profile/cache
changes separately from unexpected user-data differences.

Exercise these steps on named ROM/save working copies, retaining originals:

| Test | Acceptance |
| --- | --- |
| Library and direct/archive ROM | Existing library remains; valid direct/ZIP input opens; bounded invalid input is rejected |
| Continue/SAVE/normal Exit/cold Continue | Fresh process loads the newly saved working generation; no original file changed |
| Savestate rejection | Invalid state is rejected while the prior running state remains usable |
| Cheat import/search | Valid import and selectors work; failure/cancel preserves old DB; retry works; search does not enable codes |
| Settings/controllers/layout | Backup/restore on synthetic settings works and existing configuration remains |
| Lifecycle/input/audio/renderer | Pause/resume, sleep/lid, touch/controller, mute and renderer recovery remain usable |
| DSi/SAF | Use suitable authorized copies; do not substitute a DS game run for DSi/NAND/provider acceptance |

Bound diagnostic logs and record each failure, skip and device limitation.
Retain source tests and the report; stop only campaign-owned processes afterward.
Do not infer all-game safety or CPU/GPU/battery gains from this acceptance.

## Evidence already available

The [second-campaign report](../2026-11-03-security-followup/README.md) preserves
JVM/API, lint/build, host sanitizer, Android-native ARM64 4/16KiB, ART/provider,
WorkManager and optimized-APK runtime evidence. The exact APK ran direct/ZIP
homebrew and copied SGP 1.2.5 IT Continue/SAVE/cold Continue on the 16KiB AVD.
Full synthetic data comparison exists for an earlier 2.1.8 candidate update;
on the exact final APK, UID/firstInstallTime/runtime were checked but private
bytewise reread was blocked. Thor 47-to-49 migration remains untested.

## The two PR drafts

| Repository | Title | Draft |
| --- | --- | --- |
| Android app | Improve Android setup, controls and reliability | [full app body](../2026-10-28-main/NEXT-APP-PR.md) |
| Android core | Fix core state, memory and storage bounds | [full core body](../2026-10-28-main/NEXT-CORE-PR.md) |

Sources are the fork `main` branches; targets are the upstream `master`
branches. Review core first, then update the app gitlink to the revision
accepted upstream. Keep author credits, licenses, regression tests and absolute
evidence links. Private ROM/save/backup evidence and signing material stay local.
After Thor acceptance, append its exact APK/source identities and limitations
to both drafts; recheck upstream changes and links before requesting final
publication approval. No replacement PR currently exists.

### Issue and PR status

No upstream issue was closed by this campaign. The canonical
[issue/PR coverage register](../2026-10-28-refinement/ISSUE-PR-COVERAGE.csv)
records complete, partial, compared and non-integrated work separately.

| Reference | Local disposition / actual GitHub state checked 3 November |
| --- | --- |
| App #1678, NAND title-list JNI references | Exact local cause corrected; issue still open; candidate for closing wording after final acceptance |
| App #264, configuration backup | Existing manager exposed with tested restore; issue still open; candidate after maintainer acceptance of scope |
| Core upstream melonDS-emu/melonDS #2537 | Local teardown cause corrected; separate repository issue still open; reference, no cross-repository closing claim |
| App PR #1681/#1682; Android lib PR #18/#19 | Closed without merge; replacement drafts retain their scope and evidence |
| App #1665; #1651/#1099; microphone, casting, Wi-Fi/game reports | Open/partial or unresolved as detailed in the register; no blanket closing wording |
| Adapted contributor PRs | Credit/reference only; adopting code does not merge or close the original PR |

The 35+66 Security occurrences are a separate register. Their technical
disposition and their service triage state are not GitHub issue closures.

## GitHub builds and maintainer requirements

Read-only GitHub API checks found one app-fork release, [v2.1.1](https://github.com/Umberto-DEV/melonDS-android/releases/tag/v2.1.1),
and no core-fork releases. No fork `nightly-release` release or published
2.1.8 APK exists. [CI run 41720074008](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41720074008)
retains an unexpired ARM64 **debug test** artifact and reports (7-day policy).
That artifact is a separate diagnostic `.dev` package, not the Thor update.

The app [workflow](https://github.com/Umberto-DEV/melonDS-android/blob/main/.github/workflows/main.yaml)
publishes a signed Nightly only from upstream `master`; fork runs test/build
without release secrets. The [upstream README](https://github.com/rafaelvcaetano/melonDS-android/blob/master/README.md)
describes Nightly downloads/building; the [core contributor guide](https://github.com/rafaelvcaetano/melonDS-android-lib/blob/master/CONTRIBUTING.md)
sets C++ style. No fork-Nightly requirement was found in those documents or in
the empty comment/review lists checked for the prior app #1682/core #19 PRs
(app #1681 comments were also empty). This is limited evidence, not an assurance
of what a future reviewer will request.

After successful Thor acceptance and separate publication approval, a fork
prerelease could provide the exact reviewed APK and SHA256SUMS, source pair,
local package/signer identity, compatibility notes and test report. Do not call
it an official Nightly or bundle ROMs/BIOS/saves. A core-only PR needs a source
revision and native test evidence, not a second APK. No release, tag or asset
upload is performed by this handoff.
The [optional fork test-release draft](FORK-RELEASE-DRAFT.md) is prepared for
that later decision and must receive the actual Thor result before publication.
