# melonDS Android port
This is a WIP Android frontend for the melonDS Android port. For the Android port of the emulator, check out https://github.com/rafaelvcaetano/melonDS-android-lib

# What is working
*  Device scanning for ROMS
*  Games can boot and run
*  Input
*  Game saves
*  Save states
*  GBA ROM support
*  Settings (WIP)
*  Controller support

# What is kinda working
*  Sound (works properly at full speed only)

# What is missing
*  Wi-Fi
*  More settings
*  Mic input
*  OpenGL renderer

# Performance
Performance is far from OK, but acceptable on high end devices. Right now, this is more of a proof of concept that anything else. But at least we now know that it (kinda) works.

# Integration with third party frontends
It's possible to launch melonDS from third part frontends. For that, you simply need to call the emulation activity with the absolute path to the ROM file. The parameters are the following:
*  Package name: `me.magnum.melonds`
*  Activity name: `me.magnum.melonds.ui.emulator.EmulatorActivity`
*  Parameters:
    * `PATH` - a string with the absolute path to the NDS ROM (ZIP files are not yet supported)

# Building
To build the project you will need Android NDK and CMake.

Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/rafaelvcaetano/melonDS-android.git`
2.  Open the project in Android Studio.
3.  Install the Android NDK and CMake from the SDK Manager
4.  Hit Run
