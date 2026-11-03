#include <jni.h>
#include <algorithm>
#include "MelonDSAndroidConfiguration.h"
#include "renderer/Renderer.h"

MelonDSAndroid::EmulatorConfiguration MelonDSAndroidConfiguration::buildEmulatorConfiguration(JNIEnv* env, jobject emulatorConfiguration) {
    env->PushLocalFrame(64);
    jclass emulatorConfigurationClass = env->GetObjectClass(emulatorConfiguration);
    jclass uriClass = env->FindClass("android/net/Uri");
    jclass consoleTypeEnumClass = env->FindClass("me/magnum/melonds/domain/model/ConsoleType");
    jclass audioBitrateEnumClass = env->FindClass("me/magnum/melonds/domain/model/AudioBitrate");
    jclass audioInterpolationEnumClass = env->FindClass("me/magnum/melonds/domain/model/AudioInterpolation");
    jclass audioLatencyEnumClass = env->FindClass("me/magnum/melonds/domain/model/AudioLatency");
    jclass micSourceEnumClass = env->FindClass("me/magnum/melonds/domain/model/MicSource");
    jclass videoRendererEnumClass = env->FindClass("me/magnum/melonds/domain/model/VideoRenderer");
    jclass renderConfigurationClass = env->FindClass("me/magnum/melonds/domain/model/RendererConfiguration");

    jmethodID uriToStringMethod = env->GetMethodID(uriClass, "toString", "()Ljava/lang/String;");

    jobject firmwareConfigurationObject = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "firmwareConfiguration", "Lme/magnum/melonds/domain/model/FirmwareConfiguration;"));
    jobject rendererConfigurationObject = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rendererConfiguration", "Lme/magnum/melonds/domain/model/RendererConfiguration;"));
    jboolean useCustomBios = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "useCustomBios", "Z"));
    jboolean showBootScreen = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "showBootScreen", "Z"));
    jobject dsBios7Uri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsBios7Uri", "Landroid/net/Uri;"));
    jobject dsBios9Uri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsBios9Uri", "Landroid/net/Uri;"));
    jobject dsFirmwareUri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsFirmwareUri", "Landroid/net/Uri;"));
    jobject dsiBios7Uri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsiBios7Uri", "Landroid/net/Uri;"));
    jobject dsiBios9Uri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsiBios9Uri", "Landroid/net/Uri;"));
    jobject dsiFirmwareUri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsiFirmwareUri", "Landroid/net/Uri;"));
    jobject dsiNandUri = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsiNandUri", "Landroid/net/Uri;"));
    jstring internalFilesDir = (jstring) env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "internalDirectory", "Ljava/lang/String;"));
    jfloat fastForwardMaxSpeed = env->GetFloatField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "fastForwardSpeedMultiplier", "F"));
    jboolean muteFastForwardAudio = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "muteFastForwardAudio", "Z"));
    jboolean enableRewind = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rewindEnabled", "Z"));
    jint rewindPeriodSeconds = env->GetIntField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rewindPeriodSeconds", "I"));
    jint rewindWindowSeconds = env->GetIntField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rewindWindowSeconds", "I"));
    jboolean useJit = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "useJit", "Z"));
    jboolean rtcSyncToHost = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rtcSyncToHost", "Z"));
    jobject consoleTypeEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "consoleType", "Lme/magnum/melonds/domain/model/ConsoleType;"));
    jint consoleType = env->GetIntField(consoleTypeEnum, env->GetFieldID(consoleTypeEnumClass, "consoleType", "I"));
    jboolean soundEnabled = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "soundEnabled", "Z"));
    jint volume = env->GetIntField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "volume", "I"));
    jobject audioInterpolationEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "audioInterpolation", "Lme/magnum/melonds/domain/model/AudioInterpolation;"));
    jint audioInterpolation = env->GetIntField(audioInterpolationEnum, env->GetFieldID(audioInterpolationEnumClass, "interpolationValue", "I"));
    jobject audioBitrateEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "audioBitrate", "Lme/magnum/melonds/domain/model/AudioBitrate;"));
    jint audioBitrate = env->GetIntField(audioBitrateEnum, env->GetFieldID(audioBitrateEnumClass, "bitrateValue", "I"));
    jobject audioLatencyEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "audioLatency", "Lme/magnum/melonds/domain/model/AudioLatency;"));
    jint audioLatency = env->GetIntField(audioLatencyEnum, env->GetFieldID(audioLatencyEnumClass, "latencyValue", "I"));
    jobject micSourceEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "micSource", "Lme/magnum/melonds/domain/model/MicSource;"));
    jint micSource = env->GetIntField(micSourceEnum, env->GetFieldID(micSourceEnumClass, "sourceValue", "I"));
    jobject videoRendererEnum = env->GetObjectField(rendererConfigurationObject, env->GetFieldID(renderConfigurationClass, "renderer", "Lme/magnum/melonds/domain/model/VideoRenderer;"));
    MelonDSAndroid::Renderer videoRenderer = static_cast<MelonDSAndroid::Renderer>(env->GetIntField(videoRendererEnum, env->GetFieldID(videoRendererEnumClass, "renderer", "I")));
    jboolean isCopy = JNI_FALSE;
    jstring dsBios7String = dsBios7Uri ? (jstring) env->CallObjectMethod(dsBios7Uri, uriToStringMethod) : nullptr;
    jstring dsBios9String = dsBios9Uri ? (jstring) env->CallObjectMethod(dsBios9Uri, uriToStringMethod) : nullptr;
    jstring dsFirmwareString = dsFirmwareUri ? (jstring) env->CallObjectMethod(dsFirmwareUri, uriToStringMethod) : nullptr;
    jstring dsiBios7String = dsiBios7Uri ? (jstring) env->CallObjectMethod(dsiBios7Uri, uriToStringMethod) : nullptr;
    jstring dsiBios9String = dsiBios9Uri ? (jstring) env->CallObjectMethod(dsiBios9Uri, uriToStringMethod) : nullptr;
    jstring dsiFirmwareString = dsiFirmwareUri ? (jstring) env->CallObjectMethod(dsiFirmwareUri, uriToStringMethod) : nullptr;
    jstring dsiNandString = dsiNandUri ? (jstring) env->CallObjectMethod(dsiNandUri, uriToStringMethod) : nullptr;
    const char* dsBios7Path = dsBios7Uri ? env->GetStringUTFChars(dsBios7String, &isCopy) : nullptr;
    const char* dsBios9Path = dsBios9Uri ? env->GetStringUTFChars(dsBios9String, &isCopy) : nullptr;
    const char* dsFirmwarePath = dsFirmwareUri ? env->GetStringUTFChars(dsFirmwareString, &isCopy) : nullptr;
    const char* dsiBios7Path = dsiBios7Uri ? env->GetStringUTFChars(dsiBios7String, &isCopy) : nullptr;
    const char* dsiBios9Path = dsiBios9Uri ? env->GetStringUTFChars(dsiBios9String, &isCopy) : nullptr;
    const char* dsiFirmwarePath = dsiFirmwareUri ? env->GetStringUTFChars(dsiFirmwareString, &isCopy) : nullptr;
    const char* dsiNandPath = dsiNandUri ? env->GetStringUTFChars(dsiNandString, &isCopy) : nullptr;
    const char* internalDir = env->GetStringUTFChars(internalFilesDir, nullptr);

    MelonDSAndroid::EmulatorConfiguration finalEmulatorConfiguration;
    finalEmulatorConfiguration.userInternalFirmwareAndBios = !useCustomBios;
    finalEmulatorConfiguration.dsBios7Path = dsBios7Path ? dsBios7Path : "";
    finalEmulatorConfiguration.dsBios9Path = dsBios9Path ? dsBios9Path : "";
    finalEmulatorConfiguration.dsFirmwarePath = dsFirmwarePath ? dsFirmwarePath : "";
    finalEmulatorConfiguration.dsiBios7Path = dsiBios7Path ? dsiBios7Path : "";
    finalEmulatorConfiguration.dsiBios9Path = dsiBios9Path ? dsiBios9Path : "";
    finalEmulatorConfiguration.dsiFirmwarePath = dsiFirmwarePath ? dsiFirmwarePath : "";
    finalEmulatorConfiguration.dsiNandPath = dsiNandPath ? dsiNandPath : "";
    finalEmulatorConfiguration.internalFilesDir = internalDir ? internalDir : "";
    finalEmulatorConfiguration.fastForwardSpeedMultiplier = fastForwardMaxSpeed;
    finalEmulatorConfiguration.showBootScreen = showBootScreen;
    finalEmulatorConfiguration.useJit = useJit;
    finalEmulatorConfiguration.consoleType = consoleType;
    finalEmulatorConfiguration.rtcSyncToHost = rtcSyncToHost;
    finalEmulatorConfiguration.audioSettings = MelonDSAndroid::AudioSettings {
        .soundEnabled = (bool) soundEnabled,
        .muteFastForwardAudio = (bool) muteFastForwardAudio,
        .volume = volume,
        .audioInterpolation = audioInterpolation,
        .audioBitrate = audioBitrate,
        .audioLatency = audioLatency,
        .micSource = micSource
    };
    finalEmulatorConfiguration.firmwareConfiguration = buildFirmwareConfiguration(env, firmwareConfigurationObject);
    finalEmulatorConfiguration.rewindEnabled = enableRewind ? 1 : 0;
    finalEmulatorConfiguration.rewindCaptureSpacingSeconds = rewindPeriodSeconds;
    finalEmulatorConfiguration.rewindLengthSeconds = rewindWindowSeconds;
    finalEmulatorConfiguration.renderSettings = std::move(buildRenderSettings(env, videoRenderer, rendererConfigurationObject));
    finalEmulatorConfiguration.dsiSdCardSettings = MelonDSAndroid::SdCardSettings { .enabled = false };
    finalEmulatorConfiguration.dldiSdCardSettings = MelonDSAndroid::SdCardSettings { .enabled = false };
    finalEmulatorConfiguration.renderer = videoRenderer;
    if (dsBios7Path) env->ReleaseStringUTFChars(dsBios7String, dsBios7Path);
    if (dsBios9Path) env->ReleaseStringUTFChars(dsBios9String, dsBios9Path);
    if (dsFirmwarePath) env->ReleaseStringUTFChars(dsFirmwareString, dsFirmwarePath);
    if (dsiBios7Path) env->ReleaseStringUTFChars(dsiBios7String, dsiBios7Path);
    if (dsiBios9Path) env->ReleaseStringUTFChars(dsiBios9String, dsiBios9Path);
    if (dsiFirmwarePath) env->ReleaseStringUTFChars(dsiFirmwareString, dsiFirmwarePath);
    if (dsiNandPath) env->ReleaseStringUTFChars(dsiNandString, dsiNandPath);
    if (internalDir) env->ReleaseStringUTFChars(internalFilesDir, internalDir);
    env->PopLocalFrame(nullptr);
    return finalEmulatorConfiguration;
}

