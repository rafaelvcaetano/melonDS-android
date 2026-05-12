#ifndef CONFIGURATION_H
#define CONFIGURATION_H

#include <memory>
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
    char username[11];
    int language;
    int birthdayMonth;
    int birthdayDay;
    int favouriteColour;
    char message[27];
    bool randomizeMacAddress;
    char macAddress[18];
} FirmwareConfiguration;

typedef struct
{
    bool userInternalFirmwareAndBios;
    char* dsBios7Path;
    char* dsBios9Path;
    char* dsFirmwarePath;
    char* dsiBios7Path;
    char* dsiBios9Path;
    char* dsiFirmwarePath;
    char* dsiNandPath;
    char* internalFilesDir;
    float fastForwardSpeedMultiplier;
    bool showBootScreen;
    bool useJit;
    int consoleType;
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
