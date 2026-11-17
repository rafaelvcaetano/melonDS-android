# Project Summary

## Overview
This repository is an Android port of melonDS, a Nintendo DS and DSi emulator. It combines a native C++ emulator core with a Kotlin/Android frontend to provide a mobile-first gaming experience.

## Core Purpose
- Run DS and DSi ROMs on Android devices
- Manage ROM scanning, saves, settings, and configuration
- Provide Android-specific integrations such as intent launching and storage access
- Support release and nightly builds across different distribution channels

---

## Architecture

### High-Level Layering

```
┌─────────────────────────────────────────────────┐
│                   Android UI                     │
│  Jetpack Compose + View-based Activities/Frags   │
│  (romlist, emulator, settings, cheats, layouts)  │
├─────────────────────────────────────────────────┤
│              Domain / Business Logic             │
│  Repositories · Services · Models               │
│  (pure Kotlin, no Android dependencies)         │
├─────────────────────────────────────────────────┤
│           Data / Implementation Layer            │
│  Room DB · SharedPrefs · FileSystem · HTTP       │
│  (impl/, database/, rcheevos-api/)               │
├─────────────────────────────────────────────────┤
│              JNI Bridge Layer                    │
│  MelonEmulator.kt ↔ MelonDSAndroidJNI.cpp        │
│  MelonDSAndroidInterface.kt (native declarations)│
├─────────────────────────────────────────────────┤
│            Native Emulator Core (C++)            │
│  melonDS-android-lib (git submodule)             │
│  + Android wrappers (cpp/)                       │
└─────────────────────────────────────────────────┘
```

### Data Flow: Launching a ROM

```
RomListActivity
  → user taps ROM
  → EmulatorActivity launched (Intent with RomParcelable)
  → EmulatorViewModel.loadRom()
  → EmulatorLaunchPreconditionChecker validates BIOS/firmware paths
  → MelonEmulator.loadRom() (Kotlin JNI facade)
  → MelonDSAndroidJNI.cpp / MelonInstance.cpp (C++)
  → melonDS core begins emulation
  → NativeFrontBufferRenderer / SurfaceBackRenderer
  → EmulatorSurfaceView (GLSurfaceView) renders frames
```

### Rendering Pipeline

```
melonDS emulator thread → FrameQueue (C++) → OboeCallback (audio)
                       ↘
               NativeFrontBufferRenderer.cpp / renderer/FrameQueue
                       ↓
        FrameRenderCoordinator.kt (Kotlin) selects strategy
           ├─ SurfaceFrontRenderer  (front-buffer, Android 10+)
           └─ SurfaceBackRenderer   (double-buffered fallback)
                       ↓
                EmulatorSurfaceView.kt (GLSurfaceView)
                       ↓
               DSRenderer.kt (OpenGL ES compositing)
```

### Dependency Injection (Hilt)

All major singletons are provided via Hilt modules in `di/`:
- `AppModule` — application-scoped utilities (file access, permission handling)
- `DatabaseModule` — Room database and DAOs
- `EmulatorRuntimeModule` — emulator manager, launch checker
- `MelonModule` — repositories bound to their implementations
- `RAModule` — RetroAchievements API and repository
- `CoilModule` — image loading (Coil) with custom ROM icon fetcher
- `MigrationModule` — SharedPreferences versioned migrators

---

## Module Structure

### `app/` — Main Application Module
The core Android application. Contains all Kotlin source, C++ wrappers, resources, and the Room database schema.

### `melonDS-android-lib/` — Native Emulator Core (Git Submodule)
The upstream melonDS C++ emulator library compiled as a static/shared library. All emulation logic (CPU, GPU, memory, Wi-Fi, DSi) lives here. The `app/src/main/cpp/` files are Android wrappers that sit on top of this.

### `common/` — Shared Kotlin Utilities
Single file: `SuspendRunCatching.kt` — a coroutine-safe version of `runCatching` that does not swallow `CancellationException`.

### `rcheevos-api/` — RetroAchievements HTTP API Client
Standalone Kotlin module wrapping the RetroAchievements REST API. Completely decoupled from Android; only depends on Ktor/OkHttp and Kotlin Serialization.

### `masterswitch/` — Custom Preference Widget
Android preference library providing a master-switch preference widget used in the Settings UI.

### `buildSrc/` — Build Logic
Gradle version catalog and shared build configuration DST.

---

## Key Source Files

### Native Layer — `app/src/main/cpp/`