MelonDSAndroid::FirmwareConfiguration MelonDSAndroidConfiguration::buildFirmwareConfiguration(JNIEnv* env, jobject firmwareConfiguration) {
    jclass firmwareConfigurationClass = env->GetObjectClass(firmwareConfiguration);
    jstring nicknameString = (jstring) env->GetObjectField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "nickname", "Ljava/lang/String;"));
    jstring messageString = (jstring) env->GetObjectField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "message", "Ljava/lang/String;"));
    int language = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "language", "I"));
    int colour = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "favouriteColour", "I"));
    int birthdayDay = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "birthdayDay", "I"));
    int birthdayMonth = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "birthdayMonth", "I"));
    bool randomizeMacAddress = env->GetBooleanField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "randomizeMacAddress", "Z"));
    jstring macAddressString = (jstring) env->GetObjectField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "internalMacAddress", "Ljava/lang/String;"));

    const jchar* nickname = nicknameString ? env->GetStringChars(nicknameString, nullptr) : nullptr;
    const jchar* message = messageString ? env->GetStringChars(messageString, nullptr) : nullptr;
    const char* macAddress = macAddressString ? env->GetStringUTFChars(macAddressString, nullptr) : nullptr;

    MelonDSAndroid::FirmwareConfiguration finalFirmwareConfiguration;
    if (nickname)
    {
        const jsize length = std::min<jsize>(env->GetStringLength(nicknameString), 10);
        finalFirmwareConfiguration.username.assign(
            reinterpret_cast<const char16_t*>(nickname), static_cast<size_t>(length));
        if (!finalFirmwareConfiguration.username.empty() &&
            finalFirmwareConfiguration.username.back() >= 0xD800 &&
            finalFirmwareConfiguration.username.back() <= 0xDBFF)
            finalFirmwareConfiguration.username.pop_back();
    }
    if (message)
    {
        const jsize length = std::min<jsize>(env->GetStringLength(messageString), 26);
        finalFirmwareConfiguration.message.assign(
            reinterpret_cast<const char16_t*>(message), static_cast<size_t>(length));
        if (!finalFirmwareConfiguration.message.empty() &&
            finalFirmwareConfiguration.message.back() >= 0xD800 &&
            finalFirmwareConfiguration.message.back() <= 0xDBFF)
            finalFirmwareConfiguration.message.pop_back();
    }
    finalFirmwareConfiguration.language = language;
    finalFirmwareConfiguration.favouriteColour = colour;
    finalFirmwareConfiguration.birthdayDay = birthdayDay;
    finalFirmwareConfiguration.birthdayMonth = birthdayMonth;
    finalFirmwareConfiguration.randomizeMacAddress = randomizeMacAddress;
    if (macAddress != nullptr)
    {
        finalFirmwareConfiguration.macAddress = macAddress;
    }
    else
    {
        finalFirmwareConfiguration.macAddress.clear();
    }

    if (nickname) env->ReleaseStringChars(nicknameString, nickname);
    if (message) env->ReleaseStringChars(messageString, message);
    if (macAddress) env->ReleaseStringUTFChars(macAddressString, macAddress);

    return finalFirmwareConfiguration;
}

