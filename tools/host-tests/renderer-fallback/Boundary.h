#ifndef RENDERER_FALLBACK_BOUNDARY_H
#define RENDERER_FALLBACK_BOUNDARY_H

#include <cstdarg>
#include <cstdio>
#include <memory>
#include "Configuration.h"

namespace RendererTest {
inline bool failGl = false;
inline bool failCompute = false;
inline int glCreations = 0;
inline int computeCreations = 0;
inline int glSettings = 0;
inline int computeSettings = 0;
inline int softwareSettings = 0;
inline int errors = 0;
}

// Only renderer factories/settings and unrelated instance state are adapted.
// The tested frontend function and core fallback function are extracted unchanged.
namespace melonDS {
struct GPU;
struct Renderer3D {
    explicit Renderer3D(bool accelerated) : Accelerated(accelerated) {}
    virtual ~Renderer3D() = default;
    const bool Accelerated;
};
struct SoftRenderer final : Renderer3D {
    SoftRenderer() : Renderer3D(false) {}
    bool threaded = false;
    void SetThreaded(bool value, GPU&) { ++RendererTest::softwareSettings; threaded = value; }
};
struct GLRenderer final : Renderer3D {
    GLRenderer() : Renderer3D(true) {}
    int scale = -1;
    bool betterPolygons = false;
    static std::unique_ptr<GLRenderer> New() {
        ++RendererTest::glCreations;
        return RendererTest::failGl ? nullptr : std::make_unique<GLRenderer>();
    }
    void SetRenderSettings(bool better, int value) {
        ++RendererTest::glSettings; betterPolygons = better; scale = value;
    }
};
struct ComputeRenderer final : Renderer3D {
    ComputeRenderer() : Renderer3D(true) {}
    int scale = -1;
    bool highRes = false;
    static std::unique_ptr<ComputeRenderer> New() {
        ++RendererTest::computeCreations;
        return RendererTest::failCompute ? nullptr : std::make_unique<ComputeRenderer>();
    }
    void SetRenderSettings(int value, bool high) {
        ++RendererTest::computeSettings; scale = value; highRes = high;
    }
};
struct RendererOwner {
    std::unique_ptr<Renderer3D> current = std::make_unique<SoftRenderer>();
    void SetCurrentRenderer(std::unique_ptr<Renderer3D>&& renderer) { current = std::move(renderer); }
};
struct GPU {
    RendererOwner GPU3D;
    int framebufferInitializations = 0;
    void SetRenderer3D(std::unique_ptr<Renderer3D>&& renderer) noexcept;
    Renderer3D& GetRenderer3D() { return *GPU3D.current; }
    void InitFramebuffers() { ++framebufferInitializations; }
};
struct NDS { melonDS::GPU GPU; };
namespace Platform {
enum class LogLevel { Error };
inline void Log(LogLevel, const char* format, ...) {
    ++RendererTest::errors;
    va_list args;
    va_start(args, format);
    std::vfprintf(stderr, format, args);
    va_end(args);
}
}
}

namespace MelonDSAndroid {
struct MelonInstance {
    melonDS::NDS console;
    melonDS::NDS* nds = &console;
    std::shared_ptr<EmulatorConfiguration> currentConfiguration = std::make_shared<EmulatorConfiguration>();
    Renderer currentRenderer = Renderer::Software;
    void updateRenderer();
};
}

#endif
