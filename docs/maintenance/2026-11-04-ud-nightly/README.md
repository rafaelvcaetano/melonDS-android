# UD Nightly 2.1.8: release and update verification

4 November 2026. Earlier reports retain their original APK identities. The
[English feature guide](../../user-guide/UD-NIGHTLY.md) describes the changes
from official app `c42995ca` and core `431ab4bd`; the [101 individual finding
records](../2026-11-03-security-followup/FINDING-DETAILS.md) explain original
failures, corrections, evidence and limits. Findings and issue/PR coverage
remain separate: a fork correction is not an upstream issue closure.

## Public APK

| Field | Verified value |
| --- | --- |
| Artifact | `melonDS-UD-nightly-2.1.8.apk` |
| Package/version | `me.magnum.melonds.nightly`, `2.1.8-UD GH (NIGHTLY)`, code 49 |
| SHA-256 | `b5c05665ceb7c47a2f208961acb64cc29aea4bcc3e151a654de79fbd78b5be1b` |
| Size | 20,761,895 bytes |
| Certificate SHA-256 | `832a0223e29802245c0e9f8ca24c8358e7d21cfe0dbea347845fda751f950d86` |
| Build | `gitHubNightlyRelease`, R8/resource shrinking, optimized native code |
| API/ABIs | min 24, target 37; ARM64, ARM32 and x86-64 frontends |
| Flags/signature | Not debuggable, not testOnly; APK v2 verified; 16 KiB ZIP alignment passes |
| Executable app source | `4809d38fd8dfe2745a1605377a5825516a6aec58` |
| Core source | `2bb701ffd6b2cdd6260796eac5b1d4173a5e72fa` |

The core's latest commit changes its README; native code remains the tested
`ac5e6ba0` correction set. Publication manifests distinguish executable source
from later documentation-only commits. Both forks have only `main`.

