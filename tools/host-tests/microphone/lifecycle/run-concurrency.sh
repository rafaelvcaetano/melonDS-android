#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../../../.." && pwd)"
build_dir="$(mktemp -d "${TMPDIR:-/tmp}/melonds-mic-concurrency.XXXXXXXX")"
trap 'rm -rf "$build_dir"' EXIT
# Preserve the exact production source while resolving Android-only quoted
# includes through the same explicit adapters as the source-switch regression.
cp "${MIC_AUDIO_SOURCE:-$repo_dir/app/src/main/cpp/MelonDSAudio.cpp}" "$build_dir/MelonDSAudio.cpp"
cmp "${MIC_AUDIO_SOURCE:-$repo_dir/app/src/main/cpp/MelonDSAudio.cpp}" "$build_dir/MelonDSAudio.cpp"
"${CXX:-clang++}" -std=c++17 -O1 -g -Wall -Wextra -pthread -fno-omit-frame-pointer \
    -fsanitize=thread \
    -I"$script_dir/stubs" -I"$repo_dir/app/src/main/cpp" \
    -I"$repo_dir/melonDS-android-lib/src" -I"$repo_dir/melonDS-android-lib/src/frontend" \
    "$script_dir/MicConcurrencyTest.cpp" "$build_dir/MelonDSAudio.cpp" \
    "$repo_dir/app/src/main/cpp/MicInputOboeCallback.cpp" \
    "$repo_dir/app/src/main/cpp/AudioOutputPolicy.cpp" -o "$build_dir/MicConcurrencyTest"
if [ "$#" -eq 0 ]; then
    set -- sequential source0 source1 configuration hardware close-overlap pause paused-guest
fi
# Each scenario gets fresh process globals. The timeout also makes a lock-order
# regression a bounded failure rather than leaving a producer running forever.
python3 - "$build_dir/MicConcurrencyTest" "$@" <<'PY'
import os
import subprocess
import sys

for scenario in sys.argv[2:]:
    print(f"Running {scenario}", flush=True)
    result = subprocess.run([sys.argv[1], scenario], timeout=30,
                            env={**os.environ, "TSAN_OPTIONS": os.environ.get("TSAN_OPTIONS", "halt_on_error=1")})
    if result.returncode:
        sys.exit(result.returncode if result.returncode > 0 else 128 - result.returncode)
PY
