# Sleep Recovery Testing

Recovery policy and event framing use the project's standard JVM test source
set under `app/src/test`.

```sh
./gradlew :app:testGitHubProdDebugUnitTest
```

`RecoveryPolicyTest` covers:

- Eligible Android process exits: low memory, excessive resource use, freezer,
  and `SIGKILL`.
- Rejection of crashes, ANRs, user stops, stale checkpoints, awake sessions,
  missing checkpoints, and repeated automatic attempts.
- Exact-session exit-record selection.
- Sleep start/resume state transitions.
- One-shot automatic recovery.
- Hardcore preservation for automatic recovery and downgrade for manual
  recovery.

`EmulatorEventFrameDecoderTest` covers fragmented, coalesced, unknown, and
invalid native event frames.

The nightly workflow runs the JVM suite before building the release artifact.
These tests require no Android device, emulator, BIOS, firmware, or ROM.

## Manual acceptance

Android process selection, native state serialization, rendering, audio, and
input still require a final device acceptance pass. This is intentionally not
part of the automated suite.

For a release candidate:

1. Start a ROM and a firmware session, then turn the screen off long enough for
   a checkpoint.
2. Confirm normal wake resumes animation, input, audio, RTC, and both displays.
3. If Android naturally removes the sleeping process, confirm reopening restores
   automatically without a dialog and records a low-memory, freezer, resource,
   or `SIGKILL` exit.
4. Confirm user-requested stops, crashes, ANRs, stale/corrupt checkpoints, and
   manual restores use the fallback path; manual restore must disable Hardcore.

The recovery dialog can export a diagnostic ZIP containing session metadata,
the recovery journal, recent `ApplicationExitInfo` records, and an exit trace
when Android provides one.
