#ifndef CONFIGURATION_H
#define CONFIGURATION_H

#include <memory>
#include <string>
#include "renderer/Renderer.h"

namespace MelonDSAndroid
{

struct RenderSettings
{
};

struct SoftwareRenderSettings : public RenderSettings
{
    bool threadedRendering;
};

struct OpenGlRenderSettings : public RenderSettings
{
    bool betterPolygons;
    int scale;
};

struct ComputeRenderSettings : public RenderSettings
{
    int scale;
    bool highResCoordinates;
};

struct AudioSettings
{
    bool soundEnabled;
    bool muteFastForwardAudio;
    int volume;
    int audioInterpolation;
    int audioBitrate;
    int audioLatency;
    int micSource;
};

struct SdCardSettings
{
    bool enabled;
    char* imagePath;
    int imageSize;
    bool readOnly;
    bool folderSync;
    char* folderPath;
};

typedef struct
{
    std::u16string username;
    int language;
    int birthdayMonth;
    int birthdayDay;
    int favouriteColour;
    std::u16string message;
    bool randomizeMacAddress;
    std::string macAddress;
} FirmwareConfiguration;

typedef struct
{
    bool userInternalFirmwareAndBios;
    std::string dsBios7Path;
    std::string dsBios9Path;
    std::string dsFirmwarePath;
    std::string dsiBios7Path;
    std::string dsiBios9Path;
    std::string dsiFirmwarePath;
    std::string dsiNandPath;
    std::string internalFilesDir;
    float fastForwardSpeedMultiplier;
    bool showBootScreen;
    bool useJit;
    int consoleType;
    // Off by default (opt-in): when true, the DS RTC is periodically stamped forward to
    // match the host device's clock. See MelonInstance::syncRTC() / RtcSync.h for the
    // decision logic and why it never steps the clock backward.
    bool rtcSyncToHost;
    AudioSettings audioSettings;
    int rewindEnabled;
    int rewindCaptureSpacingSeconds;
    int rewindLengthSeconds;
    FirmwareConfiguration firmwareConfiguration;
    std::unique_ptr<RenderSettings> renderSettings;
    SdCardSettings dsiSdCardSettings;
    SdCardSettings dldiSdCardSettings;
    Renderer renderer;
} EmulatorConfiguration;

}

#endif
