#include "Boundary.h"
#include <cstdlib>
#include <string>

using namespace MelonDSAndroid;
using namespace melonDS;

static void expect(bool condition, const char* message) {
    if (!condition) { std::fprintf(stderr, "FAIL: %s\n", message); std::exit(1); }
}

static void configure(MelonInstance& instance, Renderer requested) {
    instance.currentConfiguration->renderer = requested;
    if (requested == Renderer::Software) {
        auto settings = std::make_unique<SoftwareRenderSettings>();
        settings->threadedRendering = true;
        instance.currentConfiguration->renderSettings = std::move(settings);
    } else if (requested == Renderer::OpenGl) {
        auto settings = std::make_unique<OpenGlRenderSettings>();
        settings->betterPolygons = true;
        settings->scale = 4;
        instance.currentConfiguration->renderSettings = std::move(settings);
    } else {
        auto settings = std::make_unique<ComputeRenderSettings>();
        settings->scale = 6;
        settings->highResCoordinates = true;
        instance.currentConfiguration->renderSettings = std::move(settings);
    }
}

static void failFactory(Renderer requested, bool fail) {
    if (requested == Renderer::Compute) RendererTest::failCompute = fail;
    else RendererTest::failGl = fail;
}

static void expectSuccess(MelonInstance& instance, Renderer requested) {
    expect(instance.currentRenderer == requested, "active renderer must match successful request");
    if (requested == Renderer::Software) {
        auto* renderer = dynamic_cast<SoftRenderer*>(&instance.nds->GPU.GetRenderer3D());
        expect(renderer && renderer->threaded, "explicit software setting must enable threading");
    } else if (requested == Renderer::Compute) {
        auto* renderer = dynamic_cast<ComputeRenderer*>(&instance.nds->GPU.GetRenderer3D());
        expect(renderer && renderer->scale == 6 && renderer->highRes, "compute quality/settings must remain unchanged");
    } else {
        auto* renderer = dynamic_cast<GLRenderer*>(&instance.nds->GPU.GetRenderer3D());
        expect(renderer && renderer->scale == 4 && renderer->betterPolygons, "GL quality/settings must remain unchanged");
    }
}

static void expectFallback(MelonInstance& instance, Renderer requested, RenderSettings* originalSettings,
                           int previousGlSettings, int previousComputeSettings, int previousSoftwareSettings) {
    expect(instance.currentRenderer == Renderer::Software, "failed factory must select software as the active renderer");
    expect(dynamic_cast<SoftRenderer*>(&instance.nds->GPU.GetRenderer3D()) != nullptr,
           "core fallback must really own a SoftRenderer");
    expect(!instance.nds->GPU.GetRenderer3D().Accelerated, "fallback must use software framebuffer/render path");
    expect(instance.currentConfiguration->renderer == requested &&
           instance.currentConfiguration->renderSettings.get() == originalSettings,
           "fallback must not overwrite the user's requested renderer/settings");
    expect(RendererTest::glSettings == previousGlSettings &&
           RendererTest::computeSettings == previousComputeSettings &&
           RendererTest::softwareSettings == previousSoftwareSettings,
           "failed accelerated request must not cast or apply incompatible settings");
    expect(RendererTest::errors > 0, "fallback must be logged");
}

static void runScenario(const std::string& scenario) {
    RendererTest::failGl = RendererTest::failCompute = false;
    RendererTest::glCreations = RendererTest::computeCreations = 0;
    RendererTest::glSettings = RendererTest::computeSettings = RendererTest::softwareSettings = RendererTest::errors = 0;
    MelonInstance instance;
    if (scenario == "software" || scenario == "compute-success" || scenario == "gl-success") {
        Renderer requested = scenario == "software" ? Renderer::Software :
                             scenario == "compute-success" ? Renderer::Compute : Renderer::OpenGl;
        configure(instance, requested);
        instance.updateRenderer();
        expectSuccess(instance, requested);
        expect(RendererTest::errors == 0, "successful initialization must not log failure");
    } else {
        Renderer requested = scenario == "gl-failure" || scenario == "compute-to-gl-failure"
            ? Renderer::OpenGl : Renderer::Compute;
        if (scenario == "gl-to-compute-failure" || scenario == "compute-to-gl-failure") {
            Renderer initial = requested == Renderer::Compute ? Renderer::OpenGl : Renderer::Compute;
            configure(instance, initial);
            instance.updateRenderer();
            expectSuccess(instance, initial);
        }
        configure(instance, requested);
        auto* originalSettings = instance.currentConfiguration->renderSettings.get();
        const int glCalls = RendererTest::glSettings;
        const int computeCalls = RendererTest::computeSettings;
        const int softwareCalls = RendererTest::softwareSettings;
        failFactory(requested, true);
        instance.updateRenderer();
        expectFallback(instance, requested, originalSettings, glCalls, computeCalls, softwareCalls);
        if (scenario == "failure-recovery") {
            failFactory(requested, false);
            instance.updateRenderer(); // The next dirty configuration event may retry.
            expectSuccess(instance, requested);
            expect(RendererTest::computeCreations == 2, "recovery must retry the requested renderer");
        } else if (scenario == "repeated-failure") {
            instance.updateRenderer();
            expectFallback(instance, requested, originalSettings, glCalls, computeCalls, softwareCalls);
            expect(RendererTest::computeCreations == 2, "the next configuration event may retry once");
            expect(RendererTest::errors == 2, "each failed initialization must be logged");
        } else if (scenario == "software-after-failure") {
            configure(instance, Renderer::Software);
            instance.updateRenderer();
            expectSuccess(instance, Renderer::Software);
        } else {
            expect(scenario == "compute-failure" || scenario == "gl-failure" ||
                   scenario == "gl-to-compute-failure" || scenario == "compute-to-gl-failure", "known scenario");
        }
    }
    std::printf("PASS: %s\n", scenario.c_str());
}

int main(int argc, char** argv) {
    if (argc == 2) {
        runScenario(argv[1]);
    } else {
        expect(argc == 1, "zero or one scenario argument required");
        for (const auto* scenario : {"software", "compute-success", "gl-success", "compute-failure",
                "gl-failure", "gl-to-compute-failure", "compute-to-gl-failure", "failure-recovery",
                "repeated-failure", "software-after-failure"}) {
            runScenario(scenario);
        }
    }
}
