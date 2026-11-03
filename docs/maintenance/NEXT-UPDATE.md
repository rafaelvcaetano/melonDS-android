# Next modified Nightly update

**3 November second-campaign successor:** the
[second independent scan and 2.1.8/49 delivery](2026-11-03-security-followup/README.md)
supersedes the 2.1.7 candidate for future testing. The retained fallback is
2.1.7/48. An in-place 2.1.7→2.1.8 update with synthetic Room, cheat, layout,
preferences and persisted SAF data passed on the dedicated 16 KiB ARM64 AVD.
The Thor remains at its last documented 2.1.6/47 and was not connected or
modified. A later Thor step still requires explicit authorization, verified
backups/readback and `adb -s SERIAL install -r --no-incremental`; uninstall,
clear-data, signature changes and implicit-device ADB remain forbidden.

**3 November successor:** the [2.1.7/48 delivery and draft review](2026-11-03-nightly-2.1.7/README.md)
supersedes the candidate/version checklist below. The installed 2.1.6/47 is the
fallback; its physical results do not validate the new security code. The new
mandate stops after Android-on-Mac checks, local drafts/docs and an update APK.
No new Thor installation or upstream PR publication is authorized by that step.

**1 November 2026 successor:** the maintainer explicitly authorized the Thor
installation with data preservation and coordination with the Sacred Gold Plus
1.3.1 owner. The [physical update report](2026-11-01-thor-update/README.md)
records the installed 2.1.6/47, preserved data and actual runtime scope. This
supersedes only the personal-device installation gate below; no publication,
push, release, data reset or upstream communication is authorized.

Mandate updated by the maintainer on 31 October 2026. Deliver the next local
modified Nightly as an in-place update for the Thor, together with separate app
and core PR drafts. This is the existing fork with its additions and fixes;
do not restart from a clean official APK or discard previous contributor work.

## Verified starting identity and next version

The [last documented Thor update](2026-10-29-thor-update/README.md) is **2.1.5,
versionCode 46**, package `me.magnum.melonds.nightly.perf`, optimized ARM64,
non-debuggable. `buildSrc/src/main/kotlin/AppConfig.kt` was 2.1.5/46 when this
mandate began and now identifies **2.1.6 / 47**. The
[local candidate and acceptance](2026-10-31-nightly-2.1.6/README.md) are ready;
recheck the installed identity before any later authorized installation.

