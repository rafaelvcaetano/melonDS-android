# Android bug audit method

The workflow in [AGENTS.md](../../AGENTS.md) is authoritative. Work on `main`,
preserve existing edits and contributor attribution, and keep the publication
gate. This method covers correctness, data integrity, resource lifetime and
performance in melonDS Android; a general security scan alone is insufficient.

## Establish the target before selecting a bug

Record app commit/dirty diff, core commit and submodule state. For binaries record
APK SHA-256, package, versionCode, signing certificate, build mode, ABI, API level,
page size, renderer, JIT/fastmem and relevant settings. A compiled Android target,
a host test and execution on Android are three different evidence levels.
Do not investigate a Mac frontend bug as if it were an Android defect.

Identify the owner of each path/process/output before writing. The ROM checkout,
Android app and core are distinct repositories sharing some fixtures/tools.
A symptom in one game is not sufficient to blame either the ROM or emulator.
Use an original synthetic/homebrew program when possible; otherwise use a
permitted canonical ROM in read-only mode and copies of its save data.

## Search and triage

Start with the [existing index](README.md), not another full census. Search the
Android app, Android core and shared-core issue/PR trackers; include closed,
merged, duplicate, reopened and workaround-only discussions. Read full selected
threads, linked patches, commit history, CI failures and prior local findings.
Use forums/Reddit/videos to refine triggers, with provenance and version limits.
An open issue may already be fixed locally; a closed issue may only have a workaround.

For each selected case preserve this compact record:

```
ID / origin / source URL / checked timestamp / issue or PR state
app + core identity / binary + configuration identity
owner + caller + consumer / prerequisites reachable through normal use
minimal sequence / expected behavior and its contract / observed behavior
first wrong transition / negative control / attempted confutations
proof level / reproducible command + seed / scope and limitations
fix + independent readback / before-after result / remaining Android/device check
```

States: reported → applicable → reproduced → corrected-in-source → verified on
identified Android target → accepted within stated coverage. Also record
confuted, already-fixed, partial and blocked-with-next-experiment. Count root
causes separately from reports and manifestations. Do not promote a model or
source signature to a runtime result.

## Look for latent defects by contracts and sequences

| Boundary | Sequence or invariant | Preferred oracle |
| --- | --- | --- |
| Activity, Surface, EGL and displays | Create → render → pause → destroy → resume; display removal or stale callback | Generations, owner identity, present/frame counts, thread and callback timeline |
| SAF, save and firmware writes | Open → partial write/failure → cancel → close → retry | Exact allowed byte ranges, checked error, no truncated adoption, hash/state after restart |
| JNI/native lifetime | Enumerate → callback/exception → cancel → teardown | Local/global reference balance, resource counts, valid owners, pending exceptions |
| Audio/microphone | Route or source switch while callback is pending, then stop/restart | Ring bounds, generation, saturation/silence, no stale callback access |
| Async import/database | Partial parse → cancellation/exception → retry | Transaction rollback, stable identity, one completion, no duplicate import |
| JIT/fastmem | Modify guest code/literal → invalidate → remap/reset → rerun | Registers, flags, memory and interpreter/JIT comparison, ABI/page-size controls |

Write a state machine with ownership and serialization boundaries. Explore
normal order, valid permutations, repeated operations, interrupted prefixes,
empty/full/boundary inputs and late callbacks. Start small; extend a concrete
sequence when evidence justifies it. Use equivalence classes and reduce failing
sequences rather than searching every combination without an oracle.

A pointer match, suspicious comment or analyzer warning is a lead. Trace the
caller and try to disprove reachability, check upstream guards and compare a
working neighboring state. A stub must not implement the behavior being blamed.
Exercise production translation units with minimal declared boundaries.

## Choose tools according to the question

- Source/call graph, git blame/diff and static analyzers find contracts and paths.
  Inspect existing lint findings before adding another large undifferentiated scan.
- JVM/Robolectric tests support frontend logic. ART/instrumentation is needed for
  actual Binder, URI grants, platform lifecycle and JNI behavior.
