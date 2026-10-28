# Microphone ownership regressions

Run from the repository root with Clang, a working ThreadSanitizer runtime and
Python 3:

```sh
bash tools/host-tests/microphone/lifecycle/run-concurrency.sh
bash tools/host-tests/microphone/lifecycle/run.sh
bash tools/host-tests/microphone/run.sh
```

The concurrency runner compiles a byte-identical copy of production
`MelonDSAudio.cpp`, production capture callback and output policy. The copy only
lets quoted Android/frontend includes resolve through explicit host adapters.
Each named scenario runs in a fresh process with a 30-second bound; temporary
sources/binaries are removed on exit. To test an older source without changing
the checkout:

```sh
MIC_AUDIO_SOURCE=/absolute/path/to/MelonDSAudio.cpp \
  bash tools/host-tests/microphone/lifecycle/run-concurrency.sh source0
```

Cases cover sequential controls, concurrent toggles against silence and blow
reads, configuration changes, capture/start/stop/cleanup overlap, a close held at
a controlled boundary, paused UI toggles, and an in-flight guest start after
pause. They check disabled silence, retained queued PCM, gain, empty new rings,
device-open failure/retry, and stream closure as well as TSan reports. The other
two runners retain their ASan/UBSan source-switch/ring/cursor and gain checks.

The stream adapter retains the supplied callback, dispatches real capture
callbacks with the requested 1024 frames, can fail open, and can hold close
until released. Its internal locks model device synchronization; they do not
replace or synchronize the production control flags/shared pointers. This is
supporting host evidence, not an Android or Oboe hardware simulation.

Production ownership:

- `micControlMutex` serializes stream pointers and Oboe start/stop/close across
  JNI settings/toggles, frontend pause/resume and guest mic requests. Guest-open
  and frontend-running flags have the same owner.
- Source and user-enable flags are atomic so `readMic` never takes that lifecycle
  lock. The single emulation reader owns the blow cursor.
- `micBufferMutex` protects callback publication/removal and the existing capture
  ring/cursors. It is never held across open/close. Oboe keeps its shared callback
  ownership until stream teardown; no new lock enters either realtime callback.
- Combined control entry points acquire output, microphone control, then buffer
  locks. Neither callback acquires either control mutex.

Android NDK compilation and runtime permission granted/denied, pause/resume,
background/foreground, source-switch and reset checks remain separate gates.
Physical microphone testing is needed to establish hardware behavior.
