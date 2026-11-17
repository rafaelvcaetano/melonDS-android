# melonDS Android — Common Commands

## Build & Install

| Command | What it does |
|---|---|
| `bash gradlew :app:installGitHubProdDebug` | Build and install the debug APK on a connected device. This is the main command for testing changes. Installs as `me.magnum.melonds.dev`. |
| `bash gradlew :app:clean :app:installGitHubProdDebug` | Clean build — wipes all cached outputs before rebuilding. Use this when changes aren't reflected after a normal build. Takes ~2–3 minutes. |
| `bash gradlew :app:assembleGitHubProdDebug` | Build the debug APK without installing it. Output: `app/build/outputs/apk/gitHubProd/debug/app-gitHub-prod-debug.apk` |
| `bash gradlew :app:assembleGitHubProdRelease` | Build a release APK. Requires a signing keystore configured in `build.gradle.kts`. |

> **Note:** Use `bash gradlew` instead of `./gradlew` to avoid permission issues on macOS.

---

## ADB (Android Debug Bridge)

### Device
| Command | What it does |
|---|---|
| `adb devices` | List connected devices and their serial numbers. |
| `adb shell date` | Show the current time on the device. |

### App Management
| Command | What it does |
|---|---|
| `adb install -r <path/to/apk>` | Install or reinstall an APK directly (bypasses Gradle). The `-r` flag keeps existing app data. |
| `adb uninstall me.magnum.melonds.dev` | Uninstall the debug build from the device. |
| `adb shell pm list packages \| grep melonds` | List all installed melonDS packages. Debug = `me.magnum.melonds.dev`, release = `me.magnum.melonds`. |
| `adb shell pm path me.magnum.melonds.dev` | Show the installed APK path on the device. |
| `adb shell dumpsys package me.magnum.melonds.dev \| grep -E "version\|time"` | Show installed version name, version code, install time, and last update time. Useful for verifying a fresh install. |

### Screenshots & Debugging
| Command | What it does |
|---|---|
| `adb exec-out screencap -p > screenshot.png` | Take a screenshot from the device and save it locally. |
| `adb logcat -s melonds` | Stream logs from the melonDS app. |
| `adb shell monkey -p me.magnum.melonds.dev -c android.intent.category.LAUNCHER 1` | Launch the debug app from the terminal. |

---

## Git Submodules

The native C++ libraries are managed as git submodules. These must be initialized before the first build.

| Command | What it does |
|---|---|
| `git submodule update --init --recursive` | Initialize and pull all submodules (`melonDS-android-lib`, `enet`, `faad2`, `oboe`). Required after a fresh clone or if the native build fails with missing directory errors. |
| `git submodule status` | Show the current state of all submodules. |

---

## Build Flavors

The app has two flavor dimensions that combine into build variants:

| Dimension | Values | Notes |
|---|---|---|
| `version` | `gitHub`, `playStore` | `gitHub` includes 32-bit (`armeabi-v7a`) support. `playStore` does not. |
| `build` | `prod`, `nightly` | `nightly` adds `.nightly` to the app ID and version name. |

The full variant name follows the pattern: `<version><build><buildType>`, e.g.:
- `GitHubProdDebug` — standard debug build (most common for development)
- `GitHubProdRelease` — GitHub release build (needs signing)
- `PlayStoreProdRelease` — Play Store release build

---

## Local Setup

The file `local.properties` at the repo root must exist with the Android SDK path:

```
sdk.dir=/Users/meetrajput/Library/Android/sdk
```

This file is gitignored and must be created manually after cloning.
