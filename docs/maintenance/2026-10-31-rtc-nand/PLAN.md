# RTC resume and NAND write audit plan

Spec: ../NEW-CHAT-PROMPT.md and ../AUDIT-METHOD.md. Execute locally on main.
No publication, personal-device installation, original data changes or ROM work.

- [x] Refresh selected issue/comment/PR/commit evidence, including closed cases.
- [x] RTC: compile exact production resume/sync/set-time functions with real NDS/RTC.
  Check disabled sync, pause-sized lag, repeated resume, ahead clock and short drift.
  Distinguish existing policy from defects; no behavior change without a red case.
- [x] NAND: author a small FAT volume and synthetic encrypted NAND using real core
  disk callbacks. Import through production NANDMount; read back bytes after remount.
  Check room available, physically full volume, interrupted/error writes, cleanup/retry
  and existing unrelated data. Keep logical HOME quota as a separate hypothesis.
- [x] For demonstrated failures, retain red regression, minimally fix the first wrong
  operation, run green and controls. Do not replace production algorithms with models.
- [x] Run host sanitizer suites, relevant JVM/build/lint and Android NDK checks.
  Use a dedicated existing Android emulator only if ownership can be established;
  otherwise record Android runtime as pending. Budget under 8 GiB peak, >10 GiB free.
- [x] Independently review output/state and diff, record exact acceptance and next
  experiments in one case ledger, link affected index/coverage entries.

Initial identities: app 1cf4fa6a; core fd758240. Pre-existing app dirty paths:
AGENTS.md, docs/superpowers/plans/2026-10-24-cheat-search.md,
docs/ventuno/audit-2026-10-24/. Core clean. No build producer observed; adb server
alone running. Available space ~43 GiB. Reuse canonical output directories.

Result: see README.md for exact RED/GREEN, checks and limits. Logical quota/HOME,
whole-app ART and physical-device execution were not promoted to acceptance.
Review fixed stale-library verification and host build-definition mismatch.
