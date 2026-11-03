cmake_minimum_required(VERSION 3.22)
project(melonds_android_native_tests LANGUAGES C CXX)

if(NOT ANDROID)
    message(FATAL_ERROR "These tests run on Android. Configure with the Android NDK toolchain.")
endif()
if(NOT ANDROID_ABI MATCHES "^(arm64-v8a|x86_64)$")
    message(FATAL_ERROR "The JIT tests support Android arm64-v8a and x86_64 only.")
endif()
if(NOT ANDROID_STL STREQUAL "c++_static")
    message(FATAL_ERROR "Use ANDROID_STL=c++_static for the standalone test executable.")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
get_filename_component(MELONDS_TEST_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)

# Directory scope instruments the unchanged core, teakra, and the test adapter alike.
add_compile_options(
    "$<$<COMPILE_LANGUAGE:C,CXX>:-fsanitize=undefined>"
    "$<$<COMPILE_LANGUAGE:C,CXX>:-fno-sanitize-recover=undefined>"
    "$<$<COMPILE_LANGUAGE:C,CXX>:-fno-omit-frame-pointer>"
)
add_link_options(-fsanitize=undefined -fno-sanitize-recover=undefined -static-libsan)

set(BUILD_QT_SDL OFF CACHE BOOL "No frontend in native tests" FORCE)
set(ENABLE_GDBSTUB OFF CACHE BOOL "No GDB stub in native tests" FORCE)
set(ENABLE_OGLRENDERER OFF CACHE BOOL "No OpenGL in native tests" FORCE)
set(ENABLE_JIT ON CACHE BOOL "Exercise the real JIT" FORCE)
set(ENABLE_LTO OFF CACHE BOOL "Keep diagnostics inspectable" FORCE)
set(ENABLE_LTO_RELEASE OFF CACHE BOOL "Keep diagnostics inspectable" FORCE)
set(SANITIZE "" CACHE STRING "Instrumentation is configured above for every target" FORCE)
add_subdirectory("${MELONDS_TEST_ROOT}/melonDS-android-lib" core-build EXCLUDE_FROM_ALL)

add_executable(JitInvalidationTest
    "${MELONDS_TEST_ROOT}/tools/host-tests/jit/JitInvalidationTest.cpp"
    "${MELONDS_TEST_ROOT}/tools/host-tests/jit/HostPlatform.cpp"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp/FileModeString.cpp"
    AndroidDynamicLibrary.cpp
)
target_include_directories(JitInvalidationTest PRIVATE "${MELONDS_TEST_ROOT}/app/src/main/cpp")
# JIT_ENABLED and core includes come from core's PUBLIC interface. Architecture definitions
# in the core's directory do not propagate to this sibling executable's headers.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(JitInvalidationTest PRIVATE ARCHITECTURE_ARM64=1)
else()
    target_compile_definitions(JitInvalidationTest PRIVATE ARCHITECTURE_x86_64=1)
endif()
target_link_libraries(JitInvalidationTest PRIVATE core teakra ${CMAKE_DL_LIBS})

set(RTC_RESUME_DIR "${MELONDS_TEST_ROOT}/tools/host-tests/rtc-resume")
find_package(Python3 REQUIRED COMPONENTS Interpreter)
set(RTC_FUNCTIONS "${CMAKE_CURRENT_BINARY_DIR}/rtc-resume/ProductionFunctions.cpp")
add_custom_command(OUTPUT "${RTC_FUNCTIONS}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${CMAKE_CURRENT_BINARY_DIR}/rtc-resume"
    COMMAND "${Python3_EXECUTABLE}" "${RTC_RESUME_DIR}/extract.py" "${RTC_FUNCTIONS}"
    DEPENDS "${RTC_RESUME_DIR}/extract.py" "${MELONDS_TEST_ROOT}/app/src/main/cpp/MelonInstance.cpp"
    VERBATIM)
