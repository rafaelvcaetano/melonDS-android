#pragma once
// Host-only Android stream adapter. Sample production and consumption remain in
// unchanged MicInputOboeCallback.cpp and MelonDSAudio.cpp, not in this fake.
#include <algorithm>
#include <atomic>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <memory>
#include <mutex>
#include <ostream>
namespace oboe {
enum class DataCallbackResult { Continue, Stop };
enum class PerformanceMode { LowLatency, None, PowerSaving };
enum class Direction { Input, Output };
enum class AudioFormat { I16 };
enum class SharingMode { Shared, Exclusive };
enum class Usage { Game };
enum class InputPreset { VoiceRecognition };
enum class Result { OK, ErrorInternal };
enum class StreamState { Open, Closing, Closed };
class AudioStream;
class AudioStreamCallback {
public:
    virtual ~AudioStreamCallback() = default;
    virtual DataCallbackResult onAudioReady(AudioStream*, void*, int32_t) = 0;
};
namespace testing {
inline std::mutex streamMutex;
inline std::shared_ptr<AudioStream> lastInputStream;
inline std::atomic<bool> failNextInputOpen{false};
inline std::atomic<int> inputOpens{0}, inputCloses{0}, callsAfterClose{0};
inline std::mutex closeMutex;
inline std::condition_variable closeChanged;
inline bool blockClose = false, closeEntered = false, releaseClose = false;
inline std::shared_ptr<AudioStream> inputStream() {
    std::lock_guard<std::mutex> lock(streamMutex);
    return lastInputStream;
}
}
class AudioStream {
public:
    AudioStream(Direction direction, std::shared_ptr<AudioStreamCallback> callback)
        : direction_(direction), callback_(std::move(callback)) {}
    int32_t getSampleRate() const { return 48000; }
    Result requestStart() { return setStarted(true); }
    Result requestStop() { return setStarted(false); }
    Result requestPause() { return setStarted(false); }
    Result close() {
        if (direction_ == Direction::Input) {
            std::unique_lock<std::mutex> lock(testing::closeMutex);
            if (testing::blockClose) {
                testing::closeEntered = true;
                testing::closeChanged.notify_all();
                testing::closeChanged.wait(lock, [] { return testing::releaseClose; });
                testing::blockClose = false;
            }
        }
        // Model Oboe close waiting for an in-flight callback. This is test-only
        // device synchronization, not a lock added to the production callback.
        std::lock_guard<std::mutex> lock(callbackMutex_);
        if (state_.exchange(StreamState::Closed) != StreamState::Closed && direction_ == Direction::Input)
            ++testing::inputCloses;
        started_ = false;
        callback_.reset();
        return Result::OK;
    }
    StreamState getState() const { return state_.load(); }
    bool isStarted() const { return started_.load(); }
    void capture(void* samples, int count) {
        std::lock_guard<std::mutex> lock(callbackMutex_);
        if (started_ && state_ != StreamState::Closed && callback_)
            callback_->onAudioReady(this, samples, count);
    }
    void setPerformanceHintEnabled(bool) {}
    int getBufferCapacityInFrames() const { return 2048; }
    void setBufferSizeInFrames(int) {}
private:
    Result setStarted(bool started) {
        if (state_ == StreamState::Closed) {
            ++testing::callsAfterClose;
            return Result::ErrorInternal;
        }
        started_ = started;
        return Result::OK;
    }
    Direction direction_;
    std::atomic<StreamState> state_{StreamState::Open};
    std::atomic<bool> started_{false};
    std::mutex callbackMutex_;
    std::shared_ptr<AudioStreamCallback> callback_;
};
class StabilizedCallback {
public:
    explicit StabilizedCallback(AudioStreamCallback*) {}
};
class AudioStreamBuilder {
public:
    void setChannelCount(int) {}
    void setFramesPerCallback(int) {}
    void setSampleRate(int) {}
    void setFormat(AudioFormat) {}
    void setFormatConversionAllowed(bool) {}
    void setDirection(Direction direction) { direction_ = direction; }
    void setPerformanceMode(PerformanceMode) {}
    void setSharingMode(SharingMode) {}
    void setUsage(Usage) {}
    void setInputPreset(InputPreset) {}
    void setDataCallback(std::shared_ptr<AudioStreamCallback> callback) { callback_ = std::move(callback); }
    void setDataCallback(std::shared_ptr<StabilizedCallback>) {}
    template<typename T> void setErrorCallback(std::shared_ptr<T>) {}
    Result openStream(std::shared_ptr<AudioStream>& out) {
        if (direction_ == Direction::Input && testing::failNextInputOpen.exchange(false)) {
            out.reset();
            return Result::ErrorInternal;
        }
        out = std::make_shared<AudioStream>(direction_, callback_);
        if (direction_ == Direction::Input) {
            std::lock_guard<std::mutex> lock(testing::streamMutex);
            testing::lastInputStream = out;
            ++testing::inputOpens;
        }
        return Result::OK;
    }
private:
    Direction direction_ = Direction::Output;
    std::shared_ptr<AudioStreamCallback> callback_;
};
}
