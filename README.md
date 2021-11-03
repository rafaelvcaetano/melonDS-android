# melonDS Android port
This is a WIP Android frontend for the melonDS Android port. For the Android port of the emulator, check out https://github.com/rafaelvcaetano/melonDS-android-lib

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=me.magnum.melonds&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

|Rom List|Dark Theme|Pocket Physics|Layout Editor|
|---|---|---|---|
|![Screenshot 1](./.github/images/screenshot_mobile0.png)|![Screenshot 2](./.github/images/screenshot_mobile1.png)|![Screenshot 3](./.github/images/screenshot_mobile2.png)|![Screenshot 4](./.github/images/screenshot_mobile3.png)|

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
*  DSi support (experimental)
*  Controller support
*  Customizable layouts
*  Settings

# What is missing
*  Wi-Fi
*  OpenGL renderer
*  Customizable button skins
*  More display filters

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
To build the project you will need Android SDK, NDK and CMake.

## Build steps:
1.  Clone the project, including submodules with:
    
    `git clone --recurse-submodules https://github.com/rafaelvcaetano/melonDS-android.git`
2.  Install the Android SDK, NDK and CMake
3.  Build with:
    1.  Unix: `./gradlew :app:assembleGitHubRelease`
    2.  Windows: `gradlew.bat :app:assembleGitHubRelease`
4.  The generated APK can be found at `app/gitHub/release`
