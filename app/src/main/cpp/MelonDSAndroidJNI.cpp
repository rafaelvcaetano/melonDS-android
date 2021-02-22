#include <jni.h>
#include <string>
#include <sstream>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdlib>
#include <MelonDS.h>
#include <InputAndroid.h>
#include <android/asset_manager_jni.h>

#define MAX_CHEAT_SIZE (2*64)

MelonDSAndroid::EmulatorConfiguration buildEmulatorConfiguration(JNIEnv* env, jobject emulatorConfiguration);
MelonDSAndroid::FirmwareConfiguration buildFirmwareConfiguration(JNIEnv* env, jobject firmwareConfiguration);
GPU::RenderSettings buildRenderSettings(JNIEnv* env, jobject renderSettings);
void* emulate(void*);

pthread_t emuThread;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;

bool stop;
bool paused;
std::atomic_bool isThreadReallyPaused = false;
int observedFrames = 0;
int fps = 0;
int targetFps;
float fastForwardSpeedMultiplier;
bool limitFps = true;
bool isFastForwardEnabled = false;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupEmulator(JNIEnv* env, jobject thiz, jobject emulatorConfiguration, jobject javaAssetManager)
{
    MelonDSAndroid::EmulatorConfiguration finalEmulatorConfiguration = buildEmulatorConfiguration(env, emulatorConfiguration);
    fastForwardSpeedMultiplier = finalEmulatorConfiguration.fastForwardSpeedMultiplier;
    jobject globalAssetManager = env->NewGlobalRef(javaAssetManager);
    AAssetManager* assetManager = AAssetManager_fromJava(env, globalAssetManager);

    MelonDSAndroid::setup(finalEmulatorConfiguration, assetManager);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupCheats(JNIEnv* env, jobject thiz, jobjectArray cheats)
{
    jsize cheatCount = env->GetArrayLength(cheats);
    if (cheatCount < 1) {
        return;
    }

    jclass cheatClass = env->GetObjectClass(env->GetObjectArrayElement(cheats, 0));
    jfieldID codeField = env->GetFieldID(cheatClass, "code", "Ljava/lang/String;");

    std::list<MelonDSAndroid::Cheat> internalCheats;

    for (int i = 0; i < cheatCount; ++i) {
        jobject cheat = env->GetObjectArrayElement(cheats, i);
        jstring code = (jstring) env->GetObjectField(cheat, codeField);
        std::string codeString = env->GetStringUTFChars(code, JNI_FALSE);

        bool isBad = false;
        int sectionCounter = 0;
        std::size_t start = 0;
        std::size_t end = 0;

        MelonDSAndroid::Cheat internalCheat;

        // Split code string into sections separated by a space
        while ((end = codeString.find(' ', start)) != std::string::npos) {
            if (end != start) {
                char* endPointer;
                std::string sectionString = codeString.substr(start, end - start);
                // Each code section must be 4 bytes (8 hex characters)
                if (sectionString.size() != 8) {
                    isBad = true;
                    break;
                }

                unsigned long section = strtoul(sectionString.c_str(), &endPointer, 16);
                if (*endPointer == 0) {
                    if (sectionCounter >= MAX_CHEAT_SIZE) {
                        isBad = true;
                        break;
                    }

                    internalCheat.code[sectionCounter] = (u32) section;
                    sectionCounter++;
                } else {
                    isBad = true;
                    break;
                }
            }
            start = end + 1;
        }

        if (!isBad && end != start) {
            char* endPointer;
            std::string sectionString = codeString.substr(start, end - start);
            if (sectionString.size() != 8) {
                isBad = true;
            } else {
                unsigned long section = strtoul(sectionString.c_str(), &endPointer, 16);
                if (*endPointer == 0 && sectionCounter < MAX_CHEAT_SIZE) {
                    internalCheat.code[sectionCounter] = (u32) section;
                    sectionCounter++;
                } else {
                    isBad = true;
                }
            }
        }

        if (isBad) {
            continue;
        }

        internalCheat.codeLength = sectionCounter;
        internalCheats.push_back(internalCheat);
    }

    MelonDSAndroid::setCodeList(internalCheats);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRomInternal(JNIEnv* env, jobject thiz, jstring romPath, jstring sramPath, jboolean loadDirect, jboolean loadGbaRom, jstring gbaRomPath, jstring gbaSramPath)
{
    jboolean isCopy = JNI_FALSE;
    const char* rom = romPath == nullptr ? nullptr : env->GetStringUTFChars(romPath, &isCopy);
    const char* sram = sramPath == nullptr ? nullptr : env->GetStringUTFChars(sramPath, &isCopy);
    const char* gbaRom = gbaRomPath == nullptr ? nullptr : env->GetStringUTFChars(gbaRomPath, &isCopy);
    const char* gbaSram = gbaSramPath == nullptr ? nullptr : env->GetStringUTFChars(gbaSramPath, &isCopy);

    int result = MelonDSAndroid::loadRom(const_cast<char*>(rom), const_cast<char*>(sram), loadDirect, loadGbaRom, const_cast<char*>(gbaRom), const_cast<char*>(gbaSram));

    if (isCopy == JNI_TRUE) {
        if (romPath) env->ReleaseStringUTFChars(romPath, rom);
        if (sramPath) env->ReleaseStringUTFChars(sramPath, sram);
        if (gbaRomPath) env->ReleaseStringUTFChars(gbaRomPath, gbaRom);
        if (gbaSramPath) env->ReleaseStringUTFChars(gbaSramPath, gbaSram);
    }

    return result;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_bootFirmwareInternal(JNIEnv* env, jobject thiz) {
    return MelonDSAndroid::bootFirmware();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_startEmulation(JNIEnv* env, jobject thiz)
{
    stop = false;
    paused = false;
    isThreadReallyPaused = false;
    limitFps = true;
    targetFps = 60;
    isFastForwardEnabled = false;

    pthread_mutex_init(&emuThreadMutex, NULL);
    pthread_cond_init(&emuThreadCond, NULL);
    pthread_create(&emuThread, NULL, emulate, NULL);
    pthread_setname_np(emuThread, "EmulatorThread");
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_copyFrameBuffer(JNIEnv* env, jobject thiz, jobject frameBuffer)
{
    void* buf = env->GetDirectBufferAddress(frameBuffer);
    MelonDSAndroid::copyFrameBuffer(buf);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_getFPS(JNIEnv* env, jobject thiz)
{
    return fps;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_pauseEmulation(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&emuThreadMutex);
    if (!stop)
        paused = true;
    pthread_mutex_unlock(&emuThreadMutex);

    MelonDSAndroid::pause();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resumeEmulation(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&emuThreadMutex);
    if (!stop) {
        paused = false;
        pthread_cond_broadcast(&emuThreadCond);
    }
    pthread_mutex_unlock(&emuThreadMutex);

    MelonDSAndroid::resume();
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_resetEmulation(JNIEnv* env, jobject thiz) {
    bool result = true;

    pthread_mutex_lock(&emuThreadMutex);
    if (!stop) {
        if (paused) {
            pthread_mutex_unlock(&emuThreadMutex);
        } else {
            pthread_mutex_unlock(&emuThreadMutex);
            Java_me_magnum_melonds_MelonEmulator_pauseEmulation(env, thiz);
        }

        // Make sure that the thread is really paused to avoid data corruption
        while (!isThreadReallyPaused);
        result = MelonDSAndroid::reset();
        Java_me_magnum_melonds_MelonEmulator_resumeEmulation(env, thiz);
    } else {
        // If the emulation is stopping, just ignore it
        pthread_mutex_unlock(&emuThreadMutex);
    }

    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_saveState(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::saveState(saveStatePath);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadState(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::loadState(saveStatePath);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_stopEmulation(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&emuThreadMutex);
    stop = true;
    paused = false;
    pthread_cond_broadcast(&emuThreadCond);
    pthread_mutex_unlock(&emuThreadMutex);

    pthread_join(emuThread, NULL);
    pthread_mutex_destroy(&emuThreadMutex);
    pthread_cond_destroy(&emuThreadCond);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onScreenTouch(JNIEnv* env, jobject thiz, jint x, jint y)
{
    MelonDSAndroid::touchScreen(x, y);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onScreenRelease(JNIEnv* env, jobject thiz)
{
    MelonDSAndroid::releaseScreen();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onKeyPress(JNIEnv* env, jobject thiz, jint key)
{
    MelonDSAndroid::pressKey(key);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onKeyRelease(JNIEnv* env, jobject thiz, jint key)
{
    MelonDSAndroid::releaseKey(key);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setFastForwardEnabled(JNIEnv* env, jobject thiz, jboolean enabled)
{
    isFastForwardEnabled = enabled;
    if (enabled) {
        limitFps = fastForwardSpeedMultiplier > 0;
        targetFps = 60 * fastForwardSpeedMultiplier;
    } else {
        limitFps = true;
        targetFps = 60;
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_updateEmulatorConfiguration(JNIEnv* env, jobject thiz, jobject emulatorConfiguration)
{
    MelonDSAndroid::EmulatorConfiguration newConfiguration = buildEmulatorConfiguration(env, emulatorConfiguration);
    MelonDSAndroid::updateEmulatorConfiguration(newConfiguration);
    fastForwardSpeedMultiplier = newConfiguration.fastForwardSpeedMultiplier;

    if (isFastForwardEnabled) {
        limitFps = fastForwardSpeedMultiplier > 0;
        targetFps = 60 * fastForwardSpeedMultiplier;
    }
}
}

MelonDSAndroid::EmulatorConfiguration buildEmulatorConfiguration(JNIEnv* env, jobject emulatorConfiguration) {
    jclass emulatorConfigurationClass = env->GetObjectClass(emulatorConfiguration);
    jclass consoleTypeEnumClass = env->FindClass("me/magnum/melonds/domain/model/ConsoleType");
    jclass micSourceEnumClass = env->FindClass("me/magnum/melonds/domain/model/MicSource");

    jboolean useCustomBios = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "useCustomBios", "Z"));
    jstring dsConfigDir = (jstring) env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsConfigDirectory", "Ljava/lang/String;"));
    jstring dsiConfigDir = (jstring) env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "dsiConfigDirectory", "Ljava/lang/String;"));
    jfloat fastForwardMaxSpeed = env->GetFloatField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "fastForwardSpeedMultiplier", "F"));
    jboolean useJit = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "useJit", "Z"));
    jobject consoleTypeEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "consoleType", "Lme/magnum/melonds/domain/model/ConsoleType;"));
    jint consoleType = env->GetIntField(consoleTypeEnum, env->GetFieldID(consoleTypeEnumClass, "consoleType", "I"));
    jboolean soundEnabled = env->GetBooleanField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "soundEnabled", "Z"));
    jobject micSourceEnum = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "micSource", "Lme/magnum/melonds/domain/model/MicSource;"));
    jint micSource = env->GetIntField(micSourceEnum, env->GetFieldID(micSourceEnumClass, "sourceValue", "I"));
    const char* dsDir = env->GetStringUTFChars(dsConfigDir, JNI_FALSE);
    const char* dsiDir = env->GetStringUTFChars(dsiConfigDir, JNI_FALSE);
    jobject firmwareConfigurationObject = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "firmwareConfiguration", "Lme/magnum/melonds/domain/model/FirmwareConfiguration;"));
    jobject rendererConfigurationObject = env->GetObjectField(emulatorConfiguration, env->GetFieldID(emulatorConfigurationClass, "rendererConfiguration", "Lme/magnum/melonds/domain/model/RendererConfiguration;"));

    MelonDSAndroid::EmulatorConfiguration finalEmulatorConfiguration;
    finalEmulatorConfiguration.userInternalFirmwareAndBios = !useCustomBios;
    finalEmulatorConfiguration.dsConfigDir = const_cast<char*>(dsDir);
    finalEmulatorConfiguration.dsiConfigDir = const_cast<char*>(dsiDir);
    finalEmulatorConfiguration.fastForwardSpeedMultiplier = fastForwardMaxSpeed;
    finalEmulatorConfiguration.useJit = useJit;
    finalEmulatorConfiguration.consoleType = consoleType;
    finalEmulatorConfiguration.soundEnabled = soundEnabled;
    finalEmulatorConfiguration.micSource = micSource;
    finalEmulatorConfiguration.firmwareConfiguration = buildFirmwareConfiguration(env, firmwareConfigurationObject);
    finalEmulatorConfiguration.renderSettings = buildRenderSettings(env, rendererConfigurationObject);
    return finalEmulatorConfiguration;
}

