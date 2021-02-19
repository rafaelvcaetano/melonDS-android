# melonDS Android port
This is a WIP Android frontend for the melonDS Android port. For the Android port of the emulator, check out https://github.com/rafaelvcaetano/melonDS-android-lib

# What is working
*  Device scanning for ROMS
*  Games can boot and run
*  Sound
*  Input
*  Mic input
*  Game saves
*  Save states
*  AR cheats
*  GBA ROM support
*  DSi support
*  Controller support
*  Settings

# What is missing
*  Wi-Fi
*  OpenGL renderer
*  Customizable layouts

# Performance
Performance is solid on 64 bit devices with thread rendering and JIT enabled, and should run at full speed on flagship devices. Performance on older devices, specially
32 bit devices, is very poor due to the lack of JIT support.

# Integration with third party frontends
It's possible to launch melonDS from third part frontends. For that, you simply need to call the emulation activity with the absolute path to the ROM file. The parameters are the following:
*  Package name: `me.magnum.melonds`
*  Activity name: `me.magnum.melonds.ui.emulator.EmulatorActivity`
*  Parameters:
    * `PATH` - a string with the absolute path to the NDS ROM (ZIP files are supported)

# Building
To build the project you will need Android NDK and CMake.

Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/rafaelvcaetano/melonDS-android.git`
2.  Open the project in Android Studio.
3.  Install the Android NDK and CMake from the SDK Manager
4.  Hit Run
