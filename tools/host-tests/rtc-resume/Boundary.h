#pragma once
#include <NDS.h>
#include <ctime>
#include "RtcSync.h"

namespace MelonDSAndroid {
namespace Platform = melonDS::Platform;
// Only instance ownership/configuration are adapted. NDS and RTC are real core objects.
struct ConfigurationBoundary { bool rtcSyncToHost; };
class MelonInstance {
public:
    melonDS::NDS* nds;
    ConfigurationBoundary* currentConfiguration;
    void setDateTime();
    void syncRTC();
    void onResumed();
};
}