std::unique_ptr<MelonDSAndroid::RenderSettings> MelonDSAndroidConfiguration::buildRenderSettings(JNIEnv* env, MelonDSAndroid::Renderer renderer, jobject renderSettings) {
    jclass renderSettingsClass = env->GetObjectClass(renderSettings);
    jmethodID getResolutionScalingMethod = env->GetMethodID(renderSettingsClass, "getResolutionScaling", "()I");
    jboolean threadedRendering = env->GetBooleanField(renderSettings, env->GetFieldID(renderSettingsClass, "threadedRendering", "Z"));
    jint internalResolutionScaling = env->CallIntMethod(renderSettings, getResolutionScalingMethod);

    std::unique_ptr<MelonDSAndroid::RenderSettings> settings;
    if (renderer == MelonDSAndroid::Renderer::OpenGl)
    {
        settings = std::make_unique<MelonDSAndroid::OpenGlRenderSettings>(
            MelonDSAndroid::OpenGlRenderSettings {
                .betterPolygons = false,
                .scale = internalResolutionScaling,
            }
        );
    }
    else if (renderer == MelonDSAndroid::Renderer::Compute)
    {
        settings = std::make_unique<MelonDSAndroid::ComputeRenderSettings>(
            MelonDSAndroid::ComputeRenderSettings {
                .scale = internalResolutionScaling,
                .highResCoordinates = true,
            }
        );
    }
    else
    {
        settings = std::make_unique<MelonDSAndroid::SoftwareRenderSettings>(
            MelonDSAndroid::SoftwareRenderSettings {
                .threadedRendering = (bool) threadedRendering
            }
        );
    }

    return settings;
}