The [app CI](https://github.com/Umberto-DEV/melonDS-android/actions/runs/41892532022)
passed at `60a5a8c246a2327ee9e048a1867d6ebe8595cf4a`: JVM tests/lint/ARM64
debug APK, native sanitizers on Linux x86-64, and native sanitizers on Linux
ARM64 all succeeded. The upstream-only publication job was skipped, as intended.
The [core CI](https://github.com/Umberto-DEV/melonDS-android-lib/actions/runs/41869978455)
passed at `2bb701ff`. CI debug artifacts and the optimized APKs below have
separate binary identities.

Earlier dispatches were blocked by GitHub HTTP422 while the fork was suspended
for Actions usage scale. The owner re-enabled execution; the same main source
then passed the run above. The workflow skips automatic full builds for pushes
changing only `docs/**` or the root README. Code/build/workflow/submodule
changes retain verification; PR and manual triggers are unchanged. Superseded
fork runs are cancelled within the same workflow/branch group. Upstream
running workflows are not cancelled by this setting. Related changes are
batched at verification milestones.

The persistent UD private key and encrypted recovery bundle remain local and
excluded from Git and downloads. An independent off-Mac recovery copy remains
the owner's responsibility. No signing credentials or personal data are bundled.

## Release and physical update

Seven release assets are provided on the [app download page](https://github.com/Umberto-DEV/melonDS-android/releases/tag/ud-nightly-2.1.8).
Uploaded sizes and SHA-256 digests were verified; an actual downloaded APK
was rehashed successfully. The source
archive contains 3,464 files including pinned submodules. Both source patches
pass application checks and byte comparisons for all 399 app and 77 core
changed files. The source retains the unchanged upstream freely
redistributable BIOS replacements and their copyright/license; no personal
system images or game/save inputs are distributed.

The physical-device preparation verified private snapshots before target
Application/provider startup, including Room schema 8 with 709,658 cheats,
30,341 folders and 4,255 games; 11 library records, controller/layout files
and three persisted URI grants are recorded. All 41 present protected external
files were backed up, verified and rehashed unchanged. Seven historical paths
were already absent before this work and were not restored or deleted.
The initial preparation helper and transport copies were removed. A fresh
baseline was captured before the subsequent [physical 47-to-49 update](../2026-11-04-thor-update/README.md).
That report verifies the installed ARM64 APK, database and protected files,
expected cache/metadata changes, permissions, real library startup and cleanup.
No original game was launched or saved during physical acceptance; gameplay
on the Thor remains the owner's separate review.

Campaign cleanup retained regression source and small reports, removed
3,169,377,590 bytes of reproducible build outputs, 20,770,620 bytes of synthetic
APK intermediates and 134,327,778 bytes of AVD working copies. Per-file
deletion manifests and retained-copy hashes precede cleanup. Both campaign
AVDs were stopped; the shared ADB server was left running.

## Selector wording corrected during release testing

The real release showed a level selector with level values but the prompt
“Choose a Pokémon” and a Pokédex search hint. `ModifierFamilyDialog` reused
species strings for all option types; its fallback help could describe an
unrelated game's L+R behavior. The correction selects prompt/search/empty-result
text from the existing kind and starter role, preserves supplied instructions,
and uses generic help otherwise. Code values, search, activation and stored
selections are unchanged. Strings cover all eight supported locales.

Four real Compose screen regressions failed before the fix. The focused
dialog/recognition/fixture suite passed 42 tests afterward. The full app/API
suite passed **349 tests**, with zero failures, errors or skips. Lint initially
found 11 missing translations; six additional locale files fixed them without
suppression. Debug lint retained 479 warnings and zero errors. Test source is
`app/src/test/java/me/magnum/melonds/ui/cheats/ModifierFamilyDialogTest.kt`.
This is usability cause `UI-MODIFIER-LABELS`, outside the two security scans.

## Build and source checks

The upstream release task is used with fork version/signing metadata. Final
release/profiling builds, both lint variants and JVM/API tests passed: 252
Gradle tasks, 110 executed and 142 up-to-date. Compiler/lint warnings remain
warnings. APK identity, flags, certificate, ABIs and checksums are inspected
from the actual file, not inferred from Gradle settings.

The core and other submodules are compiled into the APK; no separate library
installation is needed. The self-contained source archive includes their
sources and licenses. [Source patches](../../user-guide/SOURCE-PATCHES.md)
target the exact official app/core bases. Both patches are applied to temporary
source exports and every changed file is compared with the fork. The app patch
leaves `.gitmodules` and the gitlink for the reviewer to select.

Internal prompts, agent instructions, operational plans, signing material,
commercial game images, personal BIOS/firmware, NAND and saves are excluded from downloadable packages.
Technical reports, regression source tests, credits and licenses are retained.

## Actual Android release evidence

On ARM64/API35/16 KiB (`emulator-5554`), the exact final public APK
`b5c05665ceb7c47a2f208961acb64cc29aea4bcc3e151a654de79fbd78b5be1b`
passed direct and ZIP homebrew loading, complementary red/blue DS rendering
and normal Exit to the library. The level selector showed “Choose a level”,
“Search levels” and “No levels found” for the nonmatching query `999`;
Cancel left it inactive. The species selector kept its species wording, and
cheat search found folder, family and option labels. All test cheats remained
disabled. [Public screenshots and capture identities](../../images/ud-nightly/provenance.json)
use synthetic data and contain no personal game or backup files.

On this same final APK, a valid synthetic XML represented one game, five
folders and 33 cheats. Importing the 43-byte incomplete XML produced an
explicit WorkManager `FAILURE`. Read-only private snapshots before and after
showed SQLite integrity `ok`, every logical table row unchanged, and identical
main database, WAL **and shared-memory** hashes. Zero cheats were enabled.
Retrying the valid XML then produced an explicit WorkManager `SUCCESS`.
These final results supersede the earlier APK's import repeat for this release;
the older evidence retains its original identity.

The authorized SGP ROM and raw save were tested as named working copies.
Continue loaded the existing game; in-game SAVE and overwrite confirmation
completed before force-stop. A new process (PID `6845` to `7799`) cold-loaded
the saved game at the same Pokémon Center, then normal Exit returned to the
library. The 524,288-byte working save changed from SHA-256
`975bc4d18c5991a688c2dcefb97a959fc2a400027ea216d5a77bdaded2bf6f1a`
to `c0f9251c0d9c43a4b4ba1b0840dbdd48667c7773316e08cf9373ab5678bbbf95`,
which stayed unchanged after cold Continue. Canonical ROM and original save
hashes were reverified unchanged. This is one game/save path, not a claim
about every game or old savestate.

The final UI also showed all three persisted WFC preset slots across process
restarts, without testing a game/server connection. Guided BIOS instructions
and Cancel were checked, and custom BIOS mode was restored disabled. The
missing-files result belongs to the earlier APK; complete valid custom
BIOS/DSi NAND adoption was not repeated on this final release.

On ARM64/API35/4 KiB (`emulator-5556`), UD-over-official installation was
correctly rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; the official
synthetic test install remained unchanged. Only that newly created test
package was removed afterward. A synthetic public UD48-to-UD49 update
preserved UID, firstInstallTime, preferences and layout/library files; only
version/profile/cache data changed. That fixture had no Room database or URI
grants and establishes no such coverage.

A separate update of the existing `.nightly.perf` fixture used the actual
2.1.7/48 profiling build as its baseline and the optimized ARM64 2.1.8/49
release APK with SHA-256
`68f31a9059fbd6866d8892ac014d5c17ea4fe673440eae799b7ac18c5f7ceca2`.
UID, firstInstallTime and layout files were preserved. Before app startup,
only an ART cache file differed. After startup, the only preference value
changed was `last_version`; the ROM index retained its original field values,
with reordered keys and an explicit `storageNameVersion: 0` default. This
metadata change was compared structurally rather than treated as data loss.
The preserved real library entry launched the generated homebrew through its
existing document URI, rendered the expected red/blue screens, and returned
to the same library through normal Exit. No SAF regrant was needed. Bounded
runtime logs contained no Fatal/ANR markers. This fixture had no populated
Room database; the public APK's separate failed-import test supplies that
narrow database-preservation evidence.

Earlier [native sanitizer, Android 4/16 KiB and ART/provider evidence](../2026-11-03-security-followup/README.md)
supports the unchanged core/runtime logic but is not a fresh exploit execution
for every finding in this APK. Mid-stream import cancellation, valid personal
BIOS/NAND adoption, all games, ARM32/x86-64 execution and power-loss recovery
are not certified here. No measured performance or battery benefit is claimed.

## Data and signing compatibility

One APK serves a fresh install and an allowed update of the same package with
the same signer. A fork key cannot replace the official key; see [Android app
signing](https://developer.android.com/studio/publish/app-signing). A source
patch lets the original developer rebuild and sign with their own identity.

Room schemas, database definition and migration registration are unchanged
from official app `c42995ca`: version8, with no destructive migration fallback.
This static check complements runtime data comparison; [Room migration
guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions)
explains why signing alone is not a migration test.

The Thor's existing `.nightly.perf` package retained its certificate through
the separate ARM64 release update. The [physical update report](../2026-11-04-thor-update/README.md)
records 2.1.6/47 to 2.1.8/49, all 41 inventoried external files unchanged,
unchanged database/cheats/layouts/controller files and document permissions,
and the expected version/cache metadata changes after startup. It does not
certify every old savestate or game. The temporary helper was removed.

The inherited in-app update checker still follows the original project. Use
fork release downloads for UD-signed updates; source patches do not supply
the original developer's signing key. Published release assets retain the
`aef762d6` source distribution checkpoint; later physical reports on main do
not replace those immutable files or change their APK identity.

## Upstream review

The short [app](../2026-10-28-main/NEXT-APP-PR.md) and
[core](../2026-10-28-main/NEXT-CORE-PR.md) drafts retain human credits and links.
The [maintainer requested one PR per feature or fix](https://github.com/rafaelvcaetano/melonDS-android/pull/1672#issuecomment-5637055233),
so submission scope must be narrowed before publishing upstream. No upstream
PR or issue is created or closed by this fork release.
