# melonDS Android — Change Tracker

## Session: Nov 17, 2026

### Goal
Redesign the default in-game landscape layout to a PSP-style full-screen experience with ergonomic overlaid controls, and fix performance in debug builds.

---

### 1. Always use default layout
**File:** `app/src/main/java/me/magnum/melonds/impl/layout/UILayoutProvider.kt`

**What:** `getOptimalLayoutForVariant()` previously tried to find a saved custom layout variant first, only falling back to `DefaultLayoutProvider` if none existed. This meant any previously saved layout in settings would override the default.

**Change:** Simplified `getOptimalLayoutForVariant()` to always call `defaultLayoutProvider.buildDefaultLayout(variant)` directly, bypassing all saved variants.

**Why:** Ensures our new default layout is always applied regardless of what layout the user had saved previously.

---

### 2. PSP-style landscape layout with ergonomic controls
**File:** `app/src/main/java/me/magnum/melonds/impl/layout/DefaultLayoutProvider.kt`

**What:** Rewrote `buildDefaultLandscapeLayout()` with the following changes:

- **Aspect ratio fix:** Game screen is now letterboxed at the correct DS 4:3 ratio (256×192) using `centerScreenIn()`, centered in the safe area. Previously it was stretched to fill the entire display, distorting the image.

- **Controls in the black bars:** With the game letterboxed, black bars appear on the left and right. The D-pad sits in the left bar and ABXY sits in the right bar — neither overlaps the game screen at all.

- **Ergonomic positioning:** Primary buttons (D-pad, ABXY) are centered at 68% from the top of the screen, matching the natural resting position of thumbs when holding a phone in landscape.

- **SELECT / START placement:** SELECT is positioned below the D-pad, START is positioned below the ABXY cluster. Each thumb can reach its button without moving across the screen.

- **L/R buttons:** Smaller (54dp, index-finger sized), inset 8dp from edges at the top corners.

- **Visual hierarchy:** Button sizes decrease by role — D-pad/ABXY (160dp) > L/R (54dp) > SELECT/START (48dp) > utility icons (42dp).

- **Bottom screen:** Stacked behind the top screen at the same position; accessible via the swap-screens utility button.

**Previous layout:** Side-by-side screens (top screen ~60% left, bottom screen ~40% right), D-pad and ABXY at the very bottom corners, SELECT/START at center bottom overlapping the dialogue box.

---

### 3. Semi-transparent controls
**File:** `app/src/main/java/me/magnum/melonds/impl/InternalLayoutsRepository.kt`

**What:** Changed the default layout configuration to use custom opacity.

**Change:**
```
useCustomOpacity = false  →  useCustomOpacity = true
opacity = 50              →  opacity = 70
```

**Why:** At 70% opacity (alpha = 0.7) controls are clearly visible but let the game show through, reducing visual clutter — standard practice in mobile game emulators (PPSSPP, Delta, etc.).

---

### 4. Optimized native code in debug builds
**File:** `app/build.gradle.kts`

**What:** Added `arguments("-DCMAKE_BUILD_TYPE=Release")` to the `debug` build type's `externalNativeBuild` block.

**Why:** The melonDS emulator core is written in C++ and is very CPU-intensive. Without this change, debug builds compile the native code without optimizations (`-O0`), causing severe audio/video lag. Setting `CMAKE_BUILD_TYPE=Release` enables `-O3` optimizations for the native layer while keeping the APK debuggable on the Java/Kotlin side.

```kotlin
getByName("debug") {
    applicationIdSuffix = ".dev"
    externalNativeBuild {
        cmake {
            arguments("-DCMAKE_BUILD_TYPE=Release")
        }
    }
}
```

---

### Key discovery
The debug build installs as `me.magnum.melonds.dev` (note the `.dev` suffix from `applicationIdSuffix`), which is a **separate app** from the release build (`me.magnum.melonds`). To test changes, the user must launch the **melonDS (dev)** app, not the original release version.
