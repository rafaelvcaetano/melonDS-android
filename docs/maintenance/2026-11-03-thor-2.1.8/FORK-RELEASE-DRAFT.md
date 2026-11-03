# Fork test-build release draft — not published

Proposed title: **melonDS Android 2.1.8 — Thor test build**

Proposed tag: `thor-2.1.8-candidate`, not created. Target executable app commit:
`0018cddb04278f2bca5054804de501a459eb1de9`; paired core:
`ac5e6ba0fb795b7aa8a43fdefed62eed8a37eff8`. Mark as prerelease. Publish only
after physical acceptance and separate explicit authorization; replace this
preparation note with the measured Thor result before publication.

---

This is a modified Nightly test build for the local `.nightly.perf` variant.
It includes guided setup, cheat search, backup/restore and input controls,
plus save, lifecycle, native-state, file-parsing and resource-lifetime fixes.
It is a fork build and does not carry the official upstream signing identity.

Application ID: `me.magnum.melonds.nightly.perf`; versionCode: **49**.
This updates the same-package, same-signer local 2.1.6/47 or 2.1.7/48 variants
after identity checks and verified backups. It cannot replace a different
standard `.nightly` or production package in place.

The optimized ARM64 APK is not debuggable or testOnly. Its preserved local
certificate SHA-256 is
`41ad82cd4365c73195128975cf0b920699f8833093507957dc2d0f9d6bb0b4b1`.

APK SHA-256:
`195b5748fc59bac74eadee148f0a1a7b440e47b51dcc5f70fdcd384e855932cb`.

See the [source identities, verification and update procedure](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-thor-2.1.8/README.md)
and [individual finding evidence and limits](https://github.com/Umberto-DEV/melonDS-android/blob/main/docs/maintenance/2026-11-03-security-followup/FINDING-DETAILS.md).
Android AVD evidence includes 4/16KiB native execution, ART/provider checks,
synthetic upgrade preservation and copied-ROM Continue/SAVE/cold Continue.
The report distinguishes those results from physical Thor acceptance and
records skips and proof gaps. No all-game, zero-bug or performance claim is made.

Planned assets, neither uploaded nor added to Git source history here:
`melonDS-2.1.8-nightly-profiling.apk` and `SHA256SUMS`. ROMs, BIOS, firmware,
NAND, saves, signing keys and private test data are excluded.