| File | Purpose |
|------|---------|
| `MelonDSAndroidJNI.cpp` | Primary JNI entry point; maps all Kotlin `external fun` declarations to C++ implementations |
| `MelonInstance.cpp/h` | Owns the melonDS emulator instance lifecycle (boot, run loop, pause, stop) |
| `MelonDS.cpp/h` | Thin C++ wrapper around the core melonDS API, centralising emulator state |
| `MelonDSAndroidInterface.cpp/h` | Implements the `PlatformAndroid` interface so melonDS callbacks reach Android |
| `MelonDSAndroidConfiguration.cpp/h` | Translates `EmulatorConfiguration` from Kotlin into the C++ config structs melonDS needs |
| `CartLoader.cpp/h` | Loads NDS/GBA ROM carts and handles GBA slot config |
| `EmulatorArgsBuilder.cpp/h` | Builds the full argument set before each emulator boot |
| `DSiSupport.cpp/h` | DSi-specific setup: NAND mounting, title installation |
| `MelonDSAudio.cpp/h` | Bridges melonDS audio output to Oboe (low-latency Android audio) |
| `OboeCallback.cpp/h` | Oboe stream callback that pulls audio from the emulator ring buffer |
| `MicInputOboeCallback.cpp/h` | Oboe input stream reading the device microphone for DS mic emulation |
| `MelonDSAndroidCameraHandler.cpp/h` | Implements camera frame delivery for DSi camera emulation |
| `NativeFrontBufferRenderer.cpp` | C++ side of the front-buffer rendering path (Android 10+ `ASurfaceTransaction`) |
| `NativeGlContext.cpp` | Creates and manages a native EGL context used by the renderer |
| `OpenGLContext.cpp/h` | RAII wrapper around EGL display/surface/context lifecycle |
| `PlatformAndroid.cpp/h` | Implements the melonDS `Platform::*` host callbacks on Android |
| `PlatformAndroid_AAC.cpp` | AAC decoder via `faad2` for DSi AAC audio files |
| `RewindManager.cpp/h` | Ring-buffer of compressed save-state snapshots for the rewind feature |
| `RetroAchievementsMapper.cpp/h` | Maps rcheevos runtime events to JVM-visible types via JNI |
| `EmulatorMessageQueueJNI.cpp/h` | Thread-safe JNI queue for posting emulator events to Kotlin |
| `AndroidMelonEventMessenger.cpp/h` | Routes native emulator events (achievements, errors) back to Kotlin callbacks |
| `RomIconBuilder.cpp/h` | Decodes the ROM header icon bitmap and returns it as a pixel buffer |
| `JniEnvHandler.cpp/h` | Utility for attaching/detaching JNI env on worker threads |
| `MelonLog.h` | Android-logcat logging macros used throughout native code |

**`renderer/` subdirectory**

| File | Purpose |
|------|---------|
| `FrameQueue.cpp/h` | Lock-free ring buffer that moves rendered frames from emulator thread to render thread |
| `Renderer.h` | Abstract renderer interface implemented by both front-buffer and back-buffer paths |
| `ScreenshotRenderer.cpp/h` | Off-screen render path used to capture screenshots and save-state thumbnails |

### JNI Facade — Root of `me.magnum.melonds` package

| File | Purpose |
|------|---------|
| `MelonEmulator.kt` | The primary Kotlin facade for all emulator operations; all `external` JNI functions are declared here (loadRom, start, pause, resume, saveState, etc.) |
| `MelonDSAndroidInterface.kt` | Declares `external` JNI functions for Android-side callbacks (screen update, input, mic) |
| `MelonDSiNand.kt` | JNI façade for DSi NAND operations (open, install titles, close) |
| `MelonDSApplication.kt` | Application class; initialises Hilt and app-level startup |

### Domain Layer — `domain/`

#### Repositories (interfaces)
| File | Purpose |
|------|---------|
| `RomsRepository.kt` | Scan directories, observe ROM list, persist ROM metadata |
| `SettingsRepository.kt` | Read/write all app and emulator settings |
| `CheatsRepository.kt` | CRUD for cheat databases, folders, and individual cheats |
| `LayoutsRepository.kt` | Read/write screen layout configurations |
| `SaveStatesRepository.kt` | Enumerate and load/save save-state slots |
| `BackgroundRepository.kt` | Manage custom background images for the emulator overlay |
| `RetroAchievementsRepository.kt` | Fetch and cache RA game data, achievements, user progress |
| `DSiWareMetadataRepository.kt` | Look up DSiWare title metadata via NUS |
| `UpdatesRepository.kt` | Check for and download app updates (nightly/GitHub flavor) |

