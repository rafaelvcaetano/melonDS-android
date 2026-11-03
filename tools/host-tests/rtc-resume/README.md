# RTC resume integration control

Run `bash tools/host-tests/rtc-resume/run.sh`, or build/run `RtcResumeTest` with
`tools/android-tests`. The runner incrementally rebuilds the host core. The
extractor records source/body hashes and compiles exact `setDateTime`, `syncRTC`
and `onResumed` bodies. Only the surrounding instance/configuration declarations
are adapted; NDS, RTC and host time conversion are real.

Six assertions cover disabled sync, a one-hour lag, twenty repeated resumes,
an ahead clock, small drift and a second ten-minute lag. No emulated frames run:
this is a numerical control for the existing resume implementation. The pure
`RtcSyncTest` separately checks the exact production threshold.

This does not deliver Activity lifecycle events, exercise JNI locking, read time
from a guest ROM, certify a game's time-event policy or implement live host-clock
synchronization. No RTC behavior change accompanies this test.
