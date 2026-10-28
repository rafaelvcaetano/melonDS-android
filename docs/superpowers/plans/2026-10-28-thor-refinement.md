# Thor refinement implementation plan

> **For agentic workers:** Use superpowers:executing-plans for sequential implementation and a fresh final review. Follow the repository's main-only workflow; no branch/worktree. Checklist items are evidence gates, not promised outcomes.

**Goal:** Preserve behavior and data while correcting demonstrated Android defects, reducing measured unnecessary work, and refining BIOS/cheat UI in the existing melonDS design.

**Architecture:** Keep Kotlin/Room/SAF and the current C++/Oboe frontend and pinned core. Correct ownership and errors before UI polish. Separate Android regression execution from physical Thor measurements; use the existing canonical acquisition instead of cloning it.

**Tech Stack:** Kotlin coroutines, AndroidX WorkManager/Room, Compose Material 2, AppCompat Preferences, JNI/C++/Oboe, JVM/Robolectric, Android ART/NDK and Perfetto.

**Spec:** [2026-10-28-thor-refinement-design.md](../specs/2026-10-28-thor-refinement-design.md).

## Global constraints

- Work directly on app/core `main`; preserve other chats' changes and original contributor attribution.
- No PR publication without explicit user authorization; no push or installation claimed from local tests.
- Preserve original ROMs, BIOS, saves and settings. All runtime writes use fixture copies.
- Keep existing API/ABI support and storage formats; no dependency/framework upgrade in this refinement.
- Retain current verified build and previous verified fallback; a pending candidate is temporary. Keep at least 10 GiB free after planned work.
- One build/AVD/device owner. Wait for the central cleanup to release outputs; no concurrent benchmark/build.
- Existing 224 JVM/39 ART tests are historical. New claims require fresh command output for the reviewed source.

## Review focus

1. Provider open succeeds but a later read blocks and ignores interrupt: Task 1 must test actual Android descriptor behavior.
2. Cancellation arrives between acquiring a resource and registering cleanup: Task 1 must release it exactly once and preserve cancellation.
3. Mic configuration/cleanup overlaps JNI toggle: Task 2 must exercise source 2 lifecycle, not only an atomic flag.
4. SAF returns missing/invalid files or loses permission after enumeration: Tasks 3–4 preserve prior state and expose retryable error.
5. Long localized labels, font2.0 and IME hide actions or duplicate accessibility focus: Task 5 checks rendered bounds and semantics.

## Task 0: baseline and coordination

Files: maintenance `2026-10-28-refinement/STATUS.md`, private notes under
`local/notes/refinement-2026-10-28/`; existing Thor/Daybreak reports are references.

- [x] Read AGENTS, issue inventory, bugfix reports and unpublished app/core drafts.
- [x] Inspect coordination chats and existing Thor capture before ADB; no new device access.
- [x] Verify APK hash against the prior validation manifest, distinguish source from installed APK.
- [x] Start authorized rendering research chat in read-only mode; use its returned evidence.
- [x] Search targeted GitHub precedents and primary Android/Perfetto/Daybreak documentation.
- [x] Obtain central cleanup completion and check current branch/status/free-space again.
- [x] Read the final rendering report, retain only reproducible candidates in the queue.

## Task 1: cancellable cheat import, DB-ANDROID-02

Files: `app/src/main/java/me/magnum/melonds/common/workers/CheatImportWorker.kt`,
`common/cheats/CheatDatabaseImportEvents.kt`, shared
`app/src/sharedTest/kotlin/me/magnum/melonds/common/workers/CheatImportIntegrityContract.kt`,
existing JVM/Android wrappers and a test-only blocking provider in `app/src/androidTest/`.
Keep `impl/RoomCheatsRepository.kt` transaction contract unless a new regression requires change.

Interface: URI→owned readable resource→existing parser Flow→`replaceImportedCheats`.
Cancellation must reach ownership while parser read is blocked. No schema/API change;
preserve parser event order and bounded buffering. Select the smallest I/O adapter
after the descriptor experiment below, then record its exact signature in the ledger.

