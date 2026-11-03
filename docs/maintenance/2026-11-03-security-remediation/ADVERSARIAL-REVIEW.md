# Post-remediation adversarial review — 3 November 2026

## Scope and disposition

This review started only after a first correction existed for every occurrence
from Deep Security Scan `4c41e000-6ba0-4453-aaa7-3cdbec542687`. It did not
reclassify or merge the 35 scan occurrences: all 35 remain confirmed and
corrected. Its purpose was to find bypasses, incomplete fixes and adjacent
consumer failures in the final app/core pair.

The review used three independent source passes: core state/memory invariants,
network/DSi boundaries, and Android authority/persistence. A reported residual
was accepted only when its entrypoint, hostile value and failing consumer were
identified. One additional hypothesis was dismissed after inspecting the exact
`androidx.documentfile:documentfile:1.1.0` implementation: `DocumentFile`
already catches provider exceptions in the questioned calls, so the proposed
provider-exception denial of service was not reachable as described. This is a
false alarm from the post-fix review, not one of the original 35 findings.

## Residuals found and corrected

| Area | Demonstrated residual before the final correction | Final control and evidence |
| --- | --- | --- |
| Wi-Fi MP reply | The first bounds helper rejected the descriptor-free default reply used by slot 5 phases 10/11, while a broad exception could have admitted unrelated phases | The default is limited to that slot/phase pair, the dedicated busy bit is centralized, unknown phases fail, and valid/invalid phase controls pass |
| Wi-Fi memory/state | Two-byte/four-byte aligned guest addresses reached typed 32/64-bit host loads; `INT_MIN` countdown and client-mask bit zero escaped the first semantic checks | Byte-safe loads plus UBSan alignment controls pass; countdown/mask boundary tests reject the hostile states |
| Wi-Fi replies | Stack-backed reply/ACK packets were not fully initialized before transmission | Both buffers are zero-initialized; normal packet controls remain green |
| DLDI | The built-in image has 548 initialized bytes but a 1,024-byte declared footprint, which the first equality rule rejected | Initialized length, declared footprint and reserved capacity are separate invariants; the footprint is zeroed before copy and relocation remains bounded |
| Cartridge OOM | The GBA path and one loader allocation still assumed allocation success | NDS/GBA/loader allocation paths use checked `nothrow`; bounded success and oversize rejection controls pass |
| DSi snapshot | A nominally bounded 512 MiB image could still force an eager immutable copy | The immutable import snapshot is capped at 128 MiB before allocation; exact-boundary and one-byte-over controls pass |
| DNS QNAME | Four 63-byte labels followed by another label underflowed the remaining presentation-name capacity; ASan reported a stack-buffer-overflow | Capacity is checked before subtraction/copy; the same over-255-byte name is rejected and ordinary overrides still resolve |
| LAN protocol | The first network fix did not validate every ENet header, sender, payload length, AID, player-list authority or zero-length control packet | A central wire validator plus consumer checks enforce magic/type/sender/AID/actual length; malformed packet controls pass |
| LAN player wire data | Raw C++ `Player` objects crossed the wire, admitting invalid enums/bools, padding disclosure and lifetime ambiguity | A zero-initialized integer-only `PlayerWire` format is decoded only after enum/bool/ID validation; authenticated player-list and ownership paths are bounded |
| Android ROM launch | A granted URI copied into a custom extra and firmware shortcuts using an implicit action still left alternate authority/interception paths | Exported ROM launch accepts only granted `Intent.data`; ROM and firmware shortcuts target `EmulatorActivity` explicitly; argument/component tests pass |
| Cheat XML | A byte scan for `DOCTYPE` could be bypassed by parser syntax, and text was capped per field but not per game | The SAX lexical callback rejects actual DTD declarations, entities remain disabled, and per-game aggregate text is capped; comment/CDATA controls remain valid |
| Settings values | Type checks alone admitted invalid enum/range values, derived timestamps at `Long.MAX_VALUE`, a malformed skipped-version string and a non-transferable camera document URI | Persisted UI enums/ranges are allowlisted; derived update/hash metadata and authorization-bearing URIs are excluded; the GitHub update boolean remains round-trippable |
| Controller restore | A bounded but malformed controller file replaced a valid mapping and then silently degraded to defaults | The real serial DTO is structurally capped, decoded, range-checked and canonicalized before `AtomicFile`; malformed input leaves the existing mapping byte-for-byte intact |
| Layout restore | The first filter used an obsolete target field, admitted incomplete/Bogus DTOs, could hide every valid layout, had no structural caps and was not crash-recoverable | Current display DTOs determine the target; complete DTO round-trip, enum/UUID/range/count/finite-alpha controls and per-record tolerant loading are enforced; `AtomicFile` recovery runs at startup and before consumers |
| Filtered layout merge | Internal/external restore could replace the other target or grow beyond the global count/byte budget | Non-target variants are preserved by layout ID; source, existing and merged counts are capped at 256 and the final canonical byte size is checked before publication |
| ZIP cache | Transactional extraction fixed premature eviction but a full cache plus an unknown-size entry peaked above its configured budget | Before: the regression measured 40 bytes on disk with a 32-byte limit. After: space is reclaimed incrementally before each write and the measured peak is at most 32 bytes |
| Backup domain | Platform backup exclusions covered shared preferences but not every file-domain migration path | Both cloud/device rules exclude the file domain and private/derived configuration remains in the explicit validated export path |
| CI regression coverage | The native security runner existed only as a local command | It now runs in the native CI matrix, and the immutable Action/Gradle policy remains separately checked |

