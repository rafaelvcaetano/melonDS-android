#include "MelonDSAudio.h"
#include "AudioOutputPolicy.h"
#include "MicInputOboeCallback.h"
#include "mic_blow.h"
#include "OboeCallback.h"
#include <atomic>
#include <mutex>
#include <oboe/Oboe.h>

#define MIC_BUFFER_SIZE 2048

std::weak_ptr<MelonDSAndroid::MelonInstance> activeInstance;

// Guards the output stream globals below. They are written from the JNI thread (setup, update,
// cleanup, start, pause) and from the detached thread Oboe spawns for onErrorAfterClose, which
// used to be able to run at the same time and leave a half-built stream behind.
// It is never taken from the real-time data callback.
std::mutex audioOutputMutex;

// Whether the caller wants audio playing right now: set by startAudio(), cleared by pauseAudio()
// and cleanupAudio(). A device disconnect while the app is in the background must not resurrect
// a stream the user has paused.
bool isAudioRunning = false;

std::shared_ptr<oboe::AudioStream> audioStream;
std::shared_ptr<OboeCallback> outputCallback;
std::shared_ptr<oboe::StabilizedCallback> stabilizedOutputCallback;

// JNI/UI toggles and the core's Mic_Start/Mic_Stop can overlap setup, settings,
// pause and teardown. Serialize stream ownership and all start/stop requests.
// Lock order: audioOutputMutex -> micControlMutex -> micBufferMutex. Neither
// Oboe data callback takes micControlMutex; never hold micBufferMutex over close,
// which can wait for an in-flight capture callback that needs the buffer lock.
std::mutex micControlMutex;
std::shared_ptr<oboe::AudioStream> micInputStream; // micControlMutex
bool isMicOn = false; // guest request, micControlMutex
bool isMicRunning = false; // frontend running rather than paused, micControlMutex

MelonDSAndroid::AudioSettings currentAudioSettings;

// Read from the real-time data callback without taking audioOutputMutex, so both must stay
// lock-free (plain atomics), never guarded by a mutex the callback cannot afford to block on.
std::atomic_bool isAudioFastForwardActive = false;
std::atomic_bool muteFastForwardAudio = false;

// readMic runs on the emulation thread. It never waits for stream open/close:
// source and UI enable are independent atomic values, not callback publication.
// The existing buffer lock publishes/removes the callback and protects its ring.
std::atomic<int> actualMicSource = 0;
std::atomic_bool isMicInputEnabled = true;
std::mutex micBufferMutex;
std::shared_ptr<MicInputOboeCallback> micInputCallback; // micBufferMutex
// Only readMic advances the blow recording; capture cursors belong to their ring.
int micBufferReadPos = 0;

namespace MelonDSAndroid
{
    // AUDIO OUTPUT

    void resetAudioOutputStream();

    // Callers of setupAudioOutputStream() and cleanupAudioOutputStream() must hold
    // audioOutputMutex; only the public entry points take it, so a plain mutex is enough.
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

        outputCallback->setActiveInstance(activeInstance);

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
        if (result != oboe::Result::OK) {
            // AudioStreamBuilder::openStream() guarantees audioStream stays null when it
            // does not return OK (see oboe/src/common/AudioStreamBuilder.cpp), so nothing
            // below this branch may dereference it.
            Log(Error, "Failed to init audio stream");
            outputCallback = nullptr;
            stabilizedOutputCallback = nullptr;
        } else {
            audioStream->setPerformanceHintEnabled(true);
            audioStream->setBufferSizeInFrames(std::min(audioStream->getBufferCapacityInFrames(), 2048));
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
        // Runs on the detached thread Oboe spawns for onErrorAfterClose, never on the data
        // callback thread, so closing a stream from under this lock is allowed; and the stream
        // that errored has already been closed by Oboe, so cleanupAudioOutputStream() sees it
        // past StreamState::Closing and does not close it a second time.
        std::lock_guard<std::mutex> lock(audioOutputMutex);

        cleanupAudioOutputStream();
        setupAudioOutputStream(currentAudioSettings.audioLatency, currentAudioSettings.volume);
        // Only if the user is meant to be hearing something: a disconnect that arrives while the
        // emulator is paused must not start playback behind their back. startAudio() reopens the
        // stream itself if this reset could not.
        if (audioStream && isAudioRunning) {
            audioStream->requestStart();
        }
    }

    // MICROPHONE

    // All stream helpers require micControlMutex; public entry points take it.
    void setupMicInputStream()
    {
        auto callback = std::make_shared<MicInputOboeCallback>(MIC_BUFFER_SIZE, micBufferMutex);
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
        micStreamBuilder.setDataCallback(callback);

        oboe::Result micResult = micStreamBuilder.openStream(micInputStream);
        if (micResult != oboe::Result::OK)
        {
            actualMicSource = 1;
            Log(Error, "Failed to init mic audio stream");
            callback = nullptr;
        }

        std::lock_guard<std::mutex> lock(micBufferMutex);
        micInputCallback = std::move(callback);
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
        if (actualMicSource == 2 && micInputStream && isMicInputEnabled && isMicOn && isMicRunning)
            micInputStream->requestStart();
    }

    void userEnableMic()
    {
        std::lock_guard<std::mutex> lock(micControlMutex);
        isMicInputEnabled = true;
        startMicStreamIfAllowed();
    }

    void userDisableMic()
    {
        std::lock_guard<std::mutex> lock(micControlMutex);
        isMicInputEnabled = false;
        if (micInputStream)
            micInputStream->requestStop();
    }

    void enableMic()
    {
        std::lock_guard<std::mutex> lock(micControlMutex);
        isMicOn = true;
        startMicStreamIfAllowed();
    }

    void disableMic()
    {
        std::lock_guard<std::mutex> lock(micControlMutex);
        isMicOn = false;
        if (micInputStream)
            micInputStream->requestStop();
    }

    int readMic(s16* data, int maxlength)
    {
        int micSource = actualMicSource;
        if (!isMicInputEnabled)
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

        // Device capture owns its cursor with its ring. The blow recording has a
        // different length and must not carry its position into a new capture/session.
        int& readPosition = micSource == 2 ? micInputCallback->bufferReadOffset : micBufferReadPos;
        int readlength = 0;
        while (readlength < maxlength)
        {
            int thislen = maxlength - readlength;
            if ((readPosition + thislen) > micBufferLength)
                thislen = micBufferLength - readPosition;

            if (micSource == 2)
            {
                if (thislen > micInputCallback->bufferCount)
                    thislen = micInputCallback->bufferCount;

                micInputCallback->bufferCount -= thislen;
            }

            if (!thislen)
                break;

            memcpy(data, &micBuffer[readPosition], thislen * sizeof(s16));
            data += thislen;
            readPosition += thislen;
            if (readPosition >= micBufferLength)
                readPosition -= micBufferLength;

            readlength += thislen;
        }

        if (micSource == 2)
            micBufferMutex.unlock();

        return readlength;
    }

    // GENERAL

    void setupAudio(AudioSettings audioSettings)
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);
        std::lock_guard<std::mutex> micLock(micControlMutex);

        // A previous session may still own a running stream (setupEmulator after a quick
        // background/foreground cycle). Opening a new one over it destroys the old stream while
        // its callback thread is still inside it: HWASan reported the use-after-free in
        // AudioStreamAAudio::callOnAudioReady. Stop and close it first, like updateAudioSettings().
        cleanupAudioOutputStream();
        cleanupMicInputStream();

        isMicOn = false;
        isMicRunning = false;
        isAudioFastForwardActive = false;
        muteFastForwardAudio = audioSettings.muteFastForwardAudio;
        actualMicSource = audioSettings.micSource;
        currentAudioSettings = audioSettings;

        // Same condition as updateAudioSettings(): opening a stream at volume 0 only to have
        // the next settings update close it again is pointless.
        if (shouldAudioOutputStreamBeActive(audioSettings.soundEnabled, audioSettings.volume))
            setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);

        if (audioSettings.micSource == 2)
            setupMicInputStream();
    }

    void updateAudioSettings(AudioSettings audioSettings)
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);
        std::lock_guard<std::mutex> micLock(micControlMutex);

        muteFastForwardAudio = audioSettings.muteFastForwardAudio;

        if (shouldAudioOutputStreamBeActive(audioSettings.soundEnabled, audioSettings.volume)) {
            if (!audioStream) {
                setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);
            } else if (currentAudioSettings.audioLatency != audioSettings.audioLatency || currentAudioSettings.volume != audioSettings.volume) {
                // Recreate audio stream with new settings
                cleanupAudioOutputStream();
                setupAudioOutputStream(audioSettings.audioLatency, audioSettings.volume);
            }

            // A stream that was just built is stopped; without this, changing the volume or the
            // latency mid-game left the emulator silent until the next pause/resume.
            if (audioStream && isAudioRunning)
                audioStream->requestStart();
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

    void setAudioFastForwardActive(bool active)
    {
        isAudioFastForwardActive = active;
    }

    bool shouldMuteAudioOutput()
    {
        return isAudioFastForwardActive.load() && muteFastForwardAudio.load();
    }

    void setAudioActiveInstance(std::shared_ptr<MelonInstance> instance)
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);

        activeInstance = instance;
        if (outputCallback)
            outputCallback->setActiveInstance(activeInstance);
    }

    void cleanupAudio()
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);
        std::lock_guard<std::mutex> micLock(micControlMutex);

        isAudioRunning = false;
        isMicRunning = false;
        isMicOn = false;
        isAudioFastForwardActive = false;
        muteFastForwardAudio = false;
        // Forget the settings too: they are what startAudio() consults to decide whether to
        // reopen a stream, and after cleanup there is no emulator left to feed one.
        currentAudioSettings = { };
        cleanupAudioOutputStream();
        cleanupMicInputStream();
    }

    void startAudio()
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);
        std::lock_guard<std::mutex> micLock(micControlMutex);

        isAudioRunning = true;
        isMicRunning = true;

        // Reopen the stream if it is gone. A device disconnect (headphones, Bluetooth) closes
        // the stream and the reopen in resetAudioOutputStream() can fail while the app is in the
        // background, leaving audioStream null; without this the emulator stayed silent for the
        // rest of the session after coming back to the foreground (upstream #1635, #1644).
        if (!audioStream && shouldAudioOutputStreamBeActive(currentAudioSettings.soundEnabled, currentAudioSettings.volume))
            setupAudioOutputStream(currentAudioSettings.audioLatency, currentAudioSettings.volume);

        if (audioStream)
            audioStream->requestStart();

        startMicStreamIfAllowed();
    }

    void pauseAudio()
    {
        std::lock_guard<std::mutex> lock(audioOutputMutex);
        std::lock_guard<std::mutex> micLock(micControlMutex);

        isAudioRunning = false;
        isMicRunning = false;

        if (audioStream)
            audioStream->requestPause();

        if (micInputStream)
            micInputStream->requestStop();
    }
}
