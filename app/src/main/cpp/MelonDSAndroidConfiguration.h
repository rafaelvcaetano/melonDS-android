#ifndef MELONDSANDROIDCONFIGURATION_H
#define MELONDSANDROIDCONFIGURATION_H

#include "Configuration.h"
#include "MelonDS.h"

namespace MelonDSAndroidConfiguration {
    MelonDSAndroid::EmulatorConfiguration buildEmulatorConfiguration(JNIEnv* env, jobject emulatorConfiguration);
    MelonDSAndroid::FirmwareConfiguration buildFirmwareConfiguration(JNIEnv* env, jobject firmwareConfiguration);
    std::unique_ptr<MelonDSAndroid::RenderSettings> buildRenderSettings(JNIEnv* env, MelonDSAndroid::Renderer renderer, jobject renderSettings);
}

#endif //MELONDSANDROIDCONFIGURATION_H
