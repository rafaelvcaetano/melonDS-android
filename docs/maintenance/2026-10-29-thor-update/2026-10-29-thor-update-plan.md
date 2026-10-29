# Thor in-place update and acceptance

## Intended outcome

Update the installed modified Nightly from 2.1.4 to 2.1.5 on the same Android package and signing identity. Preserve every original ROM, raw save, savestate, cheat, controller/layout setting, document grant and Nintendo Wi-Fi configuration. No uninstall, data clearing, publication or branch creation.

## Sequence

1. Record device, APK, source and signing identities; coordinate exclusive device access.
2. Verify complete private-data snapshots, SQLite integrity and externally referenced originals. Reuse existing copies only after matching SHA-256. Keep rollback APK and immutable originals.
3. Record old-build startup and an isolated gameplay fixture with settings, compilation and thermal context. Do not run benchmarks during backups.
4. Build optimized, non-debuggable Profiling APK, increasing versionCode 45 → 46. Run JVM suite and variant lint/build. Verify signature/package/ABI and schema compatibility.
5. Snapshot immediately before install. Use only an in-place replacement; verify preserved data before and after first launch. Record any legitimate version/runtime metadata changes separately.
6. Repeat startup and gameplay measurements. Exercise functional flows on disposable fixtures or separate test storage; never import destructive test data into the user's cheat database. Validate normal SAVE and cold Continue on a copy.
7. Review evidence independently, report results and limitations, update draft PR validation. Clean only reproducible intermediates. Retain unique backups/evidence and current plus fallback builds.

## Measurement limits

The old installed source revision is not attested. Its APK and signing identity are pinned. Sequential old/new deployment cannot satisfy alternating A/B ordering; record that limitation rather than calling the measurements randomized pairs. An observed whole-app difference does not prove a particular patch caused it. Stable thermal or energy improvements require suitable sustained controls and validated counters. Charging battery measurements do not establish energy savings. Host tests do not replace Android device execution.

## Review Focus

- No original asset overwrite, lost SAF permission, cheat reset, schema fallback or WFC identity change.
- Snapshot acquisition occurs before target providers initialize; archive hashes, WAL files and SQL integrity are checked independently. A helper failure must never be reported as app acceptance.
- APK provenance and build mode must match the measurement claim; no unsupported CPU/GPU/energy attribution or false guest-FPS measurement.
- Separate verified results, baseline limitations and tests not performed. Do not reuse previous-device results as new-build acceptance.

Private execution ledger and originals: local/notes/thor-update-2026-10-29/PROGRESS.md. Private material must not enter Git.
