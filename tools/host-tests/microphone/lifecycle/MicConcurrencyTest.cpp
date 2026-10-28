// Compile the production audio translation unit; only Android/Oboe boundaries
// are adapted. TSan must detect unsynchronized flags, source and stream lifetime.
#include "MelonDSAudio.h"
#include <oboe/Oboe.h>
#include <array>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <thread>

using namespace MelonDSAndroid;

static void require(bool condition, const char* message) {
    if (!condition) {
        std::fprintf(stderr, "FAIL %s\n", message);
        std::exit(1);
    }
}

static AudioSettings settingsFor(int source) {
    AudioSettings settings{};
    settings.micSource = source;
    return settings;
}

static void expectSilence() {
    std::array<s16, 16> samples;
    samples.fill(123);
    require(readMic(samples.data(), samples.size()) == 16, "disabled input fills the requested samples");
    for (auto sample : samples) require(sample == 0, "disabled input is silent");
}

static void toggleProbe(int source, bool concurrent) {
    setupAudio(settingsFor(source));
    auto toggle = [] {
        for (int i = 0; i < 100000; ++i) {
            userDisableMic();
            userEnableMic();
        }
    };
    auto read = [] {
        std::array<s16, 16> samples{};
        for (int i = 0; i < 100000; ++i)
            require(readMic(samples.data(), samples.size()) == 16, "silence/blow source fills the request");
    };
    if (concurrent) {
        std::atomic<bool> start{false};
        std::thread controller([&] {
            while (!start.load()) std::this_thread::yield();
            toggle();
        });
        start = true;
        read();
        controller.join();
    } else {
        toggle();
        read();
    }
    userDisableMic();
    expectSilence();
    cleanupAudio();
}

static void lifecycleControl(bool checkPause, bool guestFirst = false) {
    setupAudio(settingsFor(2));
    auto stream = oboe::testing::inputStream();
    userEnableMic();
    require(!stream->isStarted(), "UI enable alone must not start a mic the guest has not opened");
    startAudio();
    enableMic();
    require(stream->isStarted(), "active guest and UI enable start capture");
    if (checkPause) {
        pauseAudio();
        require(!stream->isStarted(), "pause stops capture");
        if (guestFirst) {
            // JNI pause stops audio before the emulation thread acknowledges
            // pause. A guest mic start in that last frame must stay stopped.
            disableMic();
            enableMic();
            require(!stream->isStarted(), "in-flight guest mic start must not undo pause");
        }
        // The UI can toggle while a pause menu/background pause is active; the
        // JNI path does not resume emulation. It must not restart capture.
        userDisableMic();
        userEnableMic();
        require(!stream->isStarted(), "UI enable while paused must not restart capture");
        disableMic();
        enableMic();
        require(!stream->isStarted(), "guest reset/state activity while paused must not restart capture");
        startAudio();
        require(stream->isStarted(), "resume restores allowed capture");
    }
    std::array<s16, 1024> input;
    input.fill(100);
    stream->capture(input.data(), input.size());
    userDisableMic();
    require(!stream->isStarted(), "UI disable stops capture");
    expectSilence();
    userEnableMic();
    std::array<s16, 8> output{};
    require(readMic(output.data(), output.size()) == 8, "resume retains queued capture");
    for (auto sample : output) require(sample == 1000, "capture gain remains ten with original polarity");
    disableMic();
    require(!stream->isStarted(), "guest mic close stops capture");
    cleanupAudio();
    require(stream->getState() == oboe::StreamState::Closed, "cleanup closes capture");
    userDisableMic();
    userEnableMic();
    require(oboe::testing::callsAfterClose == 0, "cleanup prevents later calls on the old stream");
    // An open failure uses the existing blow fallback, and a later settings
    // update retries device capture with a new empty cursor/ring.
    oboe::testing::failNextInputOpen = true;
    setupAudio(settingsFor(2));
    require(readMic(output.data(), output.size()) == 8, "failed device open keeps blow fallback");
    updateAudioSettings(settingsFor(2));
    require(readMic(output.data(), output.size()) == 0, "retry creates an empty capture ring");
    cleanupAudio();
    require(oboe::testing::inputOpens == oboe::testing::inputCloses, "every opened input stream is closed");
}