#### Services (interfaces)
| File | Purpose |
|------|---------|
| `EmulatorManager.kt` | Orchestrates a full emulation session (preconditions → boot → run → teardown) |
| `EmulatorLaunchPreconditionChecker.kt` | Validates BIOS/firmware files and configuration before launching |
| `DSiNandManager.kt` | Opens DSi NAND images and installs/removes DSiWare titles |
| `ConfigurationDirectoryVerifier.kt` | Verifies that required system file directories are accessible |
| `UpdateInstallManager.kt` | Downloads and triggers install of update APKs |

#### Domain Models (`domain/model/`)
Core data structures shared across all layers. Notable ones:

| File | Purpose |
|------|---------|
| `EmulatorConfiguration.kt` | Full emulator boot config (console type, BIOS paths, renderer settings, etc.) |
| `FirmwareConfiguration.kt` | Firmware-specific settings (username, language, birthday, MAC, etc.) |
| `RendererConfiguration.kt` | Video renderer settings (OpenGL / software, filtering, threading) |
| `LayoutConfiguration.kt` | UI layout config (screen positions, button layout, orientation) |
| `RomInfo.kt` | ROM identity: title, game code, CRC |
| `rom/Rom.kt` | A discovered ROM with its URI, metadata, and per-ROM config |
| `rom/config/RomConfig.kt` | Per-ROM overrides (console type, GBA slot, mic source) |
| `layout/UILayout.kt` | Full layout definition (screen component positions, background) |
| `layout/LayoutComponent.kt` | Enum of all placeable UI elements (screens, buttons, joystick) |
| `emulator/EmulatorEvent.kt` | Sealed class of events the native layer sends back (saveState, shutdown, etc.) |
| `retroachievements/*.kt` | RA game summaries, achievement sets, user progress, leaderboards |
| `rewind/RewindWindowPosition.kt` | Tracks position within the rewind ring buffer |

### Implementation Layer — `impl/`

| File | Purpose |
|------|---------|
| `FileSystemRomsRepository.kt` | Scans URIs for `.nds`/`.dsi` files; caches metadata in Room |
| `SharedPreferencesSettingsRepository.kt` | Persists all settings in `SharedPreferences` with typed accessors |
| `RoomCheatsRepository.kt` | Room-backed cheat CRUD; also parses XML cheat databases via SAX |
| `InternalLayoutsRepository.kt` | Loads/saves layouts from JSON files in app-private storage |
| `FileSystemSaveStatesRepository.kt` | Finds save-state files adjacent to ROMs or in a configured directory |
| `AndroidRetroAchievementsRepository.kt` | Calls `rcheevos-api` and caches results in Room |
| `AndroidDSiNandManager.kt` | Calls `MelonDSiNand.kt` JNI to manipulate DSi NAND images |
| `NdsRomCache.kt` | In-memory LRU cache for parsed ROM metadata |
| `RomIconProvider.kt` | Calls `RomIconBuilder` JNI to decode DS ROM icons; backed by Coil cache |
| `SaveStateScreenshotProvider.kt` | Renders save-state thumbnails via the native screenshot renderer |
| `BackgroundThumbnailProvider.kt` | Scales background images for the layout editor thumbnail |
| `XmlCheatDatabaseSAXHandler.kt` | SAX parser for Action Replay / XML cheat files |
| `SettingsBackupManager.kt` | Exports/imports settings as JSON for backup/restore |
| `NusDSiWareMetadataRepository.kt` | Fetches DSiWare title info from Nintendo's NUS servers |
| `ScreenUnitsConverter.kt` | Converts between dp, px and physical screen dimensions |

### Database Layer — `database/`

Room database (`MelonDatabase`) with the following DAOs and entity groups:

| DAO | Tables | Purpose |
|-----|--------|---------|
| `GameDao` | `GameEntity` | Stores game identity (CRC, game code) used as FK for cheats |
| `CheatDatabaseDao` | `CheatDatabaseEntity` | Tracks imported cheat database files |
| `CheatFolderDao` | `CheatFolderEntity`, `CheatFolderWithCheats` | Cheat folder hierarchy |
| `CheatDao` | `CheatEntity`, `CheatStatusUpdate` | Individual cheats with enabled/disabled state |
| `RetroAchievementsDao` | `RAGameEntity`, `RAAchievementEntity`, `RAUserAchievementEntity`, `RAPendingAchievementSubmissionEntity`, etc. | Cached RA game data, achievement sets, user progress, offline submission queue |

