#pragma once
// Frontend/core boundary only: the real lifecycle implementation is compiled.
#include <memory>
#include <cstring>
#include "types.h"
using namespace melonDS;
enum LogLevel { Error };
inline void Log(LogLevel, const char*) {}
namespace MelonDSAndroid {
class MelonInstance {};
struct AudioSettings {
    bool soundEnabled;
    bool muteFastForwardAudio;
    int volume;
    int audioInterpolation;
    int audioBitrate;
    int audioLatency;
    int micSource;
};
void setupAudio(AudioSettings);
void updateAudioSettings(AudioSettings);
void cleanupAudio();
void userEnableMic();
void userDisableMic();
void enableMic();
void disableMic();
void startAudio();
void pauseAudio();
int readMic(s16*, int);
}