- [x] Promote the private Daybreak close-aware fixture into a permanent contract: wait for read entry, cancel, assert close/job termination, exact database equality, then successful second import.
- [x] Run focused `CheatImportIntegrityTest`/`CheatImportEventsTest`; retain failing assertion and valid DAT control before product edits.
- [x] Run a test provider backed by a pipe/descriptor on Android. Test cancellation during open, during blocked read and during descriptor-to-stream ownership transfer. Record whether signal, close and interrupt actually unblock each path; unsupported providers get an explicit limitation.
- [x] Implement resource cancellation and cleanup with no detached orphan coroutine. Do not rely on `CoroutineWorker.onStopped` override (final), `CancellationSignal` for future reads, or `use` that can execute only after the blocked operation returns.
- [x] Repeat shared Room tests, real WorkManager stop/re-enqueue with unique KEEP work, notification removal and provider failure. Exact rows/custom cheats preserved; no thread/descriptor left active. Timeout is a failing oracle with deterministic cleanup.
- [x] Review the fix and record commands/results/limits; integrate the reviewed campaign on main. No PR.

JVM focused command, with JDK21 and existing SDK:

```sh
sh ./gradlew :app:testGitHubNightlyDebugUnitTest --tests '*CheatImportIntegrityTest' --tests '*CheatImportEventsTest' --max-workers=2 --console=plain
```

Expected after correction: all selected tests pass, including actual rollback and
second import. A passing parser-only test does not close the WorkManager gate.

## Task 2: microphone ownership, DB-ANDROID-01

Files: `app/src/main/cpp/MelonDSAudio.cpp`, `MelonDSAudio.h`, JNI callers only if
required; permanent regression beside `tools/host-tests/microphone/lifecycle/`.
Reuse the private `MicStateRaceProbe.cpp`
as evidence, not an unreviewed source patch.

Interface: existing `userEnableMic()`, `userDisableMic()`, `readMic(s16*, int)`
and lifecycle entrypoints retain behavior. Document each field's owner and
synchronization. No blocking mutex in Oboe realtime callbacks.

- [x] Read every caller of configuration/start/stop/read and classify threads before choosing synchronization.
- [x] Preserve TSan red on the production translation unit and sequential negative control. Add concurrent source0/source1 toggles, config change, source2 fake-stream start/stop/cleanup.
- [x] Fix flag and lifecycle ownership; keep buffer policy and sample gain unchanged.
- [x] Run focused native/TSan tests and existing PCM/source-switch regressions. Expected: no races, no stale stream/cursor, correct silence when disabled.
- [x] Build Android NDK and run native source-switch/lifecycle regressions.
- [ ] Exercise the physical microphone driver with permission granted/denied and pause/resume/reset on Thor.
- [x] Review the microphone fix and preserve RED/GREEN evidence; integrate with the reviewed campaign. Do not claim all audio issues closed.

## Task 3: startup and ROM scanning

Files: `impl/FileSystemRomsRepository.kt`, `ui/romlist/RomListViewModel.kt`,
`ui/romlist/ui/RomListScreen.kt` only if status UI needs an explicit distinction;
new `app/src/test/java/me/magnum/melonds/impl/FileSystemRomsRepositoryTest.kt`.

Interface: retain `getRoms(): Flow<List<Rom>>`, `rescanRoms()` and
`getRomScanningStatus(): StateFlow<RomScanningStatus>`. Snapshots must be stable;
an error cannot leave a spinner active or disable future refreshes.

- [x] Fixture provider with cached ROMs and observable query/open counts; synchronize slow scan and failure without fixed sleeps.
- [x] Tests `scanFailureRestoresIdleAndAllowsRetry`, `cachedRomsRemainUsableDuringSlowScan`, `overlappingRefreshDoesNotReportIdleWhileScanning`, and stable previously emitted list after a new ROM. Run red cases individually, retain controls.
- [x] Correct only reproduced state/ownership defects first. Avoid conflating a background scan with TTID. Add narrow trace markers/counters only when they answer an unresolved cost question.
- [ ] Measure identical synthetic NDS/ZIP/7z trees and repeated refresh on optimized comparable APKs. Cache optimization proceeds only if query/open/decode work is measured and add/remove/modify/permission-loss controls stay correct.
- [x] Separate small persistence regression: toggle sorting, reconstruct ViewModel from real preferences, assert selected order survives. Fix saving old order only after red.
- [ ] Android UI/provider execution and later Thor cold/warm startup observation with exact installed APK identity. Report startup-ready and scan-end separately.