MelonDSAndroid::FirmwareConfiguration buildFirmwareConfiguration(JNIEnv* env, jobject firmwareConfiguration) {
    jclass firmwareConfigurationClass = env->GetObjectClass(firmwareConfiguration);
    jstring nicknameString = (jstring) env->GetObjectField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "nickname", "Ljava/lang/String;"));
    jstring messageString = (jstring) env->GetObjectField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "message", "Ljava/lang/String;"));
    int language = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "language", "I"));
    int colour = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "favouriteColour", "I"));
    int birthdayDay = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "birthdayDay", "I"));
    int birthdayMonth = env->GetIntField(firmwareConfiguration, env->GetFieldID(firmwareConfigurationClass, "birthdayMonth", "I"));

    jboolean isCopy = JNI_FALSE;
    const char* nickname = env->GetStringUTFChars(nicknameString, &isCopy);
    const char* message = env->GetStringUTFChars(messageString, &isCopy);

    MelonDSAndroid::FirmwareConfiguration finalFirmwareConfiguration;
    strcpy(finalFirmwareConfiguration.username, nickname);
    strcpy(finalFirmwareConfiguration.message, message);
    finalFirmwareConfiguration.language = language;
    finalFirmwareConfiguration.favouriteColour = colour;
    finalFirmwareConfiguration.birthdayDay = birthdayDay;
    finalFirmwareConfiguration.birthdayMonth = birthdayMonth;

    if (isCopy) {
        env->ReleaseStringUTFChars(nicknameString, nickname);
        env->ReleaseStringUTFChars(messageString, message);
    }

    return finalFirmwareConfiguration;
}

