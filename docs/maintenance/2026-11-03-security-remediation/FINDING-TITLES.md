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
