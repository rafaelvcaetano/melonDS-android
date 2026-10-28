# Real GLES renderer fallback regression

[RealGlesFallbackTest](../../../app/src/androidTest/java/me/magnum/melonds/ui/emulator/RealGlesFallbackTest.kt)
launches the Android app with generated DS homebrew. On GLES 3.0/3.1, requesting
Compute or OpenGL exercises the real compositor shader compiler failure and the
production software fallback. No renderer factory or GL function is replaced.

## Generate the fixture

From the repository root, before building the instrumentation APK:

```sh
python3 tools/android-tests/renderer-fixture/generate.py
```

The default output directory is `app/src/androidTest/assets`. An optional first
argument selects another output directory for inspection. The generator writes
`fallback-homebrew.nds`, `arm9-listing.txt` and `fixture.json`. These generated
default outputs are ignored by Git; commit the generator, not the binary asset.
An alternate directory is not automatically included in the instrumentation APK.

The 128 KiB image contains original ARM instructions and a generated banner. It
reads no external ROM, BIOS or Nintendo logo. Its ARM9 program swaps red/blue
backdrops on the two screens every sixteen VBlanks; ARM7 executes a self-branch.
The logo area is zero. This is an emulator direct-boot fixture, not a retail
cartridge authentication test.

The current fixture SHA-256 is
`e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4`;
the instrumentation test checks it. `fixture.json` reports generation and
structural checks only; it does not certify an Android execution.

## Prepare a dedicated AVD

- Use a disposable AVD with no user ROMs, saves, BIOS files or signed-in accounts.
  Run this test class alone, with no existing emulation session or other device
  owner. Select its serial explicitly in every ADB command.
- Build and install matching app and instrumentation APKs after generating the
  asset. Retain the source revision and dirty diff, build variant, ABI, APK hashes,
  signing identity and frontend library Build ID. Record the AVD image/API,
  fingerprint and GPU configuration. Identify the installed test package and
  runner with `adb -s "$RENDERER_AVD_SERIAL" shell pm list instrumentation`.
- Require an actual GLES 3.0 or 3.1 context. The test records a client-3 EGL
  capability probe; a launch flag or system property alone is insufficient.
  GLES 3.2 causes an assumption skip and does not cover this failure path.
- Keep the Activity visible and unlocked, without system dialogs or overlays.
  In particular, acknowledge Android's first-use fullscreen navigation hint before
  the run: it can cover the scene and fail the Activity color assertion even when
  both native screenshots are correct.

On a dedicated disposable AVD, the fullscreen hint can alternatively be prepared
through the following setting. Record its previous value in the run evidence;
this changes the test device's system setting, not the app or the assertion:

```sh
adb -s "$RENDERER_AVD_SERIAL" shell settings get secure immersive_mode_confirmations
adb -s "$RENDERER_AVD_SERIAL" shell settings put secure immersive_mode_confirmations confirmed
```

If reusing the AVD, restore the recorded value after the run. If it was `null`,
remove the setting with `settings delete secure immersive_mode_confirmations`.
The test does not restore this system setting itself.

The test selects DS mode, bundled FreeBIOS/generated firmware, direct boot and
JIT disabled. It uses `RomConfig.default()`; overriding the per-ROM console type
would require external BIOS. The existing test documents provider supplies the
generated ROM and its disposable sibling save through the normal launch path.
No commercial game import is needed.

## Run with explicit opt-in and a watchdog

Set `RENDERER_AVD_SERIAL`, `RENDERER_TEST_PACKAGE` and `RENDERER_EVIDENCE_DIR` to
your dedicated AVD, installed test package and one canonical output directory.
The examples use `androidx.test.runner.AndroidJUnitRunner`; confirm it matches the
installed instrumentation. Run Software first as a fixture/presentation control,
then Compute and OpenGL in separate instrumentation invocations:

```sh
python3 - <<'PY'
import os
from pathlib import Path
import subprocess

serial = os.environ["RENDERER_AVD_SERIAL"]
package = os.environ["RENDERER_TEST_PACKAGE"]
evidence = Path(os.environ["RENDERER_EVIDENCE_DIR"])
evidence.mkdir(parents=True, exist_ok=True)
for renderer in ("software", "compute", "opengl"):
    command = [
        "adb", "-s", serial, "shell", "am", "instrument", "-w", "-r",
        "-e", "class", "me.magnum.melonds.ui.emulator.RealGlesFallbackTest",
        "-e", "realGlesFallback", "true",
        "-e", "dedicatedFixtureAvd", "true",
        "-e", "rendererRequest", renderer,
        package + "/androidx.test.runner.AndroidJUnitRunner",
    ]
    log = evidence / (renderer + ".log")
    with log.open("w") as output:
        result = subprocess.run(command, stdout=output,
                                stderr=subprocess.STDOUT, timeout=90)
    text = log.read_text()
    skipped = any("INSTRUMENTATION_STATUS_CODE: " + code in text
                  for code in ("-3", "-4"))
    if result.returncode or skipped or "OK (1 test)" not in text:
        raise SystemExit("Run failed or skipped; inspect " + str(log))
    print(renderer + ": instrumentation passed; collect and inspect app evidence")
PY
```

Both opt-in arguments must be `true`; missing opt-in or unsuitable GLES produces
a skipped test, not successful fallback coverage. ADB's exit status alone also
does not establish a passing instrumentation result.

The test has a 60-second JUnit bound and bounded screenshot waits. Keep the outer
90-second host watchdog: cancelling a Java future cannot interrupt the native
screenshot condition wait. A host timeout stops the ADB client but may leave
instrumentation running on the device. Before retrying, stop the target and test
packages on the selected disposable AVD, confirm their processes have exited,
and inspect the failure evidence. Normal emulator shutdown should release the
screenshot worker; `CLEANUP_FAILED.txt` invalidates completion.

## What a passing run establishes

The assertions require:

- The normal Activity/JNI launch reaches a running ROM.
- Two newly requested native screenshots show opposite red/blue phases, followed
  by an Activity screenshot containing both scene colors.
- Compute/OpenGL logs contain the real `CompositorShader` compilation failure and
  `Failed to initialize renderer 2; using software rendering` for Compute, or
  renderer `1` for OpenGL. Software is the control and must not log fallback.
- The complete preference snapshot taken after test setup equals the snapshot
  while rendering. Requested renderer and scale `3` remain unchanged by fallback.

Evidence is written under the target app's `files/real-gles-<token>/`: EGL probe,
preference snapshots, two native PNGs, one Activity PNG and bounded process logcat.
Collect those files alongside instrumentation output and bounded external
crash/ANR logs. A native crash can prevent the test's own cleanup or logs from
completing. `PASS.txt` does not replace the final JUnit result, cleanup checks or
independent APK/build identity records.

The test restores its owned preferences and removes its provider fixture during
normal cleanup. Launching the app can still create session metadata or generated
firmware state, which is why a disposable AVD is required. Keep one canonical
evidence copy and remove obsolete reproducible outputs after their producers stop.

This verifies survival of real shader-factory failure, continuing guest frames and
presentation, and preservation of requested preferences on the tested Android
configuration. It does not measure rendering performance, accelerated rendering
quality, commercial-game compatibility or physical-device behavior. It does not
identify the cause of a crash in another APK or validate a different source build.
