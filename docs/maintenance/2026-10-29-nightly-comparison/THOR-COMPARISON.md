# Official Nightly runtime comparison protocol — not yet executed

The later [in-place update campaign](../2026-10-29-thor-update/README.md) tested
the installed modified 2.1.4 against 2.1.5. It did not execute the full protocol
below against the official release. Keep those baselines and conclusions separate.

Use the pinned official Nightly and current candidate from [BASELINE.json](BASELINE.json).
Recheck the official release when the Thor becomes available; preserve the old
baseline identity if the tag has moved. Coordinate device ownership first.

## Prepare once

- Record device/OS/GPU-driver versions, screen refresh/brightness, power/fan mode,
  battery/charging state, thermal status and exact package/APK hashes.
- Preserve original ROMs, BIOS and raw saves. Use isolated test copies and record
  hashes before/after. Keep existing installations and settings recoverable.
- Record effective settings separately for each package: renderer, resolution,
  filtering, JIT, audio, clock sync, cheats, library paths and permissions.
- Use the same legal homebrew or user-owned game scene, starting state and input
  sequence. Do not transfer savestates blindly between different core revisions.

## Functional comparison

| Scenario | Observe before and after |
| --- | --- |
| Library startup | Time until library is usable and time until scan finishes, separately; cached/uncached and invalid-provider cases |
| Cheat import | Valid input, malformed input and cancellation; old database unchanged on failure, next import succeeds |
| Guided BIOS setup | Recognition, error explanation, retry, interrupted copy and preserved existing configuration; new flow has no direct official equivalent |
| Cheats UI | Find an ordinary code and a grouped option; large text, keyboard, controller and accessibility; selection must not enable unexpectedly |
| Audio/input | Microphone source changes, fast-forward, focus loss, sleep/wake and resume with the user's normal peripherals |
| Rendering/display | Same scenes at Software 1x and available accelerated settings; disconnect/reconnect, output images, visible 2D/3D elements |
| Persistence | Normal Continue, in-game SAVE and cold restart on copies; inspect unexpected changes, never replace this with savestates |

For each reported defect, first reproduce the specific failure on an appropriate
baseline, then repeat the same steps on the patch. If it does not reproduce,
record that result rather than inventing a speedup or declaring universal repair.

## Performance comparison

- The retained `.dev` APK is suitable for correctness diagnostics, not an
  official-release performance comparison. Prepare a non-debuggable candidate
  with production-equivalent shrinking/native optimization, and record build
  flags and on-device compilation state. Macrobenchmark recommends a locally
  signed release-like target and reports invalid benchmark environments.
  [Android Macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
- Use at least five paired measured repetitions for startup and fixed scenes,
  with warm-up separate. Alternate A/B order and record thermal state; five is
  the initial protocol, not proof of statistical power. Report individual
  values, median and spread; extend sampling if variability hides the effect.
- Separate cold/warm startup and short/sustained gameplay. Include a sustained
  thermal run of at least 15 minutes before claiming stable gains.
  [Android thermal/performance guidance](https://developer.android.com/games/optimize/adpf/best-practices-adpf).
- Capture bounded Perfetto traces for scheduling, CPU frequency and process
  memory; record missing GPU counters as unavailable. Keep RSS/anonymous memory,
  PSS, swap, file I/O and emulated writes distinct. Allocation or RSS does not
  measure every RAM write. [Perfetto analysis reference](https://perfetto.dev/docs/getting-started/android-trace-analysis).
- Treat temperatures according to the identified sensor: battery temperature
  is not CPU/GPU temperature. CPU voltage may not be exposed at all.
- An energy claim needs a validated counter or meter, known units and charging
  conditions. USB charging and battery-current estimates can confound the result;
  CPU utilization alone is not watts or joules. Record unsupported measurements.

## Results to retain

- Small per-run CSV with build/scene/settings/thermal state, durations, frame
  timing, memory and available power counters; every result links to its trace.
- Graphs of startup distributions, frame-time percentiles and aligned
  CPU/memory/thermal time series. Add energy plots only with valid measurements.
- Screenshots at matching scenes for visual quality; label 2D/3D differences.
- Exact candidate/previous build, original fixtures and unique evidence.
  Remove only known regenerable intermediates after producers stop.

No physical-device result or estimated gain is filled in ahead of the test.