## RED/GREEN evidence retained in the tests

The following final regressions were deliberately run against the immediately
preceding implementation before their correction:

- unknown ZIP entry: **RED**, `cache peaked at 40 bytes with a 32-byte limit`;
  **GREEN**, the same two-chunk extractor never exceeds 32 bytes;
- malformed layout DTO: **RED**, a target-valid layout with `type=BOGUS` was
  written and caused the repository to hide valid layouts; **GREEN**, it is
  rejected and both internal/external controls reload;
- interrupted layout replacement: **RED**, a lone
  `layouts.json.restore.previous` was not recovered; **GREEN**, legacy and
  `AtomicFile` interruption states restore before parsing;
- DNS presentation name: **RED** under ASan with a stack-buffer-overflow;
  **GREEN**, the same label sequence returns no answer;
- unaligned Wi-Fi reads: **RED** under UBSan at the typed load sites; **GREEN**,
  byte-copy helpers preserve the values without an alignment violation.

Other residuals were established by source-level reachability plus an adjacent
valid/invalid regression where executing the unsafe pre-fix behavior would have
required a full guest, provider or hostile ENet peer. Those cases are not
mislabelled as dynamic exploit demonstrations.

## Independent verification

A reviewer who did not author the changes can verify this review by:

1. checking every row above against the final callsite and its immediate
   consumer, not only the helper test;
2. running `tools/host-tests/security/run.sh` with ASan/UBSan, the DNS and JNI
   runners, the full core sanitizer build and `tools/host-tests.sh`;
3. running the complete Nightly JVM suite, ARM64 APK assembly and lint with JDK
   21, then reading the XML/SARIF artifacts rather than relying on this prose;
4. rerunning the five RED cases against the parent/pre-fix patch where safe;
5. comparing `FINDINGS.csv` with the raw local `findings.json` and confirming
   that all 35 occurrence IDs have a fix and regression;
6. checking the full outgoing ranges against both remotes and confirming that
   no ROM, save, BIOS, credential, scan workbench or assistant-only plan is in
   either commit range.

## Limits

The review does not claim universal game compatibility or exhaustive coverage
of the 3,588-file scan inventory. Several native tests exercise the same small
bounds helpers used by production rather than a complete Wi-Fi/DSi/ENet guest
session. No personal ROM, save, NAND or physical Thor state was used. Those are
explicit validation limits, not unresolved instances of the 35 saved findings.
