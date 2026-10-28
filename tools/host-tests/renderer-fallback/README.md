# Renderer initialization failure regression

This test compiles the current production bodies of
`MelonInstance::updateRenderer()` and `GPU::SetRenderer3D()` against typed boundary
adapters. `extract.py` reads those functions from their source files on each build;
it does not maintain a second renderer-selection implementation. Its output records
the source and extracted-function SHA-256 hashes. The real `Configuration.h` supplies
the settings types and renderer enum.

The adapters provide deterministic failing/successful renderer factories, record
settings calls, and supply unrelated instance/framebuffer state. Renderer types
remain distinct polymorphic types, so the vptr sanitizer detects a downcast from
the core's software fallback to an accelerated renderer. The complete frontend
translation unit is not compiled here: it depends on Android, EGL, audio, storage,
network and other emulator components unrelated to this selection decision.

The ten scenarios cover normal software settings, successful Compute/OpenGL
initialization, initial failure of either accelerated renderer, failure when
switching in either direction between accelerated renderers, repeated failure,
recovery on a later update, and an explicit threaded-software request after failure.
They require the actual renderer and active enum to agree, requested settings to
remain intact, failed initialization to be logged, and incompatible settings never
to be applied. Successful Compute 6x and OpenGL 4x settings are preserved.

Run on the host with Clang and Python 3:

```sh
bash tools/host-tests/renderer-fallback/run.sh
```

The host runner enables ASan, UBSan and vptr checking, runs each scenario in its own
process, and removes its temporary executable. An optional first argument selects
a saved `MelonInstance.cpp` baseline without changing the working tree.

Build and run the same regression as an Android native executable:

```sh
bash tools/android-tests/build.sh --abi x86_64
bash tools/android-tests/run.sh --abi x86_64 --serial DEVICE_SERIAL --test RendererFallbackTest
```

Use `arm64-v8a` for an ARM64 Android target. Android builds use the existing UBSan
configuration plus explicit vptr checking; the executable runs all ten scenarios.

This verifies the production selection/fallback logic with deterministic factory
failure. It does not invoke the real GLES shader compiler, render a frame, inspect
screenshot pixels, or reproduce a particular device driver failure. Host execution
does not establish Android runtime behavior. An Android app build and an actual
renderer-failure run remain separate checks.
