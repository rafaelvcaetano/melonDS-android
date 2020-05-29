# melonDS Android port
This is a WIP Android frontend for the melonDS Android port. For the Android port of the emulator, check out https://github.com/rafaelvcaetano/melonDS-android-lib

# What is working
*  Device scanning for ROMS
*  Games can boot and run
*  Input
*  Game saves
*  GBA ROM support
*  Settings (WIP)
*  Controller support

# What is kinda working
*  Sound (works properly at full speed only)

# What is missing
*  Save states
*  Wi-Fi
*  More settings
*  Mic input
*  OpenGL renderer

# Performance
Performance is far from OK, but acceptable on high end devices. Right now, this is more of a proof of concept that anything else. But at least we now know that it (kinda) works.

# Building
To build the project you will need Android NDK and CMake.

Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/rafaelvcaetano/melonDS-android.git`
2.  Open the project in Android Studio.
3.  Install the Android NDK and CMake from the SDK Manager
4.  Hit Run
