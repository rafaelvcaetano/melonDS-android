# Independent review checkpoints

Base app `c543c3675ef28d99b77749d391d135397d8adb8c`, core unchanged.
Read-only reviewer; no build/device/commit performed by the reviewer.

## First review

Immutable private source manifests:

- `review-1`: `2456415df0e7f106569189634196c5ae1d2533680ce82901d9b22a0cdddbb2ae`.
- `review-worker`: `b33d763d62f5cb829e7cc7f5e723a882ec90d05ca7d316ff4e77e7f3bb243a03`.

No critical finding. Microphone control lock ordering and callback separation,
ROM list/object snapshots, and the parser/Room rollback contracts were judged
coherent. Initial assessment: not ready for final acceptance.

### BIOS classification read errors — confirmed

An enumerated source with unavailable size metadata, or a firmware whose header
cannot be read, was silently dropped. The result reported missing slots with no
named source error or Retry. This predates the refinement but belongs to its
error-reporting contract.

The coordinator reproduced both cases through the actual setup, filesystem
verifier, persisted settings, preference screen and result dialog. Five controls
passed: genuinely missing/unrelated files, an invalidated candidate retaining
old valid preferences, DS-only replacement retaining DSi, and complete replacement.
Two source-error assertions failed. The candidate now retains unreadable source
names without guessing their slot and offers Retry. Final validation is pending.

### Descriptor conversion hang — withdrawn, not fixed

The reviewer initially proposed that a finite-length pipe with a nonzero offset
would block inside `AssetFileDescriptor.createInputStream`, before parser
cancellation owned its input. The coordinator tested an actual remote provider
with a withheld prefix. API35 was already blocked in the XML parser's read; the
constructor-specific assertion was a false oracle, not a cancellation failure.

The reviewer rechecked Android13 and15 and withdrew the finding. The remaining
length field is zero while the constructor calls `super.skip`; its pipe fallback
calls a virtual read which sees that zero and returns EOF immediately. The
original analysis had missed virtual dispatch during construction.