static void configurationProbe(bool hardware) {
    setupAudio(settingsFor(hardware ? 2 : 1));
    std::atomic<bool> start{false}, done{false};
    std::thread controller([&] {
        while (!start.load()) std::this_thread::yield();
        while (!done.load()) {
            userDisableMic();
            userEnableMic();
        }
    });
    // This is the sole emulation reader/core mic-control owner. Lifecycle work
    // deliberately overlaps it beyond JNI's normal pause-before-config guard.
    std::thread emulator([&] {
        std::array<s16, 16> samples{};
        while (!start.load()) std::this_thread::yield();
        while (!done.load()) {
            enableMic();
            int count = readMic(samples.data(), samples.size());
            require(count >= 0 && count <= 16, "read returns only initialized samples within the request");
            disableMic();
        }
    });
    std::thread capture;
    if (hardware) {
        capture = std::thread([&] {
            std::array<s16, 1024> input;
            input.fill(100);
            while (!start.load()) std::this_thread::yield();
            while (!done.load()) {
                if (auto stream = oboe::testing::inputStream()) stream->capture(input.data(), input.size());
                std::this_thread::yield();
            }
        });
    }
    start = true;
    for (int i = 0; i < 500; ++i) {
        updateAudioSettings(settingsFor(i % (hardware ? 3 : 2)));
        if (hardware) {
            startAudio();
            pauseAudio();
            cleanupAudio();
            setupAudio(settingsFor(2));
        }
    }
    done = true;
    controller.join();
    emulator.join();
    if (capture.joinable()) capture.join();
    userDisableMic();
    expectSilence();
    cleanupAudio();
    require(oboe::testing::callsAfterClose == 0, "no stream request after close during configuration/cleanup");
    require(oboe::testing::inputOpens == oboe::testing::inputCloses, "configuration/cleanup closes each input stream");
}

static void closeOverlap() {
    setupAudio(settingsFor(2));
    startAudio();
    enableMic();
    {
        std::lock_guard<std::mutex> lock(oboe::testing::closeMutex);
        oboe::testing::blockClose = true;
    }
    std::thread cleanup([] { cleanupAudio(); });
    {
        std::unique_lock<std::mutex> lock(oboe::testing::closeMutex);
        require(oboe::testing::closeChanged.wait_for(lock, std::chrono::seconds(5), [] {
            return oboe::testing::closeEntered;
        }), "cleanup reaches the controllable close boundary");
    }
    std::atomic<bool> toggling{false};
    std::thread controller([&] {
        toggling = true;
        for (int i = 0; i < 1000; ++i) {
            userDisableMic();
            userEnableMic();
        }
    });
    while (!toggling.load()) std::this_thread::yield();
    // readMic must not acquire the lifecycle lock held over a slow close.
    // Filling/draining and callback publication still use the existing ring lock.
    std::array<s16, 16> output{};
    const int count = readMic(output.data(), output.size());
    require(count >= 0 && count <= 16, "emulation read can finish while hardware close is pending");
    {
        std::lock_guard<std::mutex> lock(oboe::testing::closeMutex);
        oboe::testing::releaseClose = true;
        oboe::testing::closeChanged.notify_all();
    }
    cleanup.join();
    controller.join();
    require(oboe::testing::callsAfterClose == 0, "toggle never uses the stream after cleanup closes it");
    expectSilence();
}

int main(int argc, char** argv) {
    require(argc == 2, "one named scenario is required");
    const std::string scenario = argv[1];
    if (scenario == "sequential") { toggleProbe(0, false); toggleProbe(1, false); lifecycleControl(false); }
    else if (scenario == "source0") toggleProbe(0, true);
    else if (scenario == "source1") toggleProbe(1, true);
    else if (scenario == "configuration") configurationProbe(false);
    else if (scenario == "hardware") configurationProbe(true);
    else if (scenario == "close-overlap") closeOverlap();
    else if (scenario == "pause") lifecycleControl(true);
    else if (scenario == "paused-guest") lifecycleControl(true, true);
    else require(false, "unknown scenario");
    std::printf("PASS %s\n", scenario.c_str());
}
