#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BUILD_DIR="$REPO_ROOT/app/build/host-security"
CORE_SRC="$REPO_ROOT/melonDS-android-lib/src"
mkdir -p "$BUILD_DIR"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/SavestateBoundaryTest.cpp" \
    "$CORE_SRC/Savestate.cpp" \
    -o "$BUILD_DIR/savestate-boundary-test"

python3 - "$BUILD_DIR/savestate-boundary-test" <<'PY'
import subprocess
import sys

try:
    result = subprocess.run([sys.argv[1]], timeout=3)
except subprocess.TimeoutExpired:
    print("FAIL: savestate boundary test did not terminate within 3 seconds", file=sys.stderr)
    raise SystemExit(1)
raise SystemExit(result.returncode)
PY

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/WifiTxBoundsTest.cpp" \
    -o "$BUILD_DIR/wifi-tx-bounds-test"
"$BUILD_DIR/wifi-tx-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/WifiMemoryTest.cpp" \
    -o "$BUILD_DIR/wifi-memory-test"
"$BUILD_DIR/wifi-memory-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" "$REPO_ROOT/tools/host-tests/security/MPPacketValidationTest.cpp" \
    -o "$BUILD_DIR/mp-packet-validation-test"
"$BUILD_DIR/mp-packet-validation-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DSiSaveGeometryTest.cpp" \
    -o "$BUILD_DIR/dsi-save-geometry-test"
"$BUILD_DIR/dsi-save-geometry-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DSiTitleDataBoundsTest.cpp" \
    -o "$BUILD_DIR/dsi-title-data-bounds-test"
"$BUILD_DIR/dsi-title-data-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DSiBootBoundsTest.cpp" \
    -o "$BUILD_DIR/dsi-boot-bounds-test"
"$BUILD_DIR/dsi-boot-bounds-test"

"${CC:-clang}" -std=c11 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" -c "$CORE_SRC/sha1/sha1.c" -o "$BUILD_DIR/sha1.o"
"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DSiContentValidationTest.cpp" \
    "$BUILD_DIR/sha1.o" \
    -o "$BUILD_DIR/dsi-content-validation-test"
"$BUILD_DIR/dsi-content-validation-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/FirmwareLayoutTest.cpp" \
    -o "$BUILD_DIR/firmware-layout-test"
"$BUILD_DIR/firmware-layout-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/CartridgeImageBoundsTest.cpp" \
    -o "$BUILD_DIR/cartridge-image-bounds-test"
"$BUILD_DIR/cartridge-image-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/UtilsPaddingTest.cpp" \
    "$CORE_SRC/Utils.cpp" \
    -o "$BUILD_DIR/utils-padding-test"
"$BUILD_DIR/utils-padding-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/SaveInputBoundsTest.cpp" \
    -o "$BUILD_DIR/save-input-bounds-test"
"$BUILD_DIR/save-input-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/RetroAchievementsMemoryTest.cpp" \
    -o "$BUILD_DIR/retroachievements-memory-test"
"$BUILD_DIR/retroachievements-memory-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/CartridgeTransferBoundsTest.cpp" \
    -o "$BUILD_DIR/cartridge-transfer-bounds-test"
"$BUILD_DIR/cartridge-transfer-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DLDIBoundsTest.cpp" \
    -o "$BUILD_DIR/dldi-bounds-test"
"$BUILD_DIR/dldi-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/GBAFlashBoundsTest.cpp" \
    -o "$BUILD_DIR/gba-flash-bounds-test"
"$BUILD_DIR/gba-flash-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/DSPHLEBoundsTest.cpp" \
    -o "$BUILD_DIR/dsp-hle-bounds-test"
"$BUILD_DIR/dsp-hle-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/NativeRuntimeBoundsTest.cpp" \
    -o "$BUILD_DIR/native-runtime-bounds-test"
"$BUILD_DIR/native-runtime-bounds-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/ARWorkBudgetTest.cpp" \
    -o "$BUILD_DIR/ar-work-budget-test"
"$BUILD_DIR/ar-work-budget-test"

"${CXX:-clang++}" -std=c++17 -g -O1 -fsanitize=address,undefined \
    -I"$CORE_SRC" \
    "$REPO_ROOT/tools/host-tests/security/SavestateInvariantTest.cpp" \
    -o "$BUILD_DIR/savestate-invariant-test"
"$BUILD_DIR/savestate-invariant-test"