Database migrations are versioned in `database/migrations/`.
App-level SharedPreferences migrations are in `migrations/`.

### Dependency Injection — `di/`

| File | Bindings |
|------|---------|
| `AppModule.kt` | `UriFileHandler`, `PermissionHandler`, `UriPermissionManager`, `ScreenUnitsConverter`, coroutine dispatchers |
| `DatabaseModule.kt` | `MelonDatabase`, all DAOs |
| `MelonModule.kt` | All repository and service interface → implementation bindings |
| `EmulatorRuntimeModule.kt` | `EmulatorManager`, `EmulatorLaunchPreconditionChecker` |
| `RAModule.kt` | `RAApi` (Ktor HTTP client), `RetroAchievementsRepository` |
| `CoilModule.kt` | Coil `ImageLoader` with custom `RomIconFetcher` |
| `MigrationModule.kt` | `Migrator` with all `Migration` instances ordered |

### UI Layer — `ui/`

#### `ui/emulator/` — The core emulation screen
| File | Purpose |
|------|---------|
| `EmulatorActivity.kt` | Host activity; full-screen, immersive; delegates rendering to `EmulatorSurfaceView` |
| `EmulatorViewModel.kt` | Manages emulation session state, pause/resume, save states, rewind, RetroAchievements |
| `EmulatorRetroAchievementsViewModel.kt` | Handles RA session lifecycle, achievement unlocks and popup events |
| `EmulatorSurfaceView.kt` | `GLSurfaceView` that hosts the DS screen rendering |
| `DSRenderer.kt` | `GLSurfaceView.Renderer`; composites top/bottom DS screens using OpenGL ES |
| `RuntimeLayoutView.kt` | Inflates the active layout config into real Android views (buttons, screens) |
| `RuntimeLayoutComponentViewBuilderFactory.kt` | Factory that maps `LayoutComponent` enum values to their Android view builders |
| `render/FrameRenderCoordinator.kt` | Chooses front-buffer or back-buffer render path based on Android version and device capabilities |
| `render/SurfaceFrontRenderer.kt` | Front-buffer path using `ASurfaceTransaction` (Android 10+, lowest latency) |
| `render/SurfaceBackRenderer.kt` | Traditional double-buffered EGL path for older devices |
| `render/ChoreographerFrameRenderer.kt` | Ties frame presentation to `Choreographer` vsync signals |
| `input/InputProcessor.kt` | Central dispatcher: routes touch events, button presses and controller input |
| `input/MelonTouchHandler.kt` | Maps touch coordinates on the DS bottom screen to NDS stylus coordinates |
| `input/ConnectedControllerManager.kt` | Detects connected gamepads and routes their input |
| `input/EmulatorRumbleManager.kt` | Drives Android vibrator for rumble-pak feedback |
| `rewind/model/RewindWindow.kt` | Circular buffer of `RewindSaveState` snapshots |

#### `ui/romlist/` — ROM Library Screen
| File | Purpose |
|------|---------|
| `RomListActivity.kt` | Entry point activity; hosts the ROM browser |
| `RomListViewModel.kt` | Triggers ROM scanning, exposes filtered/sorted ROM list as a Flow |
| `UpdatesViewModel.kt` | Checks for app updates (GitHub/nightly flavor only) |
| `ui/RomListScreen.kt` | Compose screen showing the scrollable ROM grid/list |
| `ui/RomList.kt` | Compose lazy list component for ROM items |
| `RomIcon.kt` | Coil-backed composable that shows the DS ROM icon |

#### `ui/cheats/` — Cheat Manager
| File | Purpose |
|------|---------|
| `CheatsActivity.kt` | Host activity for the cheat manager flow |
| `CheatsViewModel.kt` | Loads cheat databases, handles search, enable/disable, add/edit/delete |
| `CheatsNavigation.kt` | Compose Navigation graph for the cheat screens |
| `ui/GameListScreen.kt` | List of games that have cheats |
| `ui/FolderListScreen.kt` | Cheat folders within a game |
| `ui/CheatListScreen.kt` | Cheats within a folder |
| `ui/CheatSearchScreen.kt` | Full-text search across all cheats |
| `ui/EnabledCheatsListScreen.kt` | Quick view of all currently-enabled cheats |

