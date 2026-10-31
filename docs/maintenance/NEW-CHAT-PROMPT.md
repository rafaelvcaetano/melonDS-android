# melonDS Android — investigation and correction mandate

Work in the melonDS Android checkout supplied to this chat. The objective is to
find and correct demonstrable bugs in **this Android fork**, including latent
sequence/lifetime defects, while preserving user data and keeping storage bounded.
Do not assume the other chats or a model name supply project knowledge.

## Read first and establish ownership

Read `AGENTS.md`, `docs/maintenance/README.md`, `AUDIT-METHOD.md` in that directory,
`tools/README.md` and `tools/android-tests/README.md`. Follow links to the current
coverage and latest validation as needed; do not read every historic log by default.

Check branch, HEAD, index and dirty files in the app and `melonDS-android-lib`.
Work only on `main`; no new branch/worktree/PR. Preserve pre-existing edits and
human attribution. The preparation baseline was app `54bd57ec` / core `fd758240`; record
current identities rather than resetting to it. The app checkout initially had
uncommitted storage rules and older untracked audit files: none are disposable.

The sibling Sacred Gold Plus repository has a separate owner and active 1.3.1
work. Do not edit its sources, ROMs, build paths or tests as part of this audit.
Before Android builds, ADB or cleanup check concurrent owners and producers;
continue independent source research when a shared resource is busy. Do not
message another chat without human authorization for that coordination.

## First batch: research tied to two concrete problems

Use the existing 246-entry historical census and issue/PR coverage; do not create
a duplicate catalogue. Refresh full selected issue threads/comments, related PRs,
commit history and upstream/local diffs. Include closed/workaround/partial cases.
Official app/core GitHub trackers are primary; forums, Reddit and YouTube can
refine triggers, with author/date/version and explicit viewing limits.

Select two high-value problems. Good initial leads are app #1665 resume/black-screen,
shared-core #2653 RTC during sleep, and app #1651 DSi logical quota. These are leads,
not confirmed defects in the fork. App1678 JNI refs is already locally covered:
use it as a regression/control instead of redoing the fix. Distinguish feature
requests, ROM bugs, configuration errors and emulator defects.

For each selected problem:

1. Record app/core/APK identities, build mode, ABI, API, page size, renderer/JIT,
   settings, exact fixture identities and owner/caller/consumer.
2. State expected behavior from a contract or independent reference; show a
   reachable trigger and first wrong transition. Trace all relevant guards.
3. Try two substantive confutations and a neighboring negative control.
   Similar symptoms and suspicious source lines are not enough.
4. Reproduce at code level first. Reuse production tests and minimal seams.
   Use JVM/Robolectric, host sanitizer tests, NDK and instrumented Android according
   to what they can prove. Prefer authored synthetic/homebrew fixtures.
5. For a demonstrated bug, write/run the red regression, make a minimal correction,
   run green plus controls and independently read back state/output. Correctness
   fixes are authorized within this mandate; do not invent a new approval gate.
   A change requiring product policy, destructive data handling or an untested
   incompatible migration must be isolated with a concrete unresolved decision.
6. Review relevant upstream solutions and preserve contributor credit. No patch
   copying merely because a PR is closed or a newer commit exists.

## Latent bugs: sequences and invariants

Begin with at most four hypotheses across the two selected problems and small
state machines. Vary normal order, interruption, repeated actions and late
callbacks. Extend a concrete longer chain when evidence supports it; reduce the
failing sequence and keep seed/scheduler/configuration. Do not stop at four
transitions if the actual failure needs more.

Useful boundaries: Activity/Surface/display teardown and reopen; SAF partial
write/cancel/retry; pending callback across JNI owner destruction; microphone
source switch during capture; transaction rollback on malformed cheat imports;
JIT code/literal mutation, reset and fastmem remapping. Test properties such as
single ownership/release, no stale callback use, unchanged valid data after
failure, bounded buffers and equivalent interpreter/JIT results with an
independent oracle. A mock that creates the fault itself is not a reproduction.

Bound each fuzz/trace unit by time/output, record coverage limits and minimize
counterexamples. Use actual source and runtime where feasible, not only a model.
A host pass is not Android validation. Check HWASan runtime compatibility before
planning it; do not install tooling or change device images speculatively.

Use systematic-debugging before fixes, test-driven-development for the red/green
cycle and verification-before-completion for claims. Apply planning/delegation
only where it improves this concrete investigation; preserve main-only and human
scope. Use reverse-engineering tools for a specific binary question when source
is insufficient, not as a replacement for source analysis. This is a correctness
audit as well as a security investigation; do not filter out ordinary state bugs.

## Data, device and disk constraints

Do not uninstall or clear the personal app, replace signing identity, reset
settings or alter original ROM/save/BIOS/firmware/NAND/DB fixtures. Use copies
and separate diagnostic packages. No upstream message, PR, push, release or
personal-device installation is authorized by this initial mandate.

Check free space and leave at least 10 GiB after planned work. Keep current verified
and fallback builds per target, one canonical fixture copy, reduced reproducers
and small reports. No unbounded logcat/frame/RAM dumps. Inspect contents before
cleanup: old build folders have contained saves and ROMs. Delete only manifest-
reviewed inactive intermediates or exact duplicates with a verified retained
counterpart. Never delete old regression sources simply to make a suite green.

Do not restart an extended functional/visual campaign without need or duplicate
existing accepted runs. Visual/device checks are the last stage; state what
remains for the maintainer and what can be measured numerically now.

## Deliverables

Keep raw/private results under one `local/notes/android-audit-<date>/` directory.
For each case provide source URL/date/state, exact identities, minimal sequence,
expected/observed, first faulty operation, reachability, confutations, negative
control, proof level, red/green commands, correction/review and remaining checks.
Maintain one case ledger; link dated evidence instead of cloning reports.

Finish the first batch with demonstrated/corrected cases and explicit acceptance,
confuted cases, or precise blockers and next discriminating tests. Summarize
actual tests/skips, protected-data checks and storage removed/retained. Update
only the canonical index/coverage entries affected. No promise that every latent
bug is found; no solved claim for a case that only has a source signature.
