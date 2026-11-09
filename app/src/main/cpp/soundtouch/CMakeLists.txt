# SoundTouch 2.3.3 (https://codeberg.org/soundtouch/soundtouch, LGPL-2.1)
# Minimal build for melonDS-android: int16 sample path only.
# Upstream BPMDetect/WavFile/test/doc components are trimmed (design D5).
# sse_optimized.cpp is not compiled: SOUNDTOUCH_ALLOW_SSE is only defined for the
# float-sample template, which this build never uses.
# mmx_optimized.cpp MUST stay compiled: on the x86_64 ABI, clang does not define
# MSVC's _M_X64, so STTypes.h enables SOUNDTOUCH_ALLOW_MMX and TDStretch/FIRFilter
# newInstance() reference the MMX classes — dropping this file breaks the link.
add_library(soundtouch STATIC
        SoundTouch.cpp
        TDStretch.cpp
        RateTransposer.cpp
        AAFilter.cpp
        FIFOSampleBuffer.cpp
        FIRFilter.cpp
        InterpolateCubic.cpp
        InterpolateLinear.cpp
        InterpolateShannon.cpp
        cpu_detect_x86.cpp
        mmx_optimized.cpp
        )

target_include_directories(soundtouch PUBLIC .)

# PUBLIC (not PRIVATE): SAMPLETYPE selection lives in STTypes.h, so consumers
# including SoundTouch.h must see the same macro or they expand a float API.
target_compile_definitions(soundtouch PUBLIC SOUNDTOUCH_INTEGER_SAMPLES=1)