#### `ui/settings/` — Settings Screen
| File | Purpose |
|------|---------|
| `SettingsActivity.kt` | Host activity; uses `PreferenceFragmentCompat` navigation |
| `fragments/MainPreferencesFragment.kt` | Root settings screen linking to subsections |
| `fragments/GeneralPreferencesFragment.kt` | ROM search paths, sorting, theme |
| `fragments/VideoPreferencesFragment.kt` | Renderer choice, resolution scaling, filtering |
| `fragments/AudioPreferencesFragment.kt` | Latency, interpolation, bitrate, mic source |
| `fragments/SystemPreferencesFragment.kt` | Console type, JIT toggle, firmware settings |
| `fragments/FirmwarePreferencesFragment.kt` | Built-in firmware config (name, birthday, colour, MAC) |
| `fragments/CustomFirmwarePreferencesFragment.kt` | Custom BIOS/firmware file paths |
| `fragments/InputPreferencesFragment.kt` | Controller mapping and haptic settings |
| `fragments/CheatsPreferencesFragment.kt` | Cheat database import |
| `fragments/RetroAchievementsPreferencesFragment.kt` | RA login, hardcore mode toggle |
| `fragments/RewindPreferencesFragment.kt` | Rewind buffer size and interval |
| `fragments/SaveFilesPreferencesFragment.kt` | Save file location policy |
| `preferences/*.kt` | Custom preference widgets (file pickers, birthday picker, colour picker, MAC address) |

#### `ui/layouteditor/` — Layout Editor
Compose-based drag-and-drop editor for custom DS screen/button layouts. The user can position and resize all UI components for both portrait and landscape orientations.

#### `ui/romdetails/` — ROM Details
Shows ROM metadata (icon, title, game code, save file info) and lets users tweak per-ROM settings (console type, GBA slot, mic source, layout override).

#### `ui/dsiwaremanager/` — DSiWare Manager
Manages DSiWare titles installed to a DSi NAND image: browse titles, install `.nds` DSiWare, delete titles.

#### `ui/inputsetup/` — Input Mapper
Remaps hardware controller buttons and analog axes to DS inputs.

#### `ui/backgrounds/`, `ui/layouts/` — Pickers
Browse and select custom backgrounds or saved layout configurations.

#### `ui/theme/`, `ui/common/` — Design System
Shared Compose theme tokens, colors, typography, and reusable components (ROM cards, achievement tiles, preference wrappers, loading states).

### RetroAchievements — `rcheevos-api/`

| File | Purpose |
|------|---------|
| `RAApi.kt` | Ktor HTTP client wrapping all RetroAchievements.org REST endpoints (login, game data, award achievement, submit leaderboard score) |
| `RAUserAuthStore.kt` | Interface for persisting the authenticated user token |
| `RASignatureProvider.kt` | Provides the API key/secret for signing requests |
| `dto/` | Kotlin Serialization DTOs for every API response |
| `dto/mapper/` | Mappers from DTOs to clean domain models |
| `model/` | Domain types: `RAAchievement`, `RAGame`, `RALeaderboard`, `RAUserAuth`, etc. |
| `exception/` | Typed exceptions: `UnsuccessfulRequestException`, `UserNotAuthenticatedException`, `UserTokenExpiredException` |

---

## Build Variants

| Dimension | Values | Effect |
|-----------|--------|--------|
| `distribution` | `gitHub`, `playStore` | Controls update checking and install flow; `gitHub` can self-update from GitHub Releases |
| `buildType` | `prod`, `nightly` | `nightly` enables experimental features; separate app ID and signing config |

Both NDK ABI splits (`arm64-v8a`, `armeabi-v7a`, `x86_64`) are built via CMake, with the `arm64-v8a` path benefiting from the JIT recompiler in the melonDS core.

---

## Features and Capabilities
- DS/DSi emulation with OpenGL and software renderers
- ROM scanning and library management with metadata and icon display
- Save states (8 slots per ROM) with thumbnail previews
- Rewind — ring-buffer of compressed save snapshots, seekable with a scrubber UI
- Cheat code manager with XML database import and per-game enable/disable
- RetroAchievements integration (normal and hardcore mode, leaderboards, offline queue)
- Customisable screen layout editor (positions, sizes, backgrounds, button layout)
- Hardware controller support with fully remappable bindings and rumble
- DSi camera emulation using the device camera
- DSiWare title management via NAND image
- Android intent-based ROM launching from third-party frontends (e.g. Daijishō, Pegasus)
- Performance optimised for modern 64-bit devices via JIT recompilation

## Known Limitations
- No local DS wireless multiplayer
- DSi SD card not fully supported
- No customisable button skins
- Limited display filter options
- Older 32-bit devices run slower due to lack of JIT support
