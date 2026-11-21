#include "MelonDSAudio.h"
#include "MicInputOboeCallback.h"
#include "mic_blow.h"
#include "OboeCallback.h"
#include <oboe/Oboe.h>
#include <atomic>
#include <mutex>
#include <vector>
#include <cstdint>

#define MIC_BUFFER_SIZE 2048

// Auto flute override. Written from the UI thread, read from the emulation thread.
// 0 = disabled (normal microphone behaviour)
// 1 = engaged, silent (game hears no input regardless of the configured mic source)
// 2 = engaged, blowing (game hears the built-in blow sample regardless of the configured mic source)
std::atomic<int> autoMicMode{0};

// Game audio capture (used to transcribe songs played by in-game characters).
// Holds mono 48 kHz samples. Written from the audio thread, read once capture stops.
#define CAPTURE_SAMPLE_RATE 48000
std::mutex captureMutex;
std::vector<int16_t> captureBuffer;
size_t captureMaxSamples = 0;
std::atomic<bool> captureActive{false};

std::weak_ptr<MelonDSAndroid::MelonInstance> activeInstance;

std::shared_ptr<oboe::AudioStream> audioStream;
std::shared_ptr<OboeCallback> outputCallback;
std::shared_ptr<oboe::StabilizedCallback> stabilizedOutputCallback;

std::shared_ptr<oboe::AudioStream> micInputStream;
std::shared_ptr<MicInputOboeCallback> micInputCallback;

MelonDSAndroid::AudioSettings currentAudioSettings;
std::mutex micBufferMutex;
int actualMicSource = 0;
bool isMicInputEnabled = true;
bool isMicOn = false;
int micBufferReadPos = 0;

namespace MelonDSAndroid
{
    // AUDIO OUTPUT

    void resetAudioOutputStream();

    void setupAudioOutputStream(int audioLatency, int volume)
    {
        oboe::PerformanceMode performanceMode;
        switch (audioLatency) {
            case 0:
                performanceMode = oboe::PerformanceMode::LowLatency;
                break;
            case 1:
                performanceMode = oboe::PerformanceMode::None;
                break;
            case 2:
                performanceMode = oboe::PerformanceMode::PowerSaving;
                break;
            default:
                performanceMode = oboe::PerformanceMode::None;
        }

        outputCallback = std::make_shared<OboeCallback>(volume, resetAudioOutputStream);
        stabilizedOutputCallback = std::make_shared<oboe::StabilizedCallback>(outputCallback.get());

        outputCallback->activeInstance = activeInstance;

        oboe::AudioStreamBuilder streamBuilder;
        streamBuilder.setChannelCount(2);
        streamBuilder.setSampleRate(48000);
        streamBuilder.setFormat(oboe::AudioFormat::I16);
        streamBuilder.setFormatConversionAllowed(true);
        streamBuilder.setDirection(oboe::Direction::Output);
        streamBuilder.setPerformanceMode(performanceMode);
        streamBuilder.setSharingMode(oboe::SharingMode::Shared);
        streamBuilder.setUsage(oboe::Usage::Game);
        streamBuilder.setDataCallback(stabilizedOutputCallback);
        streamBuilder.setErrorCallback(stabilizedOutputCallback);

        oboe::Result result = streamBuilder.openStream(audioStream);
        audioStream->setPerformanceHintEnabled(true);
        audioStream->setBufferSizeInFrames(std::min(audioStream->getBufferCapacityInFrames(), 2048));
        if (result != oboe::Result::OK) {
            Log(Error, "Failed to init audio stream");
            outputCallback = nullptr;
            stabilizedOutputCallback = nullptr;
        }
    }

    void cleanupAudioOutputStream()
    {
        if (audioStream) {
            if (audioStream->getState() < oboe::StreamState::Closing) {
                audioStream->requestStop();
                audioStream->close();
            }

            audioStream = nullptr;
            outputCallback = nullptr;
            stabilizedOutputCallback = nullptr;
        }
    }

