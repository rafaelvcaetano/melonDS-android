#ifndef MELONDSANDROIDCONFIGURATION_H
#define MELONDSANDROIDCONFIGURATION_H

#include "MelonDS.h"

namespace MelonDSAndroidConfiguration {
    MelonDSAndroid::EmulatorConfiguration buildEmulatorConfiguration(JNIEnv* env, jobject emulatorConfiguration);
    MelonDSAndroid::FirmwareConfiguration buildFirmwareConfiguration(JNIEnv* env, jobject firmwareConfiguration);
    GPU::RenderSettings buildRenderSettings(JNIEnv* env, jobject renderSettings);
}

#endif //MELONDSANDROIDCONFIGURATION_H
