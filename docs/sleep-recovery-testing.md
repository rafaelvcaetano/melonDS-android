# Sleep Recovery Testing

Recovery policy and event framing use the project's standard JVM test source
set under `app/src/test`.

```sh
./gradlew :app:testGitHubProdDebugUnitTest
```

`RecoveryPolicyTest` covers:

- Automatic restore for known non-user process exits, including crashes, ANRs,
  low memory, excessive resource use, freezer, and signals.
- Rejection of explicit user stops, unknown exits, stale checkpoints, awake
  sessions, missing checkpoints, and repeated automatic attempts.
- Exact-session exit-record selection.
- Sleep start/resume state transitions.
- One-shot automatic recovery.
- Hardcore preservation for automatic recovery and downgrade for manual
  recovery.

`EmulatorEventFrameDecoderTest` covers fragmented, coalesced, unknown, and
invalid native event frames.

The nightly workflow runs the JVM suite before building the release artifact.
These tests require no Android device, emulator, BIOS, firmware, or ROM.

## Backup regression

Android Auto Backup can stop a sleeping emulator process after saving app data.
It can then restart the existing emulator task inside a `FullBackupAgent`
process before normal application initialization. Recovery cannot run if the
activity fails during that startup. The application therefore disables backup;
recovery checkpoints must remain local runtime state instead of backup data.

Verify every release variant's merged manifest contains:

```xml
<application android:allowBackup="false" ... />
```

For device regression testing:

1. Start a ROM, turn the screen off, and wait for its checkpoint.
2. Confirm `adb shell dumpsys package <application-id>` does not list
   `ALLOW_BACKUP`.
3. Run `adb shell bmgr backupnow <application-id>`.
4. Confirm Android reports the package is not eligible for backup, does not
   start `{android/FullBackupAgent}`, and leaves the sleeping process/session
   intact.
5. Wake the device and confirm animation, input, audio, RTC, and both displays
   resume normally.

## Manual acceptance

Android process selection, native state serialization, rendering, audio, and
input still require a final device acceptance pass. This is intentionally not
part of the automated suite.

For a release candidate:

1. Start a ROM and a firmware session, then turn the screen off long enough for
   a checkpoint.
2. Confirm normal wake resumes animation, input, audio, RTC, and both displays.
3. If the sleeping process ends for any known non-user reason, confirm reopening
   restores automatically without a dialog.
4. Confirm explicit user stops are discarded without a dialog. Unknown exits
   and stale or corrupt checkpoints must use the fallback prompt; manual restore
   must disable Hardcore.

The recovery dialog can export a diagnostic ZIP containing session metadata,
the recovery journal, recent `ApplicationExitInfo` records, and an exit trace
when Android provides one.