Sources: [Android15 AssetFileDescriptor](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-15.0.0_r1/core/java/android/content/res/AssetFileDescriptor.java),
[Android13 AssetFileDescriptor](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-13.0.0_r1/core/java/android/content/res/AssetFileDescriptor.java),
[libcore FileInputStream](https://android.googlesource.com/platform/libcore/+/959d46e4195f96523b409b73fa41cab574fdd561/ojluni/src/main/java/java/io/FileInputStream.java).

No additional descriptor ownership machinery was added for this hypothesis.
The permanent regression observes the actual blocking phase and requires prompt
cancellation before the test provider supplies EOF. API33 runtime remains a
separate gate; source inspection is not reported as execution.

## Gates explicitly left open at this review

- Real WorkManager scheduling, unique KEEP work, stop/re-enqueue and notification
  removal. The initial provider suite calls production `doWork` with a builder
  adapter; it does not establish scheduler behavior.
- BIOS slow-copy lifecycle after navigation/rotation. Synchronous provider I/O
  can continue after view cancellation; no universal cancellation claim.
- Native UI large-text/IME/TalkBack/controller checks. A local-density override
  did not scale the actual dialog Owner; a calibrated system-font test and native
  screenshot subsequently reproduced the header failure and drove its correction.
- Actual Android microphone permission/driver behavior and physical Thor checks.
- Rendering quality, GPU/power cost and startup performance: no gain inferred
  from compilation, host tests, or static renderer research.
- Preliminary provider metadata queries and arbitrary uncooperative providers
  are outside the measured cancellation guarantees.
- NAND authenticity and universal SAF atomicity are not promised by this patch.

A follow-up must review the final frozen BIOS/UI changes and the new runtime
results before final acceptance. The original assessment is preserved rather
than rewritten as an approval.

## Second scoped review — BIOS and renderer fallback

Immutable manifest `review-2` SHA256
`701ed17b0c5e5894bb82cc867c14d2992b87c976ae9d622ca43c1b443482397d`,
17 files. Verdict: bounded approval, no new Critical/Important/Minor findings.
No reviewer edits, builds or device operations.

The earlier BIOS classification finding is resolved. The reviewer inspected
30 passing BIOS tests, including actual settings/verifier/Fragment integration.
Three lifecycle failures and the subsequent undeletable-partial failure are
addressed by serialized ownership, immediate view-dialog dismissal, cooperative
cancellation and unique noncanonical staging. Both streams close before rename;
rename refusal has no direct-copy fallback. Only the operation's own new document
is cleaned up. A later complete retry is allowed; accepting an incomplete canonical
NAND is not. Source-error warnings and Retry retain the unreadable file names.

The new renderer finding is also accepted within its scope: a null accelerated
factory result already caused the core to install Software, while the frontend
still performed an accelerated-type downcast. The app now records Software and
returns before incompatible settings calls, preserving the requested preferences.
The reviewer checked that runFrame and screenshot dispatch follow the actual enum.
Ten host and API33 Android boundary scenarios pass; production function bodies
are extracted unchanged, with typed factory adapters. The coordinator additionally
ran the original function on Android: three success controls pass, both initial
factory-failure cases abort with the expected UBSan invalid downcast.

This approval excludes native SAF permissions and Activity rotation, real GLES
shader failure/pixels, physical drivers, external atomic rename races, permanently
blocked providers, BIOS/NAND authenticity, and causation of the old acquired APK's
SIG11. Native SAF tests are being added; UI landscape/IME and the final combined
candidate require a separate final checkpoint. The first review and the withdrawn
AFD hypothesis above remain part of the record.


## Final combined review — approved within the tested scope

The reviewer verified all 56 files of immutable manifest
`dd1ca68bb93f6e23b0bde7fe9718100e56e20597849234063988dd073519cbdc`.
No new blocker was found in import/WorkManager, BIOS/SAF, scanning, microphone
or renderer integration. The final visual pass identified a real contrast
regression: the edge-to-edge light dialog inherited white status icons.
That finding was retained and corrected rather than waived.

The additional frozen ModifierFamilyDialog source has SHA256
`d5ffabb3ed696913a5673d3033ec768516bca6796c8f0fea05ad69670070663f`.
It changes status/navigation icon appearance on the dialog's own window, using
the same theme as its Surface, and restores the values on disposal. The reviewer
inspected native light/dark screenshots with IME open and after Done: readable
icons and restored commands. A stale offset-pipe explanation was also corrected
without executable changes; the test APK remained byte-identical.

Verdict: **bounded approval, no open Critical/Important/Minor findings**.
The approved app APK is
`72c3e4363f74da95e9e02df44fa3cba523ddf51ca2216e6992d220682926ec1b`;
test APK is
`6bd051e69cf85f3c3b8699541ab7a1d4416d2b18abceb4af05b0a60f66f47bcb`.
Final 267 JVM tests, zero lint errors, 52 ART passes plus 3 opt-in skips per API35
4/16KiB environment and real WorkManager passes were inspected by the reviewer.
The coordinator also repeated 52 ART passes, WorkManager and three actual GLES
runs on API33 with this final APK; installed APK bytes were independently hashed.
The corresponding logs preserve the distinction between test counts and skips.

The real GLES runs close the specific native shader-failure/frame/presentation
gate. They do not establish old Thor APK crash causality or accelerated quality.
The native SAF runs close the helper/provider gate, not full Android Activity
rotation during a slow external-provider copy. Physical mic/controller/screens,
spoken TalkBack, arbitrary uncooperative I/O, external rename atomicity and all
CPU/GPU/energy claims remain bounded as recorded in the report. This review does
not authorize PR publication. Original reviews and withdrawn findings remain
part of the record.
