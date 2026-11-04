# UD Nightly Implementation Plan

**Goal:** Build a reproducible UD Nightly using the upstream Nightly release
variant and pinned core, while preserving the existing Thor update identity.

**Architecture:** The public fork build uses `gitHubNightlyRelease`, application
ID `me.magnum.melonds.nightly`, and a persistent fork release certificate.
The existing Thor installation continues to receive the same-signer `.perf`
update. Both compile the pinned core into the APK; there is no runtime core
download. App IDs and signing identities are installation boundaries, not save
formats. One cannot replace the other in place without a separate migration.

**Tech Stack:** Existing Gradle wrapper, Java21, SDK37, NDK28.0.13004108,
CMake3.22.1, Git submodules, Android APK v2 signing, GitHub Actions.

**Spec:** [Thor handoff and delivery requirements](README.md).

## Global constraints

- Both fork repositories have only `main`, locally and on GitHub as checked
  3 November. Upstream tracking refs are not additional fork branches.
- App `.gitmodules` points at `Umberto-DEV/melonDS-android-lib`; the gitlink pins
  `ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8`. A branch name does not override it.
- Preserve Thor package `me.magnum.melonds.nightly.perf` and certificate
  `41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.
- Public UD Nightly package is `me.magnum.melonds.nightly`, like upstream.
  Its signer belongs to the fork; it cannot update an upstream-signed install.
- The inspected official c42995c APK has versionCode41 and certificate
  `0493bb8749289f0304cc868489dd68b930c19ccc4dce2da876bfe6b9bdbabebc`.
- VersionCode49 is the planned first UD release floor, unless another delivery
  advances the version first. Recheck before building; never downgrade.
- No changes to Room schemas, raw saves, savestates, settings or cheat formats
  are required to align the build variant. Cross-package private data and URI
  grants do not transfer automatically. Exercise migrations rather than infer them.
- Keep original data, at most candidate/fallback per target, and at least
  10GiB free after the projected build/backup peak.
- No Thor installation, public release, tag or PR until its separate approval.

## Review focus

- Identical `.nightly` IDs with different signers: reject an in-place update,
  preserve both APK identities and never uninstall/clear data to bypass it.
- Existing Thor `.perf` installation: use only its matching update; do not
  rename the application ID of an already signed APK.
- Release R8/resource shrinking: validate launch, JNI registration, Room,
  WorkManager, serializers and SAF against the actual release APK.
- Recursive checkout: reject a missing, dirty or mismatched core/dependency
  before building; use gitlinks, not floating `--remote` submodule updates.
- Repeated UD updates: keep the signer and monotonic code, compare populated
  databases/settings/layouts/grants and saves on an AVD before release.

## Task 1: Fix and preserve the UD signing identity

**Files:** signing material outside Git; future protected fork environment
`ud-nightly-signing`; no changes to the Thor keystore or upstream secrets.

**Interface:** consumes a persistent owner-controlled keystore; produces its
public certificate fingerprint and verified recovery backup, not secret output.

- [x] Check current configuration without printing secrets: no release
  keystore/password/alias is configured in local.properties.
- [ ] Have the owner provision a dedicated persistent UD release key and
  credentials securely. Verify a readable encrypted backup. Do not publish
  with the shared Android Debug key or substitute the author's certificate.
- [ ] Bind signing to the fork-only protected environment, with explicit
  approval. Record the public certificate fingerprint and verify it before
  every signing operation. Signing credentials must never enter artifacts/logs.

**Gate:** signed UD build is blocked until this persistent identity is
provisioned. The existing verified Thor candidate remains usable separately.

## Task 2: Build with the upstream variant and dependency topology

**Files:** create `.github/workflows/ud-nightly.yaml` only in the implementation
stage; reuse `app/build.gradle.kts`, `.github/check_ci_integrity.py` and
`.gitmodules`; add build/identity checks under `tools/` with the workflow.

**Interface:** consumes exact app/core commits and signing identity from Task1;
produces one optimized signed APK, manifest and SHA256SUMS. It does not publish.

- [ ] Use manual workflow dispatch on fork `main` with read-only contents
  permissions and pinned Actions. Recursive checkout uses the fork core URL;
  require clean app/core and exact submodule status. Build from the accepted
  source revision, preserving the unmodified source SHA before version labeling.
  This clean-state gate applies to the CI checkout; preserve unrelated local
  changes in the shared workspace. Initialize with `git submodule update --init
  --recursive`, verify with `git submodule status --recursive`, and never use
  floating `--remote` updates. A plain source ZIP omits core submodule content.
- [ ] Label the build `2.1.8-UD (<app-sha>) GH (NIGHTLY)` using the upstream
  versionName metadata step, recording that bounded build-only modification.
  Do not change source package suffixes or the Thor profiling variant.
- [ ] Run `python3 .github/check_ci_integrity.py`, then use the author's task:

```sh
./gradlew :app:assembleGitHubNightlyRelease --no-daemon --max-workers=2
```

- [ ] Use the existing release signingConfig with owner-provisioned credentials.
  Keep R8, resource shrinking and release native optimization. Build the normal
  GitHub ABI set (ARM64, ARM32, x86-64), unless the release is explicitly labeled
  ARM64-only. Cross-build success does not certify execution on untested ABIs.
- [ ] Verify the APK with aapt/apksigner: `.nightly`, code49 or newer, min24,
  no debuggable/testOnly, correct persistent signer, actual ABIs/alignment,
  app/core source pair and SHA256. Reject `.perf`/`.dev` artifacts here.
- [ ] Generate a manifest and checksum alongside
  `melonDS-UD-nightly-2.1.8.apk`. Build/artifact delivery is separate from a
  GitHub Release and is not proof of installed-app migration.

## Task 3: Verify actual release behavior and update preservation

**Files:** reuse existing JVM/API/native/ART regressions; retain a dated release
report and bounded logs in the existing maintenance/evidence structure.

**Interface:** consumes the exact Task2 APK/digest; produces acceptance for that
binary, with failures/skips separated from the profiling candidate evidence.

- [ ] Run JVM/API, lint and relevant host sanitizer/CI integrity checks. Execute
  Android NDK controls on 4/16KiB AVDs. No green profiling result substitutes
  for release minification/packaging checks.
- [ ] On isolated AVDs install the actual UD release, exercise real launch,
  direct/ZIP ROM, Continue/SAVE/normal Exit/cold Continue on copies, valid and
  rejected savestates, import/cancel/retry with old cheat DB retained, backup/
  restore, lifecycle/input/audio/renderer and release WorkManager/JNI/SAF paths.
- [ ] Populate a first UD Nightly with synthetic data, rebuild a higher-code
  candidate with the same signer, install `-r`, and compare preferences,
  databases/cheats/layouts, grants, UID and save hashes before/after.
- [ ] Verify the expected signature rejection against the official-signer
  package on a separate disposable AVD without uninstalling it. Never run that
  experiment on the Thor or a personal-data package.
- [ ] Document `.perf` to `.nightly` as a separate optional migration, using
  exported copies and regranted URIs. Do not promise automatic private-data
  transfer. Keep Thor on its legacy identity during the authorized 47→49 test.

## Task 4: Publish only the accepted digest after approval

**Files:** [release body](FORK-RELEASE-DRAFT.md), both existing PR drafts,
candidate manifest and evidence index; no new source branch.

- [ ] Before publication recheck fork `main`, source/core pin, CI, signing
  identity and exact accepted APK digest. Put the Thor result and UD release
  AVD results in their own dated reports.
- [ ] After explicit authorization publish a prerelease named
  **melonDS Android UD Nightly**, using an immutable tag such as
  `ud-nightly-2.1.8-<app-sha>` with APK, SHA256SUMS and public build manifest.
  A tag is not a branch. Never force-move tags or publish signing/data fixtures.
- [ ] Keep publishing permissions confined to the approved promotion job;
  build jobs do not get contents-write permissions. Do not automatically
  enable production Play Store or upstream release workflows in the fork.
- [ ] For upstream PRs keep fork `main` → upstream `master`, core first.
  Once core is accepted, repin the app to the accepted upstream commit/URL.
  Keep the same upstream release variant; the author signs official Nightly
  with their own key. A fork-signed binary cannot become an official update.

## Current completion and handoff

Repository/package/signer/topology checks are complete. No UD release APK,
signing environment or release has been created by this plan. Existing Thor
2.1.8/49 remains the prepared local update. All implementation checks above
remain required before calling a UD Nightly built, accepted or published.
The owner requested waiting for the Thor connection and later explicit
publication approval; resume at those gates without creating branches.