## Task 4: trustworthy BIOS setup results

Files: `common/BiosGuidedFolderSetup.kt`,
`ui/settings/fragments/CustomFirmwarePreferencesFragment.kt`,
`ui/settings/viewmodel/CustomFirmwareViewModel.kt` if lifecycle ownership moves,
`domain/services/ConfigurationDirectoryVerifier.kt`, values/values-it strings,
`app/src/test/java/me/magnum/melonds/common/BiosGuidedFolderSetupTest.kt` and
new integrated settings tests.

Interface: `BiosGuidedFolderSetup.Report` keeps slot/source information; extend
with explicit copy/permission failure only if callers are updated exhaustively.
Directory preference changes require actual console validation; DS mode need
not block on missing optional DSi files. DSi completeness includes NAND and DS prerequisites.

- [x] Red tests: no NAND with three DSi slots, existing zero-size BIOS, read/output failure, null stream, revoked permission, failed partial deletion, ambiguous DSi pair, unchanged valid old preferences.
- [x] Fix result classification and preference commit criteria. Preserve user files and disclose filename-based assignments.
- [x] Test actual Fragment view destruction/recreation during slow copy on JVM, serial ownership, cancellation and cleanup; Android Binder/SAF tests verify actual PFD behavior.
- [ ] Complete physical/full Android Activity rotation during a slow external-provider NAND copy; a JVM Fragment test does not close this separate gate.
- [x] Apply Impeccable clarify/harden: concise setup explanation, grouped DS/DSi outcomes and next action; use existing theme/components and localized resources.
- [x] Validate JVM and Android SAF paths on synthetic files; real BIOS copies only for final launch checks. Verify original hashes unchanged.

## Task 5: native cheat UI refinement

Files: `ui/cheats/ui/ModifierFamilyDialog.kt`, `FolderListScreen.kt`,
`ui/common/Colors.kt` only when reuse needs extension; existing
`CheatSearchScreenTest.kt`, `ModifierFamilyDialogTest.kt`,
`CheatSearchNavigationTest.kt`.

Interface: preserve `onConfirm(value, level)`, `onDisable`, search targets and
query persistence. A search-result click never enables a cheat.

- [x] Read Impeccable craft-floor immediately before UI edits. Capture baseline from the current APK, not the older Thor screenshot or a browser.
- [x] Fix token inconsistency with existing `melonButtonColors` and `melonOutlinedTextFieldColors`; no palette overhaul.
- [x] Ensure result rows have at least48dp effective target and radio option uses one selectable semantic node with label/state; no duplicated TalkBack actions.
- [x] Test long title/actions on320dp portrait and Thor landscape, fonts1.0/1.3/2.0, IT/EN and IME visible. Adapt action placement only where measured bounds fail.
- [x] Re-run search/navigation/selector tests and inspect paired light/dark Android screenshots.
- [ ] Perform manual TalkBack exploration and physical gamepad checks.

## Task 6: rendering experiment selection

Files: read-only research in `local/notes/rendering-audit-2026-10-28/`;
product-file changes are selected from that report only after an experiment
demonstrates the cause. Core remains pinned until a tested fix warrants changing it.

- [x] Consume report from authorized rendering chat; check against current SHA and previous renderer changes.
- [x] Select one correctness or redundant-work hypothesis, retain no more than three candidates. Write a separate focused implementation plan with exact files/entrypoints/test oracle.
- [ ] Test same scene/frame/options at6x first; compare 2D-only/3D/mixed scenes. 1x is diagnostic, not equivalent quality.
- [ ] Verify images and semantic behavior before CPU/GPU/energy claims; count guest frames independently of SurfaceFlinger.
- [x] Report unsupported GPU counters and missing hardware acceptance. Do not reduce texture slots or scaling solely to report lower resource use.

