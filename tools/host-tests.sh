#!/usr/bin/env bash
# Compiles and runs the standalone host-side C++ unit tests that live next to their
# production code (RtcSyncTest.cpp, AudioOutputPolicyTest.cpp). These have zero
# Android/JNI/melonDS dependencies on purpose, so they can be built and run with a plain
# host compiler -- no NDK, no emulator, no device.
#
# It then runs the core-dependent tests under tools/host-tests/jit/, which do link the
# emulator core and so need it built for the host first (see tools/core-host-sanitize.sh).
# Those are skipped, not failed, when the core cannot be built here.
#
# Usage: tools/host-tests.sh
# Exit code: 0 if every test built and passed, non-zero otherwise.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CPP_DIR="$REPO_ROOT/app/src/main/cpp"
BUILD_DIR="$REPO_ROOT/build/host-tests"

mkdir -p "$BUILD_DIR"

CXX="${CXX:-clang++}"
CXXFLAGS=(-std=c++17 -Wall -Wextra -fsanitize=address,undefined -g -O1)
export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"

TEST_NAMES=()
TEST_RESULTS=()
OVERALL_STATUS=0

run_test() {
    local name="$1"
    shift
    local binary="$BUILD_DIR/$name"

    echo "== Compiling $name =="
    if ! "$CXX" "${CXXFLAGS[@]}" -o "$binary" "$@"; then
        echo "== $name: BUILD FAILED =="
        TEST_NAMES+=("$name")
        TEST_RESULTS+=("BUILD FAILED")
        OVERALL_STATUS=1
        return
    fi

    echo "== Running $name =="
    if "$binary"; then
        TEST_NAMES+=("$name")
        TEST_RESULTS+=("PASS")
    else
        TEST_NAMES+=("$name")
        TEST_RESULTS+=("FAIL")
        OVERALL_STATUS=1
    fi
    echo
}

# SaveManager and the FileMode translation need the core's headers (Platform.h, types.h), but
# none of its object files: the test provides its own minimal Platform implementation.
CORE_INCLUDE="$REPO_ROOT/melonDS-android-lib/src"
if [ ! -f "$CORE_INCLUDE/Platform.h" ]; then
    echo "Missing $CORE_INCLUDE/Platform.h -- run: git submodule update --init --recursive" >&2
    exit 1
fi

run_test "RtcSyncTest" "$CPP_DIR/RtcSync.cpp" "$CPP_DIR/RtcSyncTest.cpp"
run_test "AudioOutputPolicyTest" "$CPP_DIR/AudioOutputPolicy.cpp" "$CPP_DIR/AudioOutputPolicyTest.cpp"
run_test "MicInputGainTest" -fsanitize=float-cast-overflow -fno-sanitize-recover=all \
    -I"$REPO_ROOT/tools/host-tests/microphone/stubs" -I"$CPP_DIR" -I"$CORE_INCLUDE" \
    "$REPO_ROOT/tools/host-tests/microphone/MicInputGainTest.cpp" "$CPP_DIR/MicInputOboeCallback.cpp"
run_test "WildEncounterToggleTest" "$CPP_DIR/WildEncounterToggleTest.cpp"
run_test "FileModeStringTest" -I"$CORE_INCLUDE" -I"$CPP_DIR" "$CPP_DIR/FileModeString.cpp" "$CPP_DIR/FileModeStringTest.cpp"
run_test "SaveManagerFlushTest" -I"$CORE_INCLUDE" -I"$CPP_DIR" "$CPP_DIR/FileModeString.cpp" "$CPP_DIR/SaveManager.cpp" "$CPP_DIR/SaveManagerFlushTest.cpp"

if bash "$REPO_ROOT/tools/host-tests/microphone/lifecycle/run.sh"; then
    TEST_RESULTS+=("PASS")
else
    TEST_RESULTS+=("FAIL")
    OVERALL_STATUS=1
fi
TEST_NAMES+=("MicSourceSwitchTest")

# ---------------------------------------------------------------------------------------
# Core-dependent tests. Unlike everything above these link libcore.a, so they need the core
# built for the host, and they need the core's PUBLIC compile definitions: without
# JIT_ENABLED the ARMJIT class the test sees is the empty stub, sizeof(NDS) disagrees with
# the library's, and the very first NDS allocation overflows its heap block.
# ---------------------------------------------------------------------------------------

