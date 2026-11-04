#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/../../.." && pwd)"
build="$repo/build/host-tests/rtc-resume"
mkdir -p "$build"
python3 - "$repo" "$build/ResumeDispatchFunction.h" <<'PY'
import hashlib
from pathlib import Path
import sys
source = Path(sys.argv[1]) / "app/src/main/cpp/MelonDSAndroidJNI.cpp"
text = source.read_text()
start = text.index("JNIEXPORT void JNICALL\nJava_me_magnum_melonds_MelonEmulator_resumeEmulation(")
end = text.index("\n}\n", start) + 2
body = text[start:end]
Path(sys.argv[2]).write_text(body + "\n")
print("Production JNI resume SHA-256:", hashlib.sha256(body.encode()).hexdigest())
PY
"${CXX:-clang++}" -std=c++17 -g -O1 -Wall -Wextra -Wno-unused-parameter \
    -fsanitize=address,undefined -fno-sanitize-recover=all -pthread -I"$build" \
    "$repo/tools/host-tests/rtc-resume/ResumeDispatchTest.cpp" -o "$build/ResumeDispatchTest"
ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=halt_on_error=1 "$build/ResumeDispatchTest"