    void resetAudioOutputStream()
    {
        cleanupAudioOutputStream();
        setupAudioOutputStream(currentAudioSettings.audioLatency, currentAudioSettings.volume);
        if (audioStream) {
            audioStream->requestStart();
        }
    }

    // MICROPHONE

    void setupMicInputStream()
    {
        micInputCallback = std::make_shared<MicInputOboeCallback>(MIC_BUFFER_SIZE, micBufferMutex);
        oboe::AudioStreamBuilder micStreamBuilder;
        micStreamBuilder.setChannelCount(1);
        micStreamBuilder.setFramesPerCallback(1024);
        micStreamBuilder.setSampleRate(48000);
        micStreamBuilder.setFormat(oboe::AudioFormat::I16);
        micStreamBuilder.setFormatConversionAllowed(true);
        micStreamBuilder.setDirection(oboe::Direction::Input);
        micStreamBuilder.setInputPreset(oboe::InputPreset::VoiceRecognition);
        micStreamBuilder.setPerformanceMode(oboe::PerformanceMode::None);
        micStreamBuilder.setSharingMode(oboe::SharingMode::Exclusive);
        micStreamBuilder.setUsage(oboe::Usage::Game);
        micStreamBuilder.setDataCallback(micInputCallback);

        oboe::Result micResult = micStreamBuilder.openStream(micInputStream);
        if (micResult != oboe::Result::OK)
        {
            actualMicSource = 1;
            Log(Error, "Failed to init mic audio stream");
            micInputCallback = nullptr;
        }
    }

    void cleanupMicInputStream()
    {
        if (micInputStream)
        {
            micInputStream->requestStop();
            micInputStream->close();

            micInputStream = nullptr;

            std::lock_guard<std::mutex> lock(micBufferMutex);
            micInputCallback = nullptr;
        }
    }

    void startMicStreamIfAllowed()
    {
        if (actualMicSource == 2 && micInputStream && isMicInputEnabled && isMicOn)
            micInputStream->requestStart();
    }

    void userEnableMic()
    {
        isMicInputEnabled = true;
        startMicStreamIfAllowed();
    }

    void userDisableMic()
    {
        isMicInputEnabled = false;
        if (micInputStream)
            micInputStream->requestStop();
    }

    void enableMic()
    {
        isMicOn = true;
        startMicStreamIfAllowed();
    }

    void disableMic()
    {
        isMicOn = false;
        if (micInputStream)
            micInputStream->requestStop();
    }

    void startAudioCapture(int maxSeconds)
    {
        std::lock_guard<std::mutex> lock(captureMutex);
        captureMaxSamples = (size_t) CAPTURE_SAMPLE_RATE * maxSeconds;
        captureBuffer.clear();
        captureBuffer.reserve(captureMaxSamples);
        captureActive.store(true);
    }

    void captureAudio(const int16_t* stereoSamples, int frames)
    {
        if (!captureActive.load(std::memory_order_relaxed))
            return;

        // Never block the audio thread. If the lock is taken (capture is being stopped) just skip this chunk
        std::unique_lock<std::mutex> lock(captureMutex, std::try_to_lock);
        if (!lock.owns_lock())
            return;

        size_t room = captureMaxSamples - captureBuffer.size();
        size_t count = (size_t) frames < room ? (size_t) frames : room;
        for (size_t i = 0; i < count; i++)
            captureBuffer.push_back((int16_t) (((int) stereoSamples[i * 2] + (int) stereoSamples[i * 2 + 1]) / 2));
    }

    std::vector<int16_t> stopAudioCapture()
    {
        captureActive.store(false);
        std::lock_guard<std::mutex> lock(captureMutex);
        std::vector<int16_t> result = std::move(captureBuffer);
        captureBuffer = std::vector<int16_t>();
        return result;
    }

    void setAutoMicMode(int mode)
    {
        autoMicMode.store(mode, std::memory_order_relaxed);
    }