Preserve the application ID and signing identity needed for an update. The
documented signing certificate SHA-256 is
`41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.
The debug `.dev` package is a separate diagnostic application; its test results
do not establish execution of the optimized `.perf` candidate.

Current app base: `1cf4fa6adf3c7a8621734754d907ea0d27824fe9`; core base:
`fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`, with local NAND corrections and
regressions described in the [first batch](2026-10-31-rtc-nand/README.md).
Always refresh identities and dirty state before accepting later work.

## Investigation, correction and ownership

The exploratory NAND/persistence and Android lifecycle investigation may proceed
to minimal fixes once a defect is demonstrated. The previous exploration-only
restriction has been superseded for those verified defects. No additional human
approval is required for ordinary, authorized source corrections and checks.
Keep the [audit method](AUDIT-METHOD.md), negative controls, confutations,
regression tests and separate host/NDK/ART/physical evidence levels.

The investigating worker owns its production changes/tests and build outputs
until it hands them back. The coordinating session owns integration review,
versioning, final APK packaging, changelog reconciliation and PR draft updates.
Do not run competing writers or build/ADB producers on shared paths. Work on
main in both repositories; preserve pre-existing tracked and untracked work.
Communications between the maintainer's audit and investigation chats are
authorized for this handoff. On 1 November the maintainer also explicitly
authorized coordination with the Sacred Gold Plus 1.3.1 owner for the subsequent
ROM update; that workflow owns its source/output changes. Unrelated chats remain
outside scope. Permission comes from the human instruction, not a worker request.

Each correction must arrive with its exact diff, reproduction and controls,
before/after results, relevant checks/skips, attribution, artifact identities,
coverage limits and confirmation that its producers have stopped. A suspicion
without adequate evidence stays open. Do not force ordinary bugs into security
severity categories or silently convert source review into Android execution.

## Final candidate acceptance

- [x] Reconcile the corrected cases with the canonical issue/PR coverage register;
  independently review the affected callers, behavior and final diff.
- [x] Preserve app/core source changes and contributor attribution with exact
  identities; do not rewrite published history or include unrelated local work.
- [x] Advance versionName/versionCode only after the functional candidate is
  settled. Build the optimized GitHub Nightly profiling ARM64 variant using the
  existing package/signing configuration.
- [x] Run the appropriate JVM, Android build, lint and native checks, then the
  relevant dedicated Android runtime regressions. Recheck the final packaged APK
  when its mode differs from the diagnostic build. Report unavailable checks.
- [x] Verify package, versionCode, ABI, minimum API, certificate, non-testOnly and
  build mode from the APK itself; record APK SHA-256 and app/core identities.
- [x] Retain one canonical candidate, its verified fallback and small supporting
  evidence, following the bounded-storage rules. Provide the APK's actual path.
- [x] Deliver the update and the two PR drafts locally. Physical Thor update,
  install-time identity/data checks and one copied-save execution are recorded
  in the 1 November report, with narrower ART/DSi and ROM-feature limits retained.

Preparing an installable APK was the original scope. The maintainer's explicit
1 November instruction authorized installation, which is now complete as an
in-place update with fresh backups and readback checks. A connection alone never
authorizes a later installation. **Uninstall, clear-data, push, release, upstream
messages and PR publication remain unauthorized.** Preserve original data, URI
grants, settings, package and signing identity in any subsequent operation.

## Documentation and PR deliverables

Reuse these canonical documents rather than creating parallel change lists:

- [App PR draft](2026-10-28-main/NEXT-APP-PR.md).
- [Core PR draft](2026-10-28-main/NEXT-CORE-PR.md).
- [Issue/PR coverage register](2026-10-28-refinement/ISSUE-PR-COVERAGE.csv).
- [Official Nightly comparison and historical proposal context](2026-10-29-nightly-comparison/README.md).
- [Integration report and contributor credits](2026-10-28-refinement/INTEGRATION-REPORT.md).

Both drafts need a short, precise title and an opening summary understandable
without the audit history. List the concrete changes, prior behavior, corrected
behavior and linked test evidence. Separate app/frontend/JNI changes from core
changes, and cross-link the companion proposal only when a real link exists.

For every linked issue or community PR, distinguish fixed locally, partially
addressed, adapted, incorporated, already present, deferred and closed upstream.
Do not imply that a fork fix closed an upstream issue. Use closing keywords only
when full coverage and the target repository justify them. Preserve original
human credit. Do not fabricate CI runs or links to unpublished companion PRs.

The former app proposals #1681/#1682 and library proposals #18/#19 are recorded
as closed without merge in the historical integration report. Keep their history
and attribution; use them as the basis for the replacement drafts rather than
describing them as accepted upstream fixes. Refresh remote state before any
future publication. Source is this fork's main; upstream targets use master.

Draft validation by the maintainer is followed by explicit publication approval;
preparing or validating a draft alone never opens the publication gate.

Local delivery completed on 31 October: [2.1.6 candidate and build acceptance](2026-10-31-nightly-2.1.6/README.md).
Explicitly authorized physical update completed on 1 November:
[installed identity, preserved data, copied-save execution and limitations](2026-11-01-thor-update/README.md).
Ten native tests also pass on the Thor. Whole-app ART coverage of the latest
NAND paths, DSi HOME, every game and all new ROM mechanics remain outside
acceptance. No publication authorization has been inferred.
