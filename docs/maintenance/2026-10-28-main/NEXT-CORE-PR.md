# Unpublished core PR draft

**Publication blocked by the user pending coordinated debugging review.**

Source: `Umberto-DEV:main` → target: `rafaelvcaetano/melonDS-android-lib:master`.

Title: Fix DS/DSi compatibility, Wi-Fi power saving and JIT invalidation

This fixes DS/DSi register handling, Wi-Fi power saving and JIT literal invalidation, and avoids redundant renderer work.

- **DS/DSi compatibility:** apply the appropriate SCFG register initialization and access restrictions when running DS software in DSi mode.
- **Wi-Fi:** automatically power down the transceiver only when automatic power-saving mode is selected. Disabled power saving must not leave the radio off after the beacon countdown.
- **JIT:** fold literals only when their memory is tracked for invalidation, and use the actual write mask when invalidating a block. Both ARM64 and x86_64 backends check whether a literal was registered.
- **JIT teardown:** release fast-memory mappings while ARM9 is still alive. Strict Linux UBSan exposed a read of ARM9's DTCM configuration after its destruction; backing memory remains owned by the JIT until all other components have been destroyed.
- **JIT fast memory on Android:** bound DTCM mappings to their configured windows and physical offsets, keep map/unmap symmetric, and invalidate the old RAM mapping when DTCM moves. This fixes RAM/DTCM aliasing reproduced on Android ARM64; sub-page windows retain the slow fallback.
- **Rendering:** skip unchanged compute-renderer configuration and stop generating/uploading edge data or compiling shaders for the inactive edge pass.
- **Optional networking API:** let a frontend provide hostname-to-IPv4 answers to the built-in DNS responder. The table is empty by default; ordinary resolver behavior is preserved.

### Integration

The replacement Android proposal will originate from `Umberto-DEV/melonDS-android:main`; insert its URL only after publication is authorized. Merge the core first, then update the app gitlink and submodule URL to the accepted upstream revision. The WFC settings screen edits firmware DNS settings and does not depend on the optional hostname API.

Earlier paired proposals, closed during branch consolidation: Android #1682 (https://github.com/rafaelvcaetano/melonDS-android/pull/1682) and core #19. Their descriptions and source histories have been preserved.

### Validation and limits

- Consolidation retains both local and previously published histories from the earlier `f950b5e6` proposal. Additional corrections cover teardown and DTCM mapping. Teardown matches the earlier report by maximxlss in [melonDS-emu/melonDS#2537](https://github.com/melonDS-emu/melonDS/issues/2537) and RSDuck's analysis; the local fix is not a copied upstream patch. No equivalent ready-made DTCM patch was found in the targeted search.
- Strict UBSan regressions pass on Android 15 ARM64 with 4-KiB and 16-KiB pages. The failed baseline and sentinel check isolate the RAM alias from literal folding; tests include remapping and both halves of a 32-KiB configured window. These use a synthetic cartridge, with frontend services stubbed.
- Companion app validation:267 JVM tests, ARM64 debug APK build and zero lint errors (474 warnings). Host sanitizer checks cover synthetic JIT invalidation; companion DNS and JNI tests cover packet behavior and reference lifetimes.
- **Insert final reviewed commit and successful paired CI links after the coordinated audit.** The original 180-test run remains historical evidence, not verification of the updated app.
- Game-specific Android JIT/interpreter comparisons and physical-device acceptance remain pending. Core host leak detection is disabled. Synthetic checks do not establish that Android issues #579/#751 or every ROM hack are fixed.
- A prior paired Android build received five weeks of user testing on an AYN Thor. New Android corrections still require Thor acceptance testing.

Supersedes #19 and the earlier #18. New PR publication remains blocked until the coordinated review is complete and the user authorizes it.

The subsequent Android bug-remediation campaign leaves this core revision unchanged. See [Android corrections and evidence](../2026-10-28-android-bugfixes/REPORT.md); no new core/ROM compatibility claim follows from the app fixes.

The later [Android refinement campaign](../2026-10-28-refinement/STATUS.md) also
leaves this core revision unchanged. Renderer factory-failure handling is fixed
in the Android frontend; GPU visibility and texture-reuse research has not been
applied as a core optimization. The linked report records the newer Android
runtime coverage and retains separate physical-device/performance gates.
