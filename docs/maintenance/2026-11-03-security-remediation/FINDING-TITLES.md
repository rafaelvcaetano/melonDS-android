# Saved finding titles and verdicts

All titles below are copied from saved scan `4c41e000-6ba0-4453-aaa7-3cdbec542687`.
All verdicts are `CORRECTED`; no saved occurrence was classified as a false
positive. See [FINDINGS.csv](FINDINGS.csv) for its concrete correction and
before/after evidence.

| Occurrence | Severity | Saved title |
| --- | --- | --- |
| `occ_bf9ea27628798833dda7cf7b` | high | Malformed cartridge images cause native out-of-bounds reads and secure-area writes |
| `occ_2be586dc4a21fbc0f60e65fd` | medium | Malformed savestate section metadata causes infinite loops and native overreads |
| `occ_e9fbed34ba4f36c73dc9449a` | medium | Android RCHV loader discards the real buffer length before native deserialization |
| `occ_4c67e6080ca4afa7cee67a40` | high | Savestates restore unchecked cartridge transfer indices used in host memory writes |
| `occ_2993485bc81b37540bda66df` | high | Crafted savestates control native callback, FIFO, audio-buffer, and table indices |
| `occ_6b9635717a6a6448125d3010` | medium | Guest DNS parsing can read beyond the frame and return stale native bytes |
| `occ_9423bc5a581f219396ba5434` | medium | Launching a compressed ROM can exceed the configured cache limit and exhaust storage |
| `occ_46d778442f1d34d3992b75b7` | medium | Running a ROM can proxy traffic to Android localhost and private-network services |
| `occ_7912862fc50149068674e372` | medium | DSiWare metadata is imported from cleartext HTTP without authenticity verification |
| `occ_8af6b491512413f1e03d2bb8` | medium | Guest DNS queries leak native resolver allocations |
| `occ_9fbf5f7454cdc1a7a98c3959` | medium | DSi title listing reads fixed-width banner text as an unbounded string |
| `occ_a58108db986cc45682121731` | low | A malformed external firmware-launch intent crashes the Android emulator |
| `occ_2bba18cf5ac157af5fc41bcb` | high | Guest-controlled Wi-Fi TX descriptors overwrite native state and trigger an oversized copy |
| `occ_0ead515f26677112d1f828d6` | high | Untrusted serialized ROM configurations expose or overwrite files using melonDS authority |
| `occ_d540d679331dce207e3adb60` | low | Null PATH extra crashes the exported singleTask launch path |
| `occ_6fc7ac8a4bb1383c4b1bb230` | medium | A DSiWare save size can trap NAND import in a nonterminating loop |
| `occ_bc26e0f9273443d666f51fd1` | medium | A tiny cheat database can request a near-2 GiB allocation |
| `occ_1211210d24906e23b17d51f2` | low | A malformed settings backup can prevent every later app launch |
| `occ_4e4229a143d29c8d477e6cd6` | low | Cheat XML import does not bound internal entity expansion or parsed text |
| `occ_c7156b64208b9c244aee3dec` | medium | GPU3D savestate restoration trusts array indices and counts before native writes |
| `occ_dec38863aa79dfd2f944a501` | low | Privileged release workflows execute dependencies without immutable integrity pins |
| `occ_6cb1c55ef7aff5b4986102aa` | low | RetroAchievements bearer token is included in Android platform backup |
| `occ_f39613af9fae3c25d83a0055` | medium | Signing in can expose RetroAchievements passwords and tokens through URL logs |
| `occ_4ab7c23e479f23f205e15b1f` | medium | Savestate file length drives an uncapped native allocation before validation |
| `occ_a558a5b137d2433c0269cf8e` | high | A crafted custom firmware image can make checksum updates write outside its native buffer |
| `occ_3b5415742d7ddbfad77786f5` | high | A malicious document provider can turn firmware revalidation drift into a controlled heap overwrite |
| `occ_d861c760cdbcae81657434c1` | medium | A direct ROM URI can force a one-gigabyte native allocation |
| `occ_f0001eeaf73d4f180cc706e6` | medium | A truncated DSiWare file can create an invisible, non-reimportable NAND title |
| `occ_0f5f2f5c076ab2aa0094b993` | high | GBA flash commands read and overwrite host heap beyond save RAM |
| `occ_199e211e1856d6a8fcbf97fa` | high | Guest-writable firmware metadata reaches unchecked host buffer copies |
| `occ_9e7a80234cd3366f50ce8d90` | high | DSi DSP HLE pipe descriptors escape mapped host memory |
| `occ_595441428ec94336e7c4ebf7` | high | Zero-width DSi graphics command underflows a native transfer length |
| `occ_71e386607ebf94f39d022a9f` | medium | A 32-bit URI hash lets one compressed ROM substitute another cached ROM |
| `occ_a134bcef99d03aae124925a6` | high | Guest-controlled B7 transfers overread ROM memory and zero-length commands underflow into massive copies |
| `occ_c9523ca984164c679e7340bb` | medium | Savestate SRAM lengths drive unbounded cartridge allocations |

