# melonDS Android UD Nightly

UD Nightly is a fork of [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android), the Android port of the [melonDS](https://melonds.kuribo64.net/) Nintendo DS and DSi emulator. It adds easier setup and controls, and includes fixes for saves, imports, Android lifecycle handling and the native core.

[Downloads and release notes](https://github.com/Umberto-DEV/melonDS-android/releases) · [Feature and update guide](docs/user-guide/UD-NIGHTLY.md) · [Android core fork](https://github.com/Umberto-DEV/melonDS-android-lib)

Check each release for its exact version, Android package, CPU architectures, signing certificate and test results. An older release does not establish that the latest source has been packaged or tested on your device.

## What is new compared with official Nightly?

The documented comparison starts from official app revision `c42995ca` and core revision `431ab4bd`. Features already present upstream retain their original attribution, including DAT cheat support, OpenGL/Compute rendering and the settings backup implementation.

- **Guided BIOS and firmware setup:** select your files, see separate DS and DSi results, and retry a failed copy without adopting an incomplete file. [Setup details](docs/user-guide/UD-NIGHTLY.md#bios-and-firmware-setup)
- **Three firmware Wi-Fi connection slots:** edit names and DNS settings, then enable or disable connections from the pause menu. [Connection settings](docs/user-guide/UD-NIGHTLY.md#online-connection-settings)
- **Cheat search and grouped choices:** search names, folders and selector options. Supported code families can appear as a species, level, nature or starter choice. Finding a result does not enable it. [Cheat changes](docs/user-guide/UD-NIGHTLY.md#cheat-search-and-import)
- **Settings and layout backup/restore:** the existing upstream backup manager is accessible, with validation and refreshed controller settings after restore. [Backup and restore](docs/user-guide/UD-NIGHTLY.md#settings-and-controls)
- **More input options:** hold to fast-forward, combination hotkeys, optional fast-forward mute, sleep/lid handling and optional forward-only clock synchronization. [Controls](docs/user-guide/UD-NIGHTLY.md#settings-and-controls)

## Screenshots

These are actual UD Nightly 2.1.8 screens on an Android 15 emulator, using synthetic data. The cheat examples remain disabled. [Capture identity and checksums](docs/images/ud-nightly/provenance.json)

| Guided BIOS setup | Cheat search | Level selector |
| --- | --- | --- |
| ![Guided BIOS setup instructions](docs/images/ud-nightly/guided-bios-setup.png) | ![Search results for Level in synthetic cheats](docs/images/ud-nightly/cheat-search.png) | ![Level choices with level-specific search text](docs/images/ud-nightly/cheat-level-selector.png) |

| Species selector | Firmware connection slots |
| --- | --- |
| ![Species choices in a synthetic cheat database](docs/images/ud-nightly/cheat-species-selector.png) | ![Editable second and third firmware Wi-Fi slots](docs/images/ud-nightly/wfc-slots.png) |

## Bug fixes

Each linked report describes the previous behavior, correction, tests and limits.

- **Failed cheat imports preserve the previous database.** Invalid input, cancellation and write failures are handled before replacement is committed; a later import can be retried. [Import evidence](docs/maintenance/2026-10-28-android-bugfixes/VALIDATION.md)
- **Save and NAND failures receive explicit recovery.** Changes retain pending save writes and preserve or recover the previous NAND save generation when replacement fails. [Save checks](docs/maintenance/2026-10-28-main/VALIDATION.md) · [NAND failure and retry](docs/maintenance/2026-11-03-nand-rename-fault/README.md)
- **ROM scanning recovers after errors.** Scans are serialized, published lists remain stable, and compressed input is limited before expensive work. [Library and archive checks](docs/maintenance/2026-10-28-refinement/INTEGRATION-REPORT.md)
- **Android input, audio and display transitions are corrected.** This includes interrupted input, microphone sample limits, session ownership and software-renderer fallback. [Before/after behavior](docs/maintenance/2026-10-28-android-bugfixes/REPORT.md)
- **Untrusted files and emulated hardware state are checked before native access.** Bounds, sizes and work limits cover cartridge data, savestates, firmware, Wi-Fi, DSP, cheats and Android file providers. [Individual findings and fixes](docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md)
- **The shared core fixes JIT, cartridge and DS/DSi state handling.** [Core changes and evidence](https://github.com/Umberto-DEV/melonDS-android-lib#changes-from-the-official-android-core)

The [issue and PR coverage register](docs/maintenance/2026-10-28-refinement/ISSUE-PR-COVERAGE.csv) distinguishes complete local fixes, partial coverage and work not integrated. A fix in this fork does not close an upstream issue or merge another contributor's PR.

## Install or update

Use the APK listed in a release and verify its SHA-256 checksum before installing. ROMs, BIOS, firmware, NAND and game saves are not included.

- **New UD installation:** use the standalone UD Nightly APK and select your own game and system files.
- **Existing UD installation:** an in-place update requires the same Android package and signing certificate, with a higher version code. Back up saves, settings, layouts, cheats and system files first. Keep the app installed; do not clear its data as an update step.
- **Official Nightly installation:** UD Nightly follows the `.nightly` package name but has a different signing identity. Android will reject replacing the author's signed APK with a fork-signed APK.
- **Earlier `.nightly.perf` installation:** use its explicitly labeled compatible update. The public `.nightly` APK cannot update it in place. Private databases and saved document permissions do not transfer automatically between package names.

A [source patch](docs/user-guide/SOURCE-PATCHES.md) is for developers applying changes to the documented source revision and rebuilding. It cannot turn the author's signed APK into an update signed by this fork. [Installation and update details](docs/user-guide/UD-NIGHTLY.md#installation-and-data-preservation)

The persistent UD release signing certificate has SHA-256 fingerprint `832a0223e29802245c0e9f8ca24c8358e7d21cfe0dbea347845fda751f950d86`. Verify the downloaded APK against its release manifest. Earlier `.perf` builds use a different preserved local identity and require their separately labeled update.

## Supported behavior and limits

DS/DSi emulation, supported ROM archives and the existing renderers remain available. Local multiplayer, DSi SD-card support, new casting controls and broad device-specific compatibility are not claimed by these changes. Online settings do not guarantee that a game, access point or community server will connect.

Testing includes JVM/API checks, native sanitizers, Android native tests on ARM64 4 KiB and 16 KiB environments, and real-app tests on Android emulators. Physical-device results and skipped checks are recorded separately in the [verification reports](docs/maintenance/README.md). Test success does not certify every game, Android version or device. No measured battery, CPU, GPU, RAM or frame-rate improvement is claimed without a corresponding comparison.

## Building

Both fork repositories use `main`. The app pins the exact core revision as a Git submodule and compiles it into the APK; end users do not install the core separately.

```sh
git clone --recurse-submodules --branch main https://github.com/Umberto-DEV/melonDS-android.git
cd melonds-android
sh ./gradlew :app:assembleGitHubNightlyDebug
```

A plain source ZIP does not contain submodule contents. In an existing checkout, use `git submodule update --init --recursive`; do not use `--remote` when reproducing a pinned build. The debug build has a separate `.dev` ID and is not the release update.

Use Java 21 and the SDK, NDK and CMake versions declared by the project. Release builds use `:app:assembleGitHubNightlyRelease` and require an owner-controlled signing key configured in untracked `local.properties`:

```properties
MELONDS_KEYSTORE=/path/to/keystore
MELONDS_KEYSTORE_PASSWORD=<store password>
MELONDS_KEY_ALIAS=<key alias>
MELONDS_KEY_PASSWORD=<key password>
```

Keep signing material and passwords out of Git, artifacts and logs. Published UD updates must preserve their signing identity.

## Credits and license

Rafael Caetano maintains the upstream Android port; melonDS and its contributors provide the core. This fork preserves their licenses and credits. Adapted app work includes takanuva15 (cheat search), WailAbou (hotkeys and hold to fast-forward), dysprosium (fast-forward mute), N3MI-DG (external launches), DLCSharp (sleep/lid behavior) and selected fixes by corigne. The evidence documents retain the corresponding PR links and scope.

See [LICENSE](LICENSE), the [core contributor guide](https://github.com/Umberto-DEV/melonDS-android-lib/blob/main/CONTRIBUTING.md) and the original projects for licensing and contributor information. Game and system files are not distributed here.
