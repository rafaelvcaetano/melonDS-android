# Host-side build for dns_override_test.cpp: exercises the real Net_Slirp.cpp /
# Net_Slirp.h from melonDS-android-lib against the DNS host-override table, on the
# desktop, with no NDK/emulator/device involved.
#
# It reuses the net-utils CMake target exactly as app/CMakeLists.txt does (see its
# add_subdirectory(${CORE-LIB}/src/net ./net-utils) at line 24): net-utils itself
# needs libslirp (vendored under melonDS-android-lib/src/net/libslirp, no system
# glib/pcap needed -- pcap headers are vendored too and its symbols are loaded
# dynamically at runtime, not link time) and enet (vendored under
# app/src/main/cpp/enet, wired up the same way app/CMakeLists.txt wires it up).
#
# Only Net_Slirp.o actually gets pulled into the test binary at link time (a static
# library only contributes the object files a symbol reference needs), so the only
# Platform:: hook the test itself has to stub is Platform::Log.
cmake_minimum_required(VERSION 3.16)

project(dns_override_test LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(BUILD_QT_SDL OFF)
set(ENABLE_GDBSTUB OFF)
set(ENABLE_JIT OFF)
set(ENABLE_OGLRENDERER OFF)

set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../..")
set(CORE_LIB "${REPO_ROOT}/melonDS-android-lib")
set(ENET_ROOT "${REPO_ROOT}/app/src/main/cpp/enet")

add_subdirectory("${ENET_ROOT}" ./enet)
target_include_directories(enet INTERFACE "${ENET_ROOT}/include")

list(APPEND CMAKE_MODULE_PATH "${CORE_LIB}/cmake")
add_subdirectory("${CORE_LIB}/src/net" ./net-utils)

add_executable(dns_override_test dns_override_test.cpp)
target_link_libraries(dns_override_test PRIVATE net-utils)
if(CMAKE_CXX_COMPILER_ID MATCHES "Clang|GNU")
    target_compile_options(dns_override_test PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(dns_override_test PRIVATE -fsanitize=address,undefined)
    target_compile_options(net-utils PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
endif()
