cmake_minimum_required(VERSION 3.4.1)

set(CORE-LIB ../melonDS-android-lib)
add_subdirectory(${CORE-LIB} ./melonDS-android-lib)
include_directories(${CORE-LIB}/src/android)

add_library(
        melonDS-android-frontend

        SHARED

        src/main/cpp/MelonDSAndroidJNI.cpp)

target_link_libraries(melonDS-android-frontend melonDS-lib)