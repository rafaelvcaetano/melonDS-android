project(melonDS-android-frontend)

cmake_minimum_required(VERSION 3.4.1)

add_compile_definitions(MELONDS_GL_HEADER=<GLES3/gl32.h>)
add_compile_definitions(EGL_EGLEXT_PROTOTYPES)

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
        src/main/cpp/NativeGlContext.cpp
        src/main/cpp/UriFileHandler.cpp
        src/main/cpp/JniEnvHandler.cpp
        src/main/cpp/MelonDSAndroidCameraHandler.cpp
        src/main/cpp/AndroidRACallback.cpp
        src/main/cpp/RAAchievementMapper.cpp
        src/main/cpp/RomIconBuilder.cpp
)

target_link_libraries(melonDS-android-frontend melonDS-lib)