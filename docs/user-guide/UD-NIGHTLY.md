# UD Nightly: features, fixes and updates

This guide describes the fork's changes relative to official Nightly app `c42995ca` and core `431ab4bd`. Release notes identify the exact source and APK that a download contains. Source changes are not a test result for an unbuilt APK.

## BIOS and firmware setup

Guided setup checks the files selected for DS and DSi mode and reports the results separately. A failed or incomplete copy is rejected before it becomes the active setting. Errors identify the failed step and allow retry.

Provide your own valid dumps. File checks do not authenticate a dump or provide missing system files. DSi setup and DS direct boot have different requirements; a successful DS game does not validate a DSi NAND.

[Setup implementation and tests](../maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md#1-funzioni-aggiunte-nellapp) · [Incomplete-copy checks](../maintenance/2026-10-28-refinement/VALIDATION.md)

## Online connection settings

The app exposes the firmware's three Nintendo WFC connection slots. You can name each connection, change its primary and secondary DNS, and enable or disable it. The pause menu provides connection toggles. Changes are written to the applicable firmware configuration.

This is configuration support, not a new multiplayer implementation. Game compatibility, access-point behavior and server availability require separate testing. Keep a backup of custom firmware before editing it.

[Wi-Fi feature scope and tests](../maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md#1-funzioni-aggiunte-nellapp)

## Cheat search and import

Search includes cheat names, folders and recognized selector options. Results retain their location so you can navigate to the matching entry. Search does not turn a cheat on.

For supported Action Replay code families, grouped selectors replace long lists of similar codes with a species, level or nature choice. Compatibility depends on the ROM revision and code set; a selector does not make arbitrary codes safe for another ROM.

XML and DAT import have size and work limits. A failed, cancelled or invalid import preserves the previous cheat database; retries are supported. DAT support already existed upstream and is not a new format introduced by this fork.

[Search, selector and contributor details](../maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) · [Database preservation and retry](../maintenance/2026-10-28-android-bugfixes/VALIDATION.md) · [Additional parser limits](../maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)

## Settings and controls

The existing upstream settings/layout backup manager is available from the app. Restore validates imported settings, and controller settings are refreshed afterward. Keep game saves and system files in a separate verified backup: a settings export is not a complete backup of every file the emulator can use.

Additional options include hold to fast-forward, combination hotkeys and fast-forward mute. Sleep/wake handling follows the emulated lid sequence. Optional clock synchronization only moves time forward and is off by default. Fast-forward mute does not fix every audio timing or resampling problem.

[Features and original contributors](../maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) · [Save and lifecycle regressions](../maintenance/2026-10-28-main/VALIDATION.md)

## What the fixes change

| Area | Previous behavior | Corrected behavior and evidence |
| --- | --- | --- |
| Cheat imports | A failed import could replace or damage the old database | Import is committed as a transaction; parse/write/cancel failures retain old contents. [Checks](../maintenance/2026-10-28-android-bugfixes/VALIDATION.md) |
| Save writes | Failures could truncate a file or lose pending writes | Existing non-truncating access is preferred, failures are reported and pending work can be retried. Some provider fallbacks retain limits. [Checks](../maintenance/2026-10-28-main/VALIDATION.md) |
| NAND save replacement | Failed replacement could leave the previous generation inconsistent | Staging, rollback and recovery preserve the previous generation in tested failure cases. [Before/after](../maintenance/2026-11-03-nand-rename-fault/README.md) |
| ROM library | Errors could leave scanning active or mutate lists already shown | Scans are serialized, state returns to idle on failure and emitted lists remain stable. [Evidence](../maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md) |
| Input, audio and display | Interrupted input, source changes and renderer failure could leave stale state | Session ownership, input release, bounded microphone samples and actual-renderer fallback are corrected. [Evidence](../maintenance/2026-10-28-android-bugfixes/REPORT.md) |
| Files and native state | Some sizes, indices and work requests reached native consumers unchecked | Input limits and state validation reject affected paths before buffer access or unbounded work. [Individual details](../maintenance/2026-11-03-security-followup/FINDING-DETAILS.md) |

The finding register counts reports separately from underlying causes. Proof levels distinguish source analysis, focused regression tests and Android execution. These fixes do not establish that every game or device is free of defects.

## Installation and data preservation

An APK update preserves an existing installation only when Android accepts its package name, signer and version. Public UD Nightly follows upstream's `.nightly` application ID, but its certificate belongs to this fork. It cannot update Nightly signed by the original author. The earlier Thor package `.nightly.perf` needs its own matching update.

The persistent UD release certificate's SHA-256 fingerprint is `832a0223e29802245c0e9f8ca24c8358e7d21cfe0dbea347845fda751f950d86`. Release notes must identify the exact APK digest and certificate; verify both before installing.

For a supported same-package, same-signer update:

1. Complete an in-game save and close the game normally.
2. Back up current raw saves, savestates, settings, layouts, cheat data and firmware/NAND that may be written. Verify that copies are readable and match current originals.
3. Check the release APK's checksum, package, signing fingerprint and version code. Keep the installed app and its data.
4. Install the matching update in place. Compare the library, settings, cheat selections and document access, then test on a named working ROM/save copy before continuing original progress.

Never uninstall or clear app data to make a mismatched update install. A differently named package has separate private databases and permissions. Export/import across packages is a separate migration and must be tested; document permissions may need to be granted again.

A source patch applies to documented app/core revisions. It does not modify an installed APK or preserve the original author's signature. The author can review the patch, build it and sign an official update with their own key.

[Candidate identities, update acceptance and limits](../maintenance/2026-11-03-thor-2.1.8/README.md) · [Verification evidence](../maintenance/2026-11-03-security-followup/README.md)

## Contributor and issue status

Selected contributor work is adapted with attribution. The [issue/PR coverage register](../maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv) records links and the exact scope of each adaptation. Locally corrected code, a closed upstream issue and a merged PR are different states. Partial DSi HOME, microphone, casting and game-specific reports are not claimed fully resolved.
