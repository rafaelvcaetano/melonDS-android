# Applying the UD source patches

The download contains one Android APK for fresh installation or a same-signer
UD update, and separate **source patches** for developers. There is no binary
patch that can replace the official developer's Android signature.

## Exact starting revisions

| Component | Official source revision |
| --- | --- |
| Android app | `c42995caff0144f37ad9791dc9bc96d82e8a6f65` |
| Android core | `431ab4bd0003c4356e25fce89640ae1004579b9b` |

Start with a clean checkout of those revisions, including all submodules.
Keep your working project and its data backed up. The patches intentionally
exclude the app gitlink and `.gitmodules`: the reviewer controls the accepted
core revision and repository URL.

From the app source directory, check both patches before applying either:

```sh
git -C melonDS-android-lib apply --check /path/to/melonDS-UD-2.1.8-core-source.patch
git apply --check /path/to/melonDS-UD-2.1.8-app-source.patch
git -C melonDS-android-lib apply /path/to/melonDS-UD-2.1.8-core-source.patch
git apply /path/to/melonDS-UD-2.1.8-app-source.patch
```

Stop if a check fails. Do not force patches onto a different revision. Both
patches were applied to temporary exports of the stated bases and the resulting
changed files were compared byte-for-byte with the fork source.

The self-contained source archive is an alternative: it includes the pinned
core and other submodule sources, with their licenses. A plain GitHub automatic
source ZIP does not include submodule contents.

## Build and sign

Use Java 21 and the SDK, NDK and CMake versions declared by the source. Configure
your own signing key in untracked `local.properties`, following the main
[build instructions](../../README.md#building), then run:

```sh
sh ./gradlew :app:assembleGitHubNightlyRelease
```

The distributed UD binary uses the build-only version name `2.1.8-UD`; its manifest records this override. Set that value in `AppConfig.versionName` before building if you want the same label. This metadata change does not alter the data formats.

Use the existing signing identity for the installation you intend to update.
The original developer can sign the patched source with their official key;
another developer's key cannot update that official installation. UD downloads
use the persistent UD key. An APK also needs the same application ID and an
allowed versionCode to update an existing package.

Before distributing a rebuilt APK, repeat the release checks and data-preserving
update tests. A successfully applied source patch is not Android runtime
acceptance. ROMs, saves, BIOS, firmware, NAND, private keys and personal backups
are not part of these patches or source packages.
