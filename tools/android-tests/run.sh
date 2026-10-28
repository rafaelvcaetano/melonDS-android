#!/usr/bin/env bash
# Execute an already-built Android binary. No APKs, emulator setup, or device resets.
set -euo pipefail

usage() {
    echo "Usage: $0 [--abi arm64-v8a|x86_64] [--serial DEVICE_SERIAL] [--test JitInvalidationTest|MicInputGainTest|MicSourceSwitchTest|NandJniTest|StorageNandTest|RendererFallbackTest]"
    echo "Environment: ANDROID_SERIAL, ADB_BIN, ANDROID_SDK_ROOT/ANDROID_HOME"
}

abi=arm64-v8a
serial="${ANDROID_SERIAL:-}"
test_name=JitInvalidationTest
while [ "$#" -gt 0 ]; do
    case "$1" in
        --abi) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; abi="$2"; shift 2 ;;
        --serial) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; serial="$2"; shift 2 ;;
        --test) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; test_name="$2"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done
case "$abi" in arm64-v8a|x86_64) ;; *) usage >&2; exit 2 ;; esac
case "$test_name" in JitInvalidationTest|MicInputGainTest|MicSourceSwitchTest|NandJniTest|StorageNandTest|RendererFallbackTest) ;; *) usage >&2; exit 2 ;; esac

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$sdk_root" ]; then
    case "$(uname -s)" in
        Darwin) sdk_root="$HOME/Library/Android/sdk" ;;
        *) sdk_root="$HOME/Android/Sdk" ;;
    esac
fi
adb_bin="${ADB_BIN:-$sdk_root/platform-tools/adb}"
if [ ! -x "$adb_bin" ]; then
    echo "Missing ADB at $adb_bin; set ADB_BIN to the platform-tools executable." >&2
    exit 2
fi
adb=("$adb_bin")
if [ -n "$serial" ]; then adb+=(-s "$serial"); fi

build_dir="$repo_root/app/build/android-native-tests/$abi"
binary="$build_dir/$test_name"
metadata="$build_dir/android-test.properties"
if [ ! -f "$binary" ] || [ ! -f "$metadata" ]; then
    echo "Build first: $repo_root/tools/android-tests/build.sh --abi $abi" >&2
    exit 2
fi
built_abi="$(sed -n 's/^abi=//p' "$metadata")"
built_api="$(sed -n 's/^api=//p' "$metadata")"
if [ "$built_abi" != "$abi" ] || ! [[ "$built_api" =~ ^[0-9]+$ ]]; then
    echo "Invalid build metadata: $metadata" >&2
    exit 2
fi

"${adb[@]}" get-state
device_serial="$("${adb[@]}" get-serialno | tr -d '\r')"
device_sdk="$("${adb[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
device_abis="$("${adb[@]}" shell getprop ro.product.cpu.abilist | tr -d '\r')"
page_size="$("${adb[@]}" shell getconf PAGESIZE | tr -d '\r')"
printf 'Android device: %s\nSDK: %s\nABIs: %s\nPage size: %s\n' "$device_serial" "$device_sdk" "$device_abis" "$page_size"
if ! [[ "$device_sdk" =~ ^[0-9]+$ ]] || [ "$device_sdk" -lt "$built_api" ]; then
    echo "This executable requires Android API $built_api or newer." >&2
    exit 2
fi
case ",$device_abis," in
    *",$abi,"*) ;;
    *) echo "The device does not advertise $abi support." >&2; exit 2 ;;
esac

# mktemp owns a fresh directory; never delete a shared directory or another run's files.
remote_dir="$("${adb[@]}" shell mktemp -d /data/local/tmp/melonds-core-tests.XXXXXXXX | tr -d '\r')"
if ! [[ "$remote_dir" =~ ^/data/local/tmp/melonds-core-tests\.[A-Za-z0-9]+$ ]]; then
    echo "ADB returned an unexpected temporary path: $remote_dir" >&2
    exit 2
fi
cleanup() {
    local result=$?
    trap - EXIT
    if ! "${adb[@]}" shell "rm -f '$remote_dir/$test_name' && rmdir '$remote_dir'"; then
        echo "Could not clean this run's directory: $remote_dir" >&2
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

"${adb[@]}" push "$binary" "$remote_dir/$test_name"
"${adb[@]}" shell "chmod 700 '$remote_dir/$test_name'"
set +e
# Modern ADB shell protocol preserves remote exit codes. -T avoids a terminal; do not use -x.
"${adb[@]}" shell -T "cd '$remote_dir' && UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1 ./$test_name"
result=$?
set -e
printf 'Android %s exit code: %s\n' "$test_name" "$result"
exit "$result"
