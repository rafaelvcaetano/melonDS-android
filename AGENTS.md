# Repository workflow

- Work directly on `main` in this repository and `melonDS-android-lib`.
- Do not create additional branches or worktrees unless the user explicitly changes this instruction.
- Preserve existing local changes and original contributor attribution.
- Run the relevant JVM, Android build, lint and native checks before claiming a change is verified. Report skipped checks and device-only validation separately.
- Target melonDS Android. Host tests are supporting evidence for its shared core, not validation of an Android runtime. Do not pursue Mac frontend/JIT-specific defects in this task. Distinguish Android NDK builds, Android emulator/device execution and host diagnostics in reports; investigate known Android/core issues and existing PRs before proposing related fixes.
- Replacement upstream PR publication is currently blocked by the user pending a coordinated debugging review with the Sacred Gold Plus chat and a later explicit publication instruction. Pushing authorized work to the existing `main` branches for CI does not lift that gate.
- The upstream repositories use `master`; a future upstream PR should use this fork's `main` as its source and the upstream default as its target.

## Commit and publication text

- Write technical commit titles, bodies and trailers without assistant/tool
  branding, generated-by signatures or session links. Apply the same rule to
  pull-request descriptions and new source comments or project documentation.
- Preserve attribution to original human contributors and existing licenses.
- Apply this convention to both the Android app and its core library. Published
  history must not be rewritten or force-pushed without explicit authorization.