## Second independent scan

The titles below are copied from completed scan
`ab26b107-97dd-4557-9c4b-2942e5df879d`. Their dispositions are appended to
the same canonical [FINDINGS.csv](FINDINGS.csv); this section does not replace
the first campaign or count its 35 occurrences again. “Open” in the Security
results UI is a service workflow state, not the local technical disposition.

| Occurrence | Severity | Primary origin | Saved title |
| --- | --- | --- | --- |
| `occ_09abfa72b6bd218ba5427fa8` | high | `melonDS-android-lib/src/DSi.cpp` | Running a crafted DSi ROM can read beyond the BPTWL register array into host memory |
| `occ_c4615d109e2957a6c04ff773` | high | `melonDS-android-lib/src/DSi.cpp` | A crafted DSi ROM can overflow a host stack buffer through unaligned NAND block transfers |
| `occ_57ddbf72d1386b72987ba0b8` | high | `melonDS-android-lib/src/DSi.cpp` | A crafted DSi ROM can read past SD/MMC response objects into host memory |
| `occ_73d6504df53c15f837d73f4e` | high | `melonDS-android-lib/src/DSi_SD.cpp` | A crafted DSi ROM can obtain uninitialized host stack bytes through failed SD or NAND reads |
| `occ_54a7deb0ad08a3fafebc1726` | high | `app/src/main/cpp/MelonDS.cpp` | Crafted savestates escape camera, GPU2D, SD, RTC, I2C, GBA, DSP, and DMA invariants |
| `occ_31750274562430030e68a5db` | high | `melonDS-android-lib/src/NDSCart.cpp` | Retail NAND cartridge savestates retain unchecked staging cursors and addresses |
| `occ_f3a0f11c9bc863db1b13f85f` | medium | `app/src/main/java/me/magnum/melonds/impl/emulator/SramProvider.kt` | Android save-sidecar loading can allocate nearly 4 GiB from a provider-controlled or failed length |
| `occ_e52f1eda044745cca9bad3bb` | medium | `melonDS-android-lib/src/NDS.cpp` | Restored DSi DSP timestamps can trap the emulator in a zero-progress catch-up loop |
| `occ_95f95be3047a6de20473771d` | medium | `app/src/main/java/me/magnum/melonds/impl/AndroidDSiNandManager.kt` | Closing the DSiWare screen can free NAND state while title enumeration is still running |
| `occ_b5ec7b0906603afed2916ce7` | high | `melonDS-android-lib/src/NDSCart.cpp` | R4 cartridge override bypasses corrected B7 bounds and overreads native ROM memory |
| `occ_81cd48e8ae72ac0edbf324ac` | medium | `app/src/main/java/me/magnum/melonds/utils/RomProcessor.kt` | ROM metadata offsets can force multi-gigabyte archive decoding during scans or granted-content launch |
| `occ_5830142954786d70a8044c22` | medium | `app/src/main/cpp/MelonInstance.cpp` | R4 guest commands dereference SD storage that Android intentionally omits |
| `occ_19c2f7d0a71bb16afcebbb17` | medium | `app/src/main/java/me/magnum/melonds/impl/FileSystemRomsRepository.kt` | Cyclic document-provider trees can repeatedly crash ROM library scanning |
| `occ_4c6c667123e2f9c495f39cbe` | medium | `melonDS-android-lib/src/DSi_NAND.cpp` | Malformed DSi NAND exports can disclose uninitialized stack bytes |
| `occ_ac89863ef8213945c0ee1634` | medium | `app/src/main/cpp/EmulatorArgsBuilder.cpp` | G.711 DSP commands can trap the Android emulation thread in billions of iterations |
| `occ_b3ec145f5094e12cfd0ad76e` | medium | `app/src/main/java/me/magnum/melonds/impl/FileSystemConfigurationDirectoryVerifier.kt` | Crafted NAND title directories can exhaust Android heap during DSiWare checks |
| `occ_b2e00e7deb81cb8414cb9ac7` | medium | `app/src/main/java/me/magnum/melonds/common/workers/CheatImportWorker.kt` | Cheat XML and DAT imports still lack an actual-byte and document-wide resource budget |
| `occ_1db371734a02d73a8775b1cc` | low | `app/src/main/cpp/MelonInstance.cpp` | Failed save-writability checks leak native provider descriptors |
| `occ_57050ca0e32fd3583bc589bc` | informational | `melonDS-android-lib/src/DSi_SD.cpp` | [Completed candidate disposition] Core current-state reconciliation |
| `occ_2ef748460c8bc14f9e87a687` | high | `melonDS-android-lib/src/teakra/src/mmio.cpp` | Teakra DMA active-channel MMIO accepts arbitrary array indices |
| `occ_f332a69bdae9b2e0d1dac524` | high | `melonDS-android-lib/src/NDS.cpp` | Savestate scheduler fix leaves DisplayFIFO event parameters able to corrupt native memory |
| `occ_ae5b73c4466f8467cbd84442` | high | `melonDS-android-lib/src/GPU2D.cpp` | GPU2D savestates restore unchecked display-FIFO pointers |
| `occ_a28be96fd4601332ade34084` | medium | `app/src/main/cpp/UriFileHandler.cpp` | Persistent native threads leak JNI local references across SAF and camera callbacks |
| `occ_fd5c865996d47f8a29eaaafc` | low | `app/src/gitHubProd/java/me/magnum/melonds/github/repositories/GitHubProdUpdatesRepository.kt` | GitHub builds contact GitHub after update checks are disabled |
| `occ_f49486b60787daa20317b351` | medium | `rcheevos-api/src/main/java/me/magnum/rcheevosapi/RAApi.kt` | RetroAchievements responses can exhaust the Android heap before unbounded JNI mapping |
| `occ_3c141d4de0621f3163477e28` | medium | `app/src/main/cpp/EmulatorArgsBuilder.cpp` | Malformed DSi NAND reads expose uninitialized host memory during boot |
| `occ_1ba9fffd6413a9fed864cc8a` | medium | `melonDS-android-lib/src/DSi_NWifi.cpp` | DSi NWiFi BMI commands run up to 2^32 guest-selected iterations |
| `occ_1fca5222a4e54a0e50d62054` | low | `app/src/main/java/me/magnum/melonds/common/workers/CheatImportWorker.kt` | DAT cheat import has no aggregate byte, object, or database-growth budget |
| `occ_b8800026c2c1514ac6cf77e3` | low | `app/src/main/java/me/magnum/melonds/common/UriFileHandler.kt` | Provider write exceptions cross JNI unchecked and poison the native save worker |
| `occ_374991baf934127ee85e4c58` | medium | `app/src/main/cpp/retroachievements/RetroAchievementsManager.cpp` | The sized rcheevos deserializer still overreads malformed RCHV chunks |
| `occ_f26f37e53de818cf6047c118` | low | `app/src/main/java/me/magnum/melonds/impl/SettingsBackupManager.kt` | Restored firmware text can crash generated-firmware startup |
| `occ_e7ee97f5407adcc076962982` | medium | `app/src/main/cpp/MelonDS.cpp` | Savestate-restored multiplayer reply lengths overflow the native Wi-Fi receive buffer |
| `occ_2cbc1cd3c17db642b326d34c` | medium | `app/src/main/cpp/MelonDS.cpp` | Exhausted guest DHCP leases leak native reply buffers on every subsequent request |
| `occ_198b7bfed24d33a6f5a9703a` | medium | `app/src/main/java/me/magnum/melonds/impl/FileSystemRomsRepository.kt` | Provider-controlled ROM names can escape, collide with, and overwrite SRAM and savestate targets |
| `occ_56f99ecbb0c411912ec720d8` | medium | `app/src/main/java/me/magnum/melonds/common/workers/CheatImportWorker.kt` | Parenthesis stripping permits quadratic cheat-name processing and persistent UI hangs |
| `occ_51f8dfa0478282b4bf2b39ae` | high | `melonDS-android-lib/src/Utils.cpp` | ROM padding exposes uninitialized native heap bytes to emulated cartridges |
| `occ_7e9c0b4d94b8437452182fb0` | medium | `melonDS-android-lib/src/CartridgeImageBounds.h` | Cartridge bounds fix omits word-rounded direct-boot reads |
| `occ_76beba3dc134ea6d6c6e02b8` | low | `rcheevos-api/src/main/java/me/magnum/rcheevosapi/dto/AchievementDto.kt` | Loading a crafted RetroAchievements definition can make Android read across an emulated-memory region boundary |
| `occ_95b372be5c7a53614199db50` | medium | `app/src/main/java/me/magnum/melonds/common/cheats/UsrCheatDatabaseParser.kt` | Enabling a crafted Action Replay cheat can freeze the Android emulator for billions of operations |
| `occ_2142f6825b17450c0807e331` | medium | `app/src/main/java/me/magnum/melonds/domain/model/DSiWareTitle.kt` | Importing a wrong-size DSiWare save can replace the installed title’s persistent data |
| `occ_6fe4c3eccf94ed4e2f5b5fc2` | medium | `app/src/main/java/me/magnum/melonds/common/BiosGuidedFolderSetup.kt` | A name-only NAND candidate can make guided setup consume storage until provider EOF |
| `occ_277d6d22695d3f279a0df1f1` | medium | `app/src/main/cpp/MelonDS.cpp` | Failed savestate loads leave partial state because the backup is never switched to load mode |
| `occ_1c733703a47262e271e37f76` | medium | `melonDS-android-lib/src/DSi_Camera.cpp` | Invalid DSi camera dimensions create an endless zero-delay event loop |
| `occ_88d732c7d8c916df74e2401a` | low | `melonDS-android-lib/src/teakra/src/mmio.cpp` | Running a DSi ROM with an odd DSP audio queue can crash the emulator |
| `occ_836e4218de342a38455bf14c` | low | `melonDS-android-lib/src/teakra/src/dma.cpp` | A DSi DSP DMA length can wrap its progress counter and hang the emulator thread |
| `occ_3f076c55d71a70958a33220f` | medium | `app/src/main/AndroidManifest.xml` | An external app can substitute an unrelated cached ROM during an exported launch |
| `occ_a32f5bdeefb1bbbc68fd7eaf` | medium | `app/src/main/java/me/magnum/melonds/ui/emulator/EmulatorViewModel.kt` | Large achievement sets exhaust JNI references and pin Java strings |
| `occ_e3d247849cd9edb46f0819ab` | low | `rcheevos-api/src/main/java/me/magnum/rcheevosapi/RAApi.kt` | RetroAchievements HTTP errors leak response resources across repeated requests and retries |
| `occ_6cb0ac532a98fc7abd11b3aa` | medium | `app/src/main/res/xml/pref_video.xml` | Static-camera image decoding lacks a dimension and pixel budget |
| `occ_6422719eff0c108a08550a57` | medium | `melonDS-android-lib/src/SPU.cpp` | A restored zero SPU interval can trap the emulator thread at one timestamp |
| `occ_f10f444f438e1120ee83f810` | high | `melonDS-android-lib/src/NDSCart.h` | R4 data commands expose an uninitialized native heap buffer to guest code |
| `occ_836a150ecb9cff2653ede290` | medium | `melonDS-android-lib/src/SPU.cpp` | Live SPU timer-reload writes can index before interpolation tables |
| `occ_681a4f167a153dbb72619581` | high | `melonDS-android-lib/src/NDS.cpp` | Restored SPU scheduler parameters can write beyond blip audio buffers |
| `occ_cb3a39caab485844bb03f2f0` | low | `melonDS-android-lib/src/DSi_SD.cpp` | A crafted DSi guest can extend the selected host NAND file beyond its declared capacity |
| `occ_835616344065fc5650304ffc` | high | `melonDS-android-lib/src/GPU.cpp` | Restored GPU VRAM mapping masks select outside the native bank tables |
| `occ_845f508c28d308a3ed6921f4` | high | `melonDS-android-lib/src/SavestateInvariant.h` | GPU3D transient validation omits the polygon mode and vertex-count relationship |
| `occ_86c50141e68ec4d1fcff078f` | medium | `app/src/main/cpp/EmulatorArgsBuilder.cpp` | G.711 output to SCFG_RST can delete the executing DSP HLE object |
| `occ_f1b3992fa930f8e5be9671e5` | low | `melonDS-android-lib/src/DSi_NWifi.cpp` | DSi Wi‑Fi EEPROM window reads beyond the fixed array at tail offsets |
| `occ_f81c873106385dfd79200c78` | medium | `melonDS-android-lib/src/GBACart.cpp` | Guest-triggered rumble events enter an effectively unbounded Android SharedFlow |
| `occ_5aa0cdd63e52e8bf6e5c5f58` | medium | `melonDS-android-lib/src/NDSCartR4.cpp` | Guest-corrupted R4 FAT geometry causes native division by zero |
| `occ_44c82f1de1a141fb56634019` | low | `settings.gradle.kts` | Verify every Gradle artifact before privileged release builds |
| `occ_7fddae3d4a0847da222da76c` | high | `.github/workflows/release.yaml` | Unrestricted tag pushes can initiate Google Play production deployment |
| `occ_2d9284d05331b527dffd90b6` | high | `.github/workflows/release.yaml` | Unrestricted tag pushes reach production signing and GitHub publication |
| `occ_fe120097762083f50d897200` | low | `app/src/main/cpp/MelonDSAndroidConfiguration.cpp` | Android JNI entrypoints leak UTF string leases across repeated operations |
| `occ_3b717bb0887528b36797d1bb` | low | `app/src/main/cpp/MelonDS.cpp` | Loading a crafted DSi savestate can trap AES processing in billions of iterations |
| `occ_dad7fd6942e9dd4f18a15e3f` | low | `.github/check_ci_integrity.py` | Nested-core CI checks execute a mutable GitHub Action outside the integrity checker's scope |
