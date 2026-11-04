# Thumb/DTCM literal campaign plan

Status before execution: `NOT_RUN`.

## Frontier and priority

This campaign selects only `CORE-LITERAL-A64`: the first AArch64 JIT attempt
to fold a Thumb PC-relative literal whose address is in DTCM. It has priority
because the current regression uses ARM, while the Thumb-specific first path
does not require `FetchedInstr::LiteralRegistered`. NAND replacement, DTCM
mapping and RTC already have local corrections and historical regression
evidence; Android Surface recreation needs a separate UI/runtime campaign.

The Android frontend owns the production configuration and passes JIT,
literal-optimisation and fast-memory settings to the core. The core owns Thumb
decoding, address localisation, literal folding and invalidation. This campaign
does not infer an app lifecycle or game-specific cause from a core result.

## Candidate identity and limits

- App: `main` at `1cf4fa6adf3c7a8621734754d907ea0d27824fe9`, with preserved pre-existing
  dirty changes.
- Core: `main` at `fd7582400bb7e35fe0d36dbb0c89b6766c1a8f2e`, with preserved pre-existing
  `src/DSi_NAND.cpp` changes.
- Relevant source hashes before the reproducer are recorded in the dated raw
  evidence; a commit alone does not identify this worktree.
- Initial budget: 45 minutes and at most 50 MiB of retained raw output.
- Disk gate: retain at least 10 GiB free. Reuse the existing incremental host
  and NDK build trees; do not create another APK or duplicate ROM tree.

## Hypothesis and oracle

Hypothesis: `T_Comp_LoadPCRel` calls `Comp_MemLoadLiteral` before the generic
`Comp_MemAccess` guard can reject an unregistered literal. With folding on, a
DTCM value can therefore be embedded in generated AArch64 code and remain stale
after the guest changes it.

The synthetic ARM9 program enters Thumb state normally, executes
`LDR r0,[pc,#0xFC]` at `0x02000F02`, reads DTCM at `0x02001000`, and publishes
the value to main RAM at `0x02000300`. Values `A`, `B` and `C` are written to
DTCM between bounded frame runs. The independent oracle is the ARMv5 Thumb
literal-load rule: PC is `(instruction address + 4) & ~3`, so all three reads
must return the current word at `0x02001000`.

## Controls and attempted confutations

1. Target: JIT on, literal folding on, fast memory off. This isolates literal
   folding from the separate DTCM fast-memory mapping correction.
2. Healthy control: the existing ARM PC-relative DTCM case with the same core
   and fast memory off.
3. Confutation A: identical Thumb program with literal folding off.
4. Confutation B: identical Thumb program through the interpreter.
5. Sensitivity: clear the destination and destination register between values;
   an omitted or non-running load cannot accidentally preserve a passing value.

## Execution and stop criteria

1. Add only the missing synthetic Thumb case to
   `tools/host-tests/jit/JitInvalidationTest.cpp`; do not change production core.
2. Run the AArch64 host executable as supporting shared-core evidence.
3. If the target is red and both confutations are green, rebuild only the
   existing Android `JitInvalidationTest` target and run it on the prepared
   API 35 ARM64 4 KiB AVD. Run the 16 KiB AVD only to check that page size does
   not alter the fastmem-off finding.
4. Record commands, exit status, target properties, hashes and bounded logs.
5. Stop before correction. A fix requires a separate red/green phase after the
   reproducer, evidence and canonical register have been delivered.

Stop immediately if free space would fall below 10 GiB, retained output reaches
50 MiB, the core cannot be built with the required AArch64 JIT definitions, the
AVD identity differs from the prepared target, or either confutation fails for a
reason not explained by the hypothesis.