- Host ASan/UBSan, and TSan where supported, support native bounds and races;
  controlled schedules make a failing interleaving reproducible. They do not
  certify an Android driver or every real-time callback schedule.
- NDK tests in `tools/android-tests/` target Android ABI/API/page-size behavior.
  Record assumptions, skipped tests and the actual target. Reuse existing harnesses.
- [Android HWASan](https://developer.android.com/ndk/guides/hwasan) requires an
  appropriate ARM64 runtime; ordinary deployment guidance is Android 14+.
  An Android 13 device is not automatically compatible. The
  [NDK ASan guide](https://developer.android.com/ndk/guides/asan) marks classic
  Android ASan unsupported since 2023; do not promise it as a universally supported
  replacement. Host ASan remains separate evidence.
- Bounded fuzz/property tests suit parsers, buffers and state machines. Record seed,
  corpus hash, input/resource limits and minimized reproducer. Sanitizer success
  or coverage percentage alone proves neither correctness nor no latent bugs.
- Differential/metamorphic tests compare equivalent configurations and invariants;
  interpreter and JIT can share a bug. Use an independent expected result too.
- [Perfetto/system tracing](https://developer.android.com/topic/performance/tracing)
  can explain scheduling/lifecycle. Use short captures, exact workload and controls;
  CPU/PSS endpoints are not energy savings, peak memory or a leak proof.
- Reverse engineering is useful for a shipped APK/native binary whose source
  identity is uncertain. Prefer the source for ordinary repository analysis;
  tool presence does not prove a working decompiler or compatible sanitizer.

## Correct and verify

1. Obtain a failing original case for the product reason, with a negative control.
   Harness, permission and missing-fixture failures are not product regressions.
2. Make one root-cause fix; preserve interfaces/data or provide a lossless tested
   migration. Compare available upstream solutions and preserve attribution.
3. Run the original failing case, neighboring controls and appropriate regressions.
   Independently read actual output/state, byte ranges, serialized data and identities.
4. Run JVM/NDK/build/lint/ART checks appropriate to the changed layer. Repeat or
   broaden when new changes/failures justify it, not merely to accumulate checks.
5. Review the diff and test boundaries. State exact acceptance and outstanding
   hardware/visual checks. Never claim a fresh pass on an old binary.

## Preserve data and control publication

Use disposable copies of saves, firmware, NAND and databases; preserve originals,
WAL files, URI grants, settings, cheats and signing identity. Never clear app data,
uninstall the personal app, change signing key or reset settings as a diagnostic
shortcut. Use separate diagnostic packages and copied data. Device installation,
network service interaction, upstream comments/PRs and publication require their
own existing authorization; local investigation does not grant those actions.
Do not publish ROMs, BIOS, dumps, credentials, private DBs or personal logs.

## Storage and documentation lifecycle

One current verified build and one verified fallback per required target; pending
outputs are temporary until promoted or removed. Check free space and peak cost
before large work, leaving at least 10 GiB. Bound logs, traces, captures and fuzz
corpora. Keep one canonical large copy and refer to it.

Before deletion, identify producer/consumer and stop or coordinate them. Classify
individual files: source/fixture/original, unique evidence, verified build/fallback,
reproducible intermediate, or proven duplicate. `build`, `tmp`, age and an ignored
Git path do not establish disposability. Unknown ownership is preserved.

For eligible files write a small manifest with paths, byte counts, hashes,
producer, reason, retained canonical counterpart and current activity check.
Recheck identity before deletion, remove only the reviewed selection, then verify
protected hashes and record removed allocated bytes and free space. Do not clean
all of `/tmp`, global SDK/AVDs, source trees, Git history or backup folders.

Keep regression source tests that protect a distinct contract. Delete a test only
when the behavior is obsolete or a replacement proves the same failure/control;
record the replacement and run the relevant suite. Generated test output is a
different category. Preserve unique reproducers, reduced inputs and small result
summaries. Keep historical evidence dated and immutable, mark superseded status,
and link it from this index instead of duplicating it as a current report.
