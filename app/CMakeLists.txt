project(melonDS-android-frontend)

cmake_minimum_required(VERSION 3.4.1)

include(CheckSymbolExists)
function(detect_architecture symbol arch)
    if (NOT DEFINED ARCHITECTURE)
        set(CMAKE_REQUIRED_QUIET 1)
        check_symbol_exists("${symbol}" "" ARCHITECTURE_${arch})
        unset(CMAKE_REQUIRED_QUIET)

        # The output variable needs to be unique across invocations otherwise
        # CMake's crazy scope rules will keep it defined
        if (ARCHITECTURE_${arch})
            set(ARCHITECTURE "${arch}" PARENT_SCOPE)
            set(ARCHITECTURE_${arch} 1 PARENT_SCOPE)
            add_definitions(-DARCHITECTURE_${arch}=1)
        endif()
    endif()
endfunction()

detect_architecture("__x86_64__" x86_64)
detect_architecture("__i386__" x86)
detect_architecture("__arm__" ARM)
detect_architecture("__aarch64__" ARM64)

if (ARCHITECTURE STREQUAL x86_64 OR ARCHITECTURE STREQUAL ARM64)
    option(ENABLE_JIT "Enable x64 JIT recompiler" ON)
endif()

if (ENABLE_JIT)
    add_definitions(-DJIT_ENABLED)
endif()

set(CORE-LIB ../melonDS-android-lib)
add_subdirectory(${CORE-LIB} ./melonDS-android-lib)
include_directories(${CORE-LIB}/src/android)

add_library(
        melonDS-android-frontend

        SHARED

        src/main/cpp/MelonDSAndroidJNI.cpp
        src/main/cpp/MelonDSAndroidConfiguration.cpp
        src/main/cpp/MelonDSAndroidInterface.cpp
        src/main/cpp/MelonDSNandJNI.cpp
        src/main/cpp/UriFileHandler.cpp
        src/main/cpp/JniEnvHandler.cpp
        src/main/cpp/MelonDSAndroidCameraHandler.cpp
        src/main/cpp/AndroidRACallback.cpp
)

target_link_libraries(melonDS-android-frontend melonDS-lib)