GPU::RenderSettings buildRenderSettings(JNIEnv* env, jobject renderSettings) {
    jclass renderSettingsClass = env->GetObjectClass(renderSettings);
    jboolean threadedRendering = env->GetBooleanField(renderSettings, env->GetFieldID(renderSettingsClass, "threadedRendering", "Z"));
    return {
            threadedRendering == JNI_TRUE,
            1,
            false
    };
}

double getCurrentMillis() {
    timespec now;
    clock_gettime(CLOCK_REALTIME, &now);
    return (now.tv_sec * 1000.0) + now.tv_nsec / 1000000.0;
}

void* emulate(void*)
{
    double startTick = getCurrentMillis();
    double lastTick = startTick;
    double lastMeasureFpsTick = startTick;
    double frameLimitError = 0.0;

    MelonDSAndroid::start();

    for (;;)
    {
        pthread_mutex_lock(&emuThreadMutex);
        if (paused) {
            isThreadReallyPaused = true;
            while (paused && !stop)
                pthread_cond_wait(&emuThreadCond, &emuThreadMutex);

            isThreadReallyPaused = false;
        }

        if (stop) {
            pthread_mutex_unlock(&emuThreadMutex);
            break;
        }

        pthread_mutex_unlock(&emuThreadMutex);

        MelonDSAndroid::updateMic();
        u32 nLines = MelonDSAndroid::loop();

        double currentTick = getCurrentMillis();
        double delay = currentTick - lastTick;

        if (limitFps)
        {
            float frameRate = (1000.0 * nLines) / ((float) targetFps * 263.0f);

            frameLimitError += frameRate - delay;
            if (frameLimitError < -frameRate)
                frameLimitError = -frameRate;
            if (frameLimitError > frameRate)
                frameLimitError = frameRate;

            if (round(frameLimitError) > 0.0)
            {
                usleep(frameLimitError * 1000);
                double timeBeforeSleep = currentTick;
                currentTick = getCurrentMillis();
                frameLimitError -= currentTick -timeBeforeSleep;
            }

            lastTick = currentTick;
        } else {
            if (delay < 1)
                usleep(1000);

            lastTick = getCurrentMillis();
        }

        observedFrames++;
        if (observedFrames >= 30) {
            double currentFpsTick = getCurrentMillis();
            fps = (int) (observedFrames * 1000.0) / (currentFpsTick - lastMeasureFpsTick);
            lastMeasureFpsTick = currentFpsTick;
            observedFrames = 0;
        }

    }

    MelonDSAndroid::cleanup();
    pthread_exit(NULL);
}