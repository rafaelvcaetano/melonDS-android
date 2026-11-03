# Deep Security Scan remediation — 3 November 2026

> **Successor:** the
> [second independent scan](../2026-11-03-security-followup/README.md) adds 66
> new occurrences without rewriting or recounting the 35 below. This report
> remains the canonical evidence for the first completed campaign.

## Result

Deep Security Scan `4c41e000-6ba0-4453-aaa7-3cdbec542687` reported 35
occurrences against Android revision `6a9d7a1f19cfd44ba7fccdedec9f9196d3e04108`
and core revision `907849b3`. Every occurrence was re-read against the actual
entrypoint, propagation and sink. The resulting candidate contains a correction
for all 35: **35 corrected, 0 dismissed as false positives, 0 intentionally
accepted**. The [saved titles](FINDING-TITLES.md) and exact per-occurrence
correction/evidence mapping in [FINDINGS.csv](FINDINGS.csv) are kept separately
so the PR summary cannot silently rename or merge findings.

The subsequent [independent adversarial review](ADVERSARIAL-REVIEW.md) found
additional incomplete-fix and adjacent-boundary defects, all of which were
corrected before the final validation. Its only dismissed hypothesis was a
provider-exception denial of service already caught inside the exact
`DocumentFile` dependency. That post-fix false alarm is separate from the 35
scan occurrences, of which none was dismissed.

This is a closure statement for the 35 saved occurrences, not a claim that the
repository is vulnerability-free. The scan inventory contained 3,588 files;
74 were recorded as fully reviewed and 3,514 as not fully reviewed. The scan's
aggregate completeness field therefore must not be paraphrased as complete
manual file coverage.

## What a high-level security review adds

The original request correctly required fixing real bugs, documenting false
positives and measuring before/after behavior. A professional review also needs:

1. a trust-boundary model for ROMs, savestates, document providers, intents,
   settings backups, network replies and CI dependencies;
2. semantic validation after parsing, not only byte-length checks;
3. transactional restore or rollback when live emulator state is mutated;
4. compatibility controls proving valid legacy ROMs, saves and states still load;
5. defense in depth at both the deserialization boundary and dangerous sink;
6. immutable supply-chain identities and a check that prevents future drift;
7. an evidence chain that distinguishes a demonstrated failure, a source-backed
   reachability proof and an untested extrapolation;
8. an independent bypass review by someone who did not author the fix.

Those requirements shaped the implementation: the app retains a pre-load
savestate and restores it on semantic failure, native queues and GPU pointers
are validated before use, dangerous sinks keep local bounds checks, workflow
dependencies are pinned to commits, and the regression suite runs with native
sanitizers.

## Raw material, not pre-digested conclusions

The review started from these primary artifacts:

- `manifest.json`, `report.md`, `findings.json` and `coverage.json` from scan
  `4c41e000-6ba0-4453-aaa7-3cdbec542687`;
- the exact app/core source at the scanned revisions and the current working
  diff, including callsites outside each reported snippet;
- Android manifests, backup rules, network security policy and all workflow
  YAML files;
- raw host sanitizer output, Gradle XML test results, lint SARIF and native
  build logs;
- official Git refs for every GitHub Action tag and the official Gradle 9.7.1
  distribution checksum.

The saved scan artifacts remain local security-workbench evidence. This public
record identifies them by scan ID and filename without publishing workstation
paths or copying private state into the repository.

## Method and tools

- Each occurrence was triaged independently as confirmed, false positive or
  needing more evidence. No occurrence was closed merely because another one
  shared a file.
- Reproducers were written before the corresponding correction when a compact
  deterministic harness was possible. Native cases use Clang ASan/UBSan;
  Android cases use JUnit/coroutines plus the actual production class.
- Full consumer paths were inspected with `rg`, source review and call-chain
  tracing. Bounds helpers are intentionally small and independently compilable.
- Core integration was built with `tools/core-host-sanitize.sh`; host policy,
  save, JIT, DNS, NAND/FatFs and JNI suites were run separately.
- The Android verification matches CI: Nightly JVM tests, an ARM64 debug APK and
  lint. CI integrity is checked by `.github/check_ci_integrity.py`.
- Action tags were resolved with read-only `git ls-remote` against their official
  repositories. Gradle's SHA-256 was read from the official distribution server.
- A fresh read-only adversarial review was requested after implementation; its
  corrections and final verdict are part of the final commit evidence, not an
  input to the implementation.

## Before/after measurements

| Boundary | Before the correction | After the correction |
| --- | --- | --- |
| Savestate framing | A zero-length/short/cross-section record could fail to make progress or consume the next section | 34 boundary cases plus cross-section control pass under ASan/UBSan |
| Save flush ranges | Invalid `offset=33,length=4,size=32` reached overlapping/out-of-range copy behavior | The same request is rejected; the 32-byte control still persists |
| DNS | A truncated frame consumed stale bytes from the larger LAN buffer | Truncated frame and truncated label return no answer; normal overrides still resolve |
| DSi save geometry | A 65,536-sector size could wrap the 16-bit field and loop; sub-sector input made no progress | 65,535 sectors terminate; 65,536 and sub-sector sizes are rejected |
| Cartridge images | Header ranges were trusted after large allocation/padding | Truncation, wrap, secure-area and 512 MiB/32 MiB caps are enforced before use |
| Cartridge transfer state | Serialized position/length/direction reached fixed native arrays | Last valid word passes; unaligned, oversized and unknown-direction states fail |
| GBA flash | Bank/sector commands could address beyond save RAM | Both flash sizes, last byte, invalid bank and 4 KiB erase alignment pass |
| Wi-Fi TX | Descriptor/frame/sequence/WEP offsets were used without complete-span checks | Exact boundary passes; one-byte overflow and short WEP frame fail |
| DSP/graphics | Pipe/page spans, odd transfer lengths and zero/small scaling dimensions escaped local memory | Pipe, transfer, scaling and YUV scheduler-overflow controls pass |
| Scheduler/FIFO/SPU/GPU state | Restored callback IDs, cursors, counts and pointer indices were immediately trusted | 24 semantic invariant controls reject capacity, registration and cross-field violations |
| Cheat/XML import | Declared counts/entities/text could drive very large allocation/expansion | Count, text and code caps plus disabled DTD/external entities pass JVM regressions |
| ROM cache | Declared/decompressed size and a 32-bit URI hash could exceed quota or collide; the first transactional fix peaked at 40 bytes with a 32-byte cache | SHA-256 keys, declared/actual quotas and incremental pre-write eviction keep the measured peak at or below 32 bytes |
| Android launch | Exported parcelables/raw paths/null extras reached file authority or crashes | Only the granted `Intent.data` URI crosses the contract; malformed/null fields fail closed |
| Settings/layout restore | Type-only checks, malformed DTOs and interrupted replacement could poison or hide valid settings/layouts | Semantic allowlists, structural caps, canonical DTO round-trips and startup `AtomicFile` recovery pass JVM regressions |
| DSiWare metadata | Cleartext TMD and short title content were accepted | No production source is enabled by default; HTTP is rejected before I/O; exact 520-byte HTTPS transport and truncation controls pass |
| RetroAchievements | Credentials/tokens could enter URLs and platform backup | Sensitive calls use POST; auth storage is excluded from backup with migration coverage |
| CI supply chain | 27 mutable Action references plus one missing Gradle checksum were accepted | 27 full commit pins and the official checksum pass an automated zero-drift policy |

The CI policy's RED count is 28 controls: 27 mutable `uses:` references and one
missing `distributionSha256Sum`. Its GREEN count is zero violations. The native
security runner currently reports 163 passing checks.

## Occurrence disposition

The table is intentionally concise; the CSV preserves the exact title and
test/fix mapping for PR review.

| Severity | Count | Disposition |
| --- | ---: | --- |
| High | 12 | 12 corrected |
| Medium | 17 | 17 corrected |
| Low | 6 | 6 corrected |
| **Total** | **35** | **35 corrected; no false positives** |

The principal correction groups are:

- **Untrusted native formats:** cartridge headers, savestate sections, transfer
  state, scheduler/FIFO/SPU/GPU state, SRAM lengths, firmware layout and DSi NAND
  metadata now restore through explicit capacity and cross-field invariants.
- **Guest-driven native I/O:** Wi-Fi TX, DNS, GBA flash, cartridge B7, DSP pipes
  and graphics scaling now validate complete spans before pointer arithmetic or
  copy loops.
- **Android authority and resource limits:** exported launch data uses granted
  content URIs, ROM/cache/state allocations are capped, XML/DAT parsers are
  bounded, settings types are schema-checked and JNI fixed-width strings are
  bounded.
- **Secrets and transport:** RetroAchievements secrets are moved out of URLs and
  Android backup; DSiWare metadata fails closed without an authenticated source;
  slirp blocks host-loopback/private reachability.
- **Build trust:** GitHub Actions and the Gradle distribution use immutable,
  independently resolved digests, checked on every relevant workflow run.

## Verification result

The final code identity is app `a77ccaaa` with core gitlink `65952aec`. The
following commands passed on that exact source pair before the documentation-only
commit:

```text
bash tools/host-tests/security/run.sh
bash tools/core-host-sanitize.sh
bash tools/host-tests.sh                     # outside sandbox for JIT shared memory
bash tools/host-tests/dns/run.sh
bash tools/host-tests/nand-jni/run.sh
python3 .github/check_ci_integrity.py
sh ./gradlew :app:testGitHubNightlyDebugUnitTest \
  :app:assembleGitHubNightlyDebug :app:lintGitHubNightlyDebug \
  :rcheevos-api:test \
  --no-daemon --max-workers=2 -Pandroid.injected.build.abi=arm64-v8a
git diff --check
git -C melonDS-android-lib diff --check
```

Observed results:

- 163/163 focused native security checks pass with ASan/UBSan;
- full core host sanitizer build reaches 100%;
- host policy/save/JIT/NAND/RTC suite passes; the optional differential JIT test
  is skipped because no ROM may be stored in the repository;
- DNS and JNI NAND suites pass;
- 311 app JVM tests plus 1 rcheevos API test pass with zero failures, errors or skips;
- ARM64 Nightly debug APK builds;
- lint completes successfully and writes HTML/SARIF reports;
- CI integrity and both whitespace checks pass.
- three independent adversarial source reviews report no remaining concrete
  bypass in their assigned core, network/DSi and Android boundaries.

## Independent reproduction

Another reviewer can verify the result without trusting this report:

1. Check out the recorded app commit with its recorded core gitlink and initialize
   submodules recursively.
2. Run `.github/check_ci_integrity.py`; independently resolve every comment tag
   with `git ls-remote` and compare Gradle's checksum with the official server.
3. Run the focused security runner under Clang with ASan/UBSan and inspect that
   the invalid boundary immediately adjacent to each valid control is rejected.
4. Run the complete core host build and host suites. On macOS, allow the JIT test
   to create shared memory; a sandbox denial is an environment failure, not PASS.
5. Run the exact Gradle block above with JDK 21 and inspect the generated XML,
   APK and SARIF rather than relying on the console summary.
6. Review `FINDINGS.csv` against raw `findings.json`: every occurrence ID must
   map to a concrete code change and regression. Search the final tree for the
   original unsafe sink and attempt a bypass through alternate entrypoints.
7. Verify the commits' signatures/hashes and that no ROM, BIOS, save, credential,
   local plan or scan workbench artifact is present in the outgoing range.

## Limits

- No personal ROM, BIOS or save was used. No physical Thor installation or game
  launch was needed or performed for this remediation.
- The optional JIT differential test needs an external ROM and remains skipped;
  all synthetic JIT invalidation cases pass.
- Host and JVM/NDK evidence does not replace broad real-device/game compatibility.
- The DSiWare fix deliberately disables network metadata import until a trusted
  HTTPS source is configured. It does not silently fall back to the legacy HTTP
  endpoint.
- Blocking slirp host/private targets is an application policy; it is not a claim
  that libslirp itself is a security boundary.
- The scan had partial file-level coverage. Future changes or unreviewed files can
  introduce new findings and require a new scan/diff review.