add_executable(RtcResumeTest "${RTC_RESUME_DIR}/RtcResumeTest.cpp" "${RTC_FUNCTIONS}"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp/RtcSync.cpp")
target_include_directories(RtcResumeTest PRIVATE "${RTC_RESUME_DIR}")
add_executable(NandFatTest "${MELONDS_TEST_ROOT}/tools/host-tests/nand-fat/NandFatTest.cpp")
add_executable(ExistingSaveReplacementTest
    "${MELONDS_TEST_ROOT}/tools/host-tests/nand-fat/ExistingSaveReplacementTest.cpp")
add_executable(ReplacementFaultSweepTest
    "${MELONDS_TEST_ROOT}/tools/host-tests/nand-fat/ReplacementFaultSweepTest.cpp")
set(CORE_AUDIT_TESTS RtcResumeTest NandFatTest ExistingSaveReplacementTest ReplacementFaultSweepTest)
# Optional immutable original source for before/after reproduction after the core changes.
if(NAND_FAT_BASELINE_SOURCE)
    add_executable(NandFatBaselineTest "${MELONDS_TEST_ROOT}/tools/host-tests/nand-fat/NandFatTest.cpp"
        "${NAND_FAT_BASELINE_SOURCE}")
    list(APPEND CORE_AUDIT_TESTS NandFatBaselineTest)
endif()
foreach(CORE_AUDIT_TEST IN LISTS CORE_AUDIT_TESTS)
    if(CORE_AUDIT_TEST STREQUAL "RtcResumeTest")
        set(CORE_AUDIT_PLATFORM "${MELONDS_TEST_ROOT}/tools/host-tests/jit/HostPlatform.cpp")
    else()
        set(CORE_AUDIT_PLATFORM "${MELONDS_TEST_ROOT}/tools/host-tests/nand-fat/FaultPlatform.cpp")
    endif()
    target_sources(${CORE_AUDIT_TEST} PRIVATE
        "${CORE_AUDIT_PLATFORM}"
        "${MELONDS_TEST_ROOT}/app/src/main/cpp/FileModeString.cpp"
        AndroidDynamicLibrary.cpp)
    target_include_directories(${CORE_AUDIT_TEST} PRIVATE "${MELONDS_TEST_ROOT}/app/src/main/cpp")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        target_compile_definitions(${CORE_AUDIT_TEST} PRIVATE ARCHITECTURE_ARM64=1)
    else()
        target_compile_definitions(${CORE_AUDIT_TEST} PRIVATE ARCHITECTURE_x86_64=1)
    endif()
    target_link_libraries(${CORE_AUDIT_TEST} PRIVATE core teakra ${CMAKE_DL_LIBS})
endforeach()

# Compile the production microphone callback against the real Android Oboe API.
# The test supplies PCM directly; it does not require microphone permission/hardware.
set(OBOE_BUILD_TESTS OFF CACHE BOOL "No Oboe test app" FORCE)
add_subdirectory("${MELONDS_TEST_ROOT}/app/src/main/cpp/oboe" oboe-build EXCLUDE_FROM_ALL)
add_executable(MicInputGainTest
    "${MELONDS_TEST_ROOT}/tools/host-tests/microphone/MicInputGainTest.cpp"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp/MicInputOboeCallback.cpp"
)
target_include_directories(MicInputGainTest PRIVATE
    "${MELONDS_TEST_ROOT}/app/src/main/cpp"
    "${MELONDS_TEST_ROOT}/melonDS-android-lib/src"
)
target_compile_options(MicInputGainTest PRIVATE -fsanitize=float-cast-overflow)
target_link_libraries(MicInputGainTest PRIVATE oboe)

# Isolate stream opening at the boundary while compiling the actual audio lifecycle,
# microphone reader and capture callback. No hardware stream is opened by this test.
set(MIC_LIFECYCLE_DIR "${MELONDS_TEST_ROOT}/tools/host-tests/microphone/lifecycle")
configure_file("${MELONDS_TEST_ROOT}/app/src/main/cpp/MelonDSAudio.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/mic-lifecycle/MelonDSAudio.cpp" COPYONLY)
add_executable(MicSourceSwitchTest
    "${MIC_LIFECYCLE_DIR}/MicSourceSwitchTest.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/mic-lifecycle/MelonDSAudio.cpp"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp/MicInputOboeCallback.cpp"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp/AudioOutputPolicy.cpp"
)
target_include_directories(MicSourceSwitchTest PRIVATE
    "${MIC_LIFECYCLE_DIR}/stubs"
    "${MELONDS_TEST_ROOT}/app/src/main/cpp"
    "${MELONDS_TEST_ROOT}/melonDS-android-lib/src"
    "${MELONDS_TEST_ROOT}/melonDS-android-lib/src/frontend"
)

set(NAND_TEST_DIR "${MELONDS_TEST_ROOT}/tools/host-tests/nand-jni")
configure_file("${MELONDS_TEST_ROOT}/app/src/main/cpp/MelonDSNandJNI.cpp"
    "${CMAKE_CURRENT_BINARY_DIR}/nand-boundary/MelonDSNandJNI.cpp" COPYONLY)
foreach(NAND_TEST NandJniTest StorageNandTest UtfLeaseTest)
    add_executable(${NAND_TEST} "${NAND_TEST_DIR}/${NAND_TEST}.cpp")
    target_include_directories(${NAND_TEST} PRIVATE
        "${CMAKE_CURRENT_BINARY_DIR}/nand-boundary"
        "${NAND_TEST_DIR}/stubs"
        "${MELONDS_TEST_ROOT}/melonDS-android-lib/src"
    )
    target_compile_options(${NAND_TEST} PRIVATE -Wno-deprecated-declarations)
endforeach()

# Compile the exact frontend renderer-selection and core fallback function bodies.
# Only renderer factories and unrelated instance/framebuffer state use typed adapters.
find_package(Python3 REQUIRED COMPONENTS Interpreter)
set(RENDERER_FALLBACK_DIR "${MELONDS_TEST_ROOT}/tools/host-tests/renderer-fallback")
set(RENDERER_FUNCTIONS "${CMAKE_CURRENT_BINARY_DIR}/renderer-fallback/ProductionFunctions.cpp")
add_custom_command(OUTPUT "${RENDERER_FUNCTIONS}"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${CMAKE_CURRENT_BINARY_DIR}/renderer-fallback"
    COMMAND "${Python3_EXECUTABLE}" "${RENDERER_FALLBACK_DIR}/extract.py" "${RENDERER_FUNCTIONS}"
    DEPENDS "${RENDERER_FALLBACK_DIR}/extract.py"
        "${MELONDS_TEST_ROOT}/app/src/main/cpp/MelonInstance.cpp"
        "${MELONDS_TEST_ROOT}/melonDS-android-lib/src/GPU.cpp"
    VERBATIM
)
add_executable(RendererFallbackTest "${RENDERER_FALLBACK_DIR}/RendererFallbackTest.cpp" "${RENDERER_FUNCTIONS}")
target_include_directories(RendererFallbackTest PRIVATE "${RENDERER_FALLBACK_DIR}" "${MELONDS_TEST_ROOT}/app/src/main/cpp")
target_compile_options(RendererFallbackTest PRIVATE -fsanitize=vptr -fno-sanitize-recover=vptr)
target_link_options(RendererFallbackTest PRIVATE -fsanitize=vptr -fno-sanitize-recover=vptr)

# The NDK legacy toolchain sets CMAKE_SYSTEM_VERSION to 1, independently of the API.
string(REGEX REPLACE "^android-" "" MELONDS_TEST_API "${ANDROID_PLATFORM}")
if(NOT MELONDS_TEST_API MATCHES "^[0-9]+$")
    message(FATAL_ERROR "Could not determine the target API from ANDROID_PLATFORM.")
endif()
file(WRITE "${CMAKE_BINARY_DIR}/android-test.properties"
    "abi=${ANDROID_ABI}\napi=${MELONDS_TEST_API}\n")