## Task 7: integrated acceptance and retention

- [x] Freeze source/diff, APK hash/signature/native Build IDs and exact fixture/config manifests for correctness.
- [ ] Prepare paired optimized/profileable builds for future performance work; debug/sanitizer timing is not a performance result.
- [x] Run relevant full JVM suite, ARM64 APK and lint; native suites for changed native code. Commands follow `docs/maintenance/2026-10-28-android-bugfixes/VALIDATION.md` from repository root.
- [x] Run Android shared regressions on API35 4/16KiB, and API33 replica scenarios. Assign explicit serial each time and qualify API/ABI/page size.
- [ ] Thor acceptance only in a coordinated window: protect originals, backup/verify configuration, select separate candidate package if signatures/data require it, confirm startup/BIOS/cheat/mic/screens/controller, raw Continue→save→cold restart on copies.
- [ ] Performance pilot:10 paired cold/warm launch runs, repeated fixed gameplay windows after warmup, A/B order alternated. Decide sample size from observed variation; keep traces/units/raw exclusions. No artificial threshold that trades correctness for speed.
- [x] Fresh independent review of the immutable combined diff; fix important findings with red/green evidence. Record every skipped gate.
- [x] Promote candidate only after its checks; retain prior verified fallback and small evidence. Remove own reproducible intermediates per AGENTS and update canonical paths before cleanup.
- [x] Update STATUS/bozze with verified results and review `git diff --check`; save the reviewed local campaign on main. No replacement PR without user authorization.

## Daybreak follow-up brief

Use the existing audit as input, not another broad scan. Supply exact immutable
app/core SHAs and diff, DB-ANDROID-01/02 probes and new green evidence. Ask for
at most two residual boundaries: (1) resource acquisition/cancellation/Room
rollback and WorkManager retry; (2) microphone stream ownership at config/teardown.
If both close, investigate BIOS SAF copy/preference commit separately.
Require caller chain, reachable input, minimal failing control, impact and limits.
No product writes, build/test collision, ADB, publication or extra agents from
the reviewer; private notes only. Distinguish credible source concern from
executed Android finding. Do not classify general performance or visual polish
as a security vulnerability. The primary coordinator reproduces and accepts fixes.


## Execution notes

The user confirmed that the Thor may be disconnected; physical acceptance and
power/GPU measurements need a later coordinated session. Runtime work now uses
explicit AVD serials only. The existing API33 replica chat has completed its
acquisition/replay phase; its native Compute failure and Software1x fallback are
separate from this candidate's correctness checks.

The first independent review is preserved separately. A source-only concern
about blocked AFD construction is being checked against actual Android behavior:
API35 reaches parser read for a finite-length offset pipe, rather than blocking
in createInputStream. Do not manufacture a RED result for the proposed mechanism.

The original local-density UI test did not actually scale the dialog's Android
Owner. Replaced with system font calibration and full-bounds assertions; genuine
RED matches the native320dp/font2 screenshot, then the compact header passes.


## Final execution scope

The demonstrated defects above are corrected; remaining unchecked items are
explicit follow-up gates, not implied passes. In particular, normal startup
profiling/cache optimization, 6x GPU experiments, physical microphone/controller/
display acceptance, power measurements and full Android Activity rotation with
a slow external provider require their own controlled runs. The user may keep
the Thor disconnected while the current Mac work is completed.

The API33 ROM replica is used by the V1.3.0 chat. This campaign created a separate
blank temporary API33 AVD and reused its own API35 4/16KiB AVDs. No writes to the
shared ROM AVD or physical device. The earlier descriptor-constructor hypothesis
was withdrawn after native Android counterevidence; no speculative fix was added.

Additional demonstrated renderer correctness defect: failed accelerated factory
left the frontend's enum incompatible with the core's installed Software object.
Seven sanitizer failures became ten passes. The separate original-homebrew
Android test also passes actual GLES3.1 shader rejection, two advancing phases,
presentation and unchanged requested settings. This is independent of the
unexecuted compute-visibility performance plan.