JIT_DIR="$REPO_ROOT/tools/host-tests/jit"
CORE_BUILD_DIR="$REPO_ROOT/app/build/core-host"
CORE_LIB="$CORE_BUILD_DIR/src/libcore.a"
TEAKRA_LIB="$CORE_BUILD_DIR/src/teakra/src/libteakra.a"

skip_core_tests() {
    echo "== Skipping the core-dependent JIT tests: $1 =="
    TEST_NAMES+=("JitInvalidationTest" "JitDifferentialTest")
    TEST_RESULTS+=("SKIPPED" "SKIPPED")
    if [ "${REQUIRE_CORE_TESTS:-0}" = "1" ]; then
        echo "Core tests are required in this environment." >&2
        OVERALL_STATUS=1
    fi
}

run_core_tests() {
    if [ ! -f "$CORE_LIB" ]; then
        echo "== Building the core for the host (tools/core-host-sanitize.sh) =="
        if ! "$REPO_ROOT/tools/core-host-sanitize.sh"; then
            skip_core_tests "the core failed to build for the host"
            return
        fi
    fi

    if [ ! -f "$CORE_LIB" ] || [ ! -f "$TEAKRA_LIB" ]; then
        skip_core_tests "libcore.a or libteakra.a is missing under $CORE_BUILD_DIR"
        return
    fi

    # The JIT is only compiled in on ARM64 and x86_64, and CMake force-disables it for
    # x86_64 on Apple, so there is nothing to test on an Intel Mac.
    local arch
    arch="$(uname -m)"
    local jit_arch_flag
    case "$arch" in
        arm64|aarch64) jit_arch_flag="-DARCHITECTURE_ARM64=1" ;;
        x86_64)
            if [ "$(uname -s)" = "Darwin" ]; then
                skip_core_tests "the core's JIT is disabled for x86_64 on macOS"
                return
            fi
            jit_arch_flag="-DARCHITECTURE_x86_64=1"
            ;;
        *)
            skip_core_tests "no JIT backend for $arch"
            return
            ;;
    esac

    # Same sanitizers as the core: mixing an instrumented library with an uninstrumented test
    # does not link.
    # -isystem, not -I: the core's headers trip -Wall -Wextra all over the place and that is
    # not this suite's business.
    local core_flags=(-isystem "$CORE_INCLUDE" -I"$CPP_DIR" -I"$JIT_DIR" -DJIT_ENABLED "$jit_arch_flag")
    local core_objects=("$JIT_DIR/HostPlatform.cpp" "$CPP_DIR/FileModeString.cpp" "$CORE_LIB" "$TEAKRA_LIB")

    run_test "JitInvalidationTest" "${core_flags[@]}" "$JIT_DIR/JitInvalidationTest.cpp" "${core_objects[@]}"

    if [ -z "${MELONDS_TEST_ROM:-}" ]; then
        echo "== Skipping JitDifferentialTest: MELONDS_TEST_ROM is not set =="
        echo "   (point it at an .nds ROM; ROMs cannot live in the repository)"
        TEST_NAMES+=("JitDifferentialTest")
        TEST_RESULTS+=("SKIPPED")
        return
    fi

    run_test "JitDifferentialTest" "${core_flags[@]}" "$JIT_DIR/JitDifferentialTest.cpp" "${core_objects[@]}"
}

# The core is huge and ASan reports a leak in every emulator object it never frees; neither
# is what these tests are about.
export ASAN_OPTIONS="${ASAN_OPTIONS:-detect_leaks=0}"
run_core_tests

for audit in nand-fat rtc-resume; do
    if bash "$REPO_ROOT/tools/host-tests/$audit/run.sh"; then
        TEST_RESULTS+=("PASS")
    else
        TEST_RESULTS+=("FAIL")
        OVERALL_STATUS=1
    fi
    TEST_NAMES+=("$audit")
done

echo "== Summary =="
for i in "${!TEST_NAMES[@]}"; do
    printf '%-24s %s\n' "${TEST_NAMES[$i]}" "${TEST_RESULTS[$i]}"
done

exit "$OVERALL_STATUS"