    int readMic(s16* data, int maxlength)
    {
        int micSource = actualMicSource;
        int autoMode = autoMicMode.load(std::memory_order_relaxed);
        if (autoMode == 2)
        {
            // Auto flute is blowing. Use the built-in blow sample (source 1)
            micSource = 1;
        }
        else if (autoMode == 1)
        {
            // Auto flute is engaged but between notes. Feed silence so that a real microphone can't interfere
            micSource = 0;
        }
        else if (!isMicInputEnabled)
        {
            micSource = 0;
        }

        if (micSource == 0)
        {
            memset(data, 0, maxlength * sizeof(s16));
            return maxlength;
        }

        int micBufferLength;
        s16* micBuffer;

        if (micSource == 2)
        {
            micBufferMutex.lock();
            if (!micInputCallback)
            {
                micBufferMutex.unlock();
                memset(data, 0, maxlength * sizeof(s16));
                return maxlength;
            }
            micBufferLength = MIC_BUFFER_SIZE / sizeof(s16);
            micBuffer = micInputCallback->buffer;
        }
        else
        {
            micBufferLength = sizeof(mic_blow) / sizeof(s16);
            micBuffer = (s16*) &mic_blow[0];
        }

        int readlength = 0;
        while (readlength < maxlength)
        {
            int thislen = maxlength - readlength;
            if ((micBufferReadPos + thislen) > micBufferLength)
                thislen = micBufferLength - micBufferReadPos;

            if (micSource == 2)
            {
                if (thislen > micInputCallback->bufferCount)
                    thislen = micInputCallback->bufferCount;

                micInputCallback->bufferCount -= thislen;
            }

            if (!thislen)
                break;

            memcpy(data, &micBuffer[micBufferReadPos], thislen * sizeof(s16));
            data += thislen;
            micBufferReadPos += thislen;
            if (micBufferReadPos >= micBufferLength)
                micBufferReadPos -= micBufferLength;

            readlength += thislen;
        }

        if (micSource == 2)
            micBufferMutex.unlock();

        return readlength;
    }

    // GENERAL

    void setupAudio(AudioSettings audioSettings)
    {
        isMicOn = false;
        actualMicSource = audioSettings.micSource;
        currentAudioSettings = audioSettings;

        if (audioSettings.soundEnabled)
            setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);

        if (audioSettings.micSource == 2)
            setupMicInputStream();
    }

    void updateAudioSettings(AudioSettings audioSettings)
    {
        if (audioSettings.soundEnabled && currentAudioSettings.volume > 0) {
            if (!audioStream) {
                setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);
            } else if (currentAudioSettings.audioLatency != audioSettings.audioLatency || currentAudioSettings.volume != audioSettings.volume) {
                // Recreate audio stream with new settings
                cleanupAudioOutputStream();
                setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);
            }
        } else if (audioStream) {
            cleanupAudioOutputStream();
        }

        int oldMicSource = actualMicSource;
        actualMicSource = audioSettings.micSource;

        if (oldMicSource == 2 && audioSettings.micSource != 2) {
            // No longer using device mic. Destroy stream
            cleanupMicInputStream();
        } else if (oldMicSource != 2 && audioSettings.micSource == 2) {
            // Now using device mic. Setup stream
            setupMicInputStream();
        }

        currentAudioSettings = audioSettings;
    }

    void setAudioActiveInstance(std::shared_ptr<MelonInstance> instance)
    {
        activeInstance = instance;
        if (outputCallback)
            outputCallback->activeInstance = activeInstance;
    }

    void cleanupAudio()
    {
        cleanupAudioOutputStream();
        cleanupMicInputStream();
    }

    void startAudio()
    {
        if (audioStream)
            audioStream->requestStart();

        startMicStreamIfAllowed();
    }

    void pauseAudio()
    {
        if (audioStream)
            audioStream->requestPause();

        if (micInputStream)
            micInputStream->requestStop();
    }
}