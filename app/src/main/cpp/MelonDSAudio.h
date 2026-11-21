#ifndef MELONDSAUDIO_H
#define MELONDSAUDIO_H

#include "Configuration.h"
#include "MelonInstance.h"
#include "types.h"
#include <cstdint>
#include <vector>

namespace MelonDSAndroid
{
    extern void userEnableMic();
    extern void userDisableMic();
    extern void enableMic();
    extern void disableMic();
    extern void setAutoMicMode(int mode);
    extern void startAudioCapture(int maxSeconds);
    extern void captureAudio(const int16_t* stereoSamples, int frames);
    extern std::vector<int16_t> stopAudioCapture();
    extern int readMic(melonDS::s16* data, int maxlength);

    extern void setupAudio(AudioSettings audioSettings);
    extern void updateAudioSettings(AudioSettings audioSettings);
    extern void setAudioActiveInstance(std::shared_ptr<MelonInstance> instance);
    extern void cleanupAudio();
    extern void startAudio();
    extern void pauseAudio();
}

#endif //MELONDSAUDIO_H
