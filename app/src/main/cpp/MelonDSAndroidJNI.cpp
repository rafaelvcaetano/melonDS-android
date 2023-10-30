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
#include "UriFileHandler.h"
#include "JniEnvHandler.h"
#include "AndroidRACallback.h"
#include "MelonDSAndroidInterface.h"
#include "MelonDSAndroidConfiguration.h"
#include "MelonDSAndroidCameraHandler.h"
#include "RAAchievementMapper.h"

#define MAX_CHEAT_SIZE (2*64)

void* emulate(void*);

pthread_t emuThread;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;

bool started = false;
bool stop;
bool paused;
std::atomic_bool isThreadReallyPaused = false;
int observedFrames = 0;
int fps = 0;
int targetFps;
float fastForwardSpeedMultiplier;
bool limitFps = true;
bool isFastForwardEnabled = false;

jobject globalAssetManager;
jobject globalCameraManager;
jobject androidRaCallback;
MelonDSAndroidCameraHandler* androidCameraHandler;
AndroidRACallback* raCallback;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupEmulator(JNIEnv* env, jobject thiz, jobject emulatorConfiguration, jobject javaAssetManager, jobject cameraManager, jobject retroAchievementsCallback, jobject textureBuffer)
{
    MelonDSAndroid::EmulatorConfiguration finalEmulatorConfiguration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);
    fastForwardSpeedMultiplier = finalEmulatorConfiguration.fastForwardSpeedMultiplier;

    globalAssetManager = env->NewGlobalRef(javaAssetManager);
    globalCameraManager = env->NewGlobalRef(cameraManager);
    androidRaCallback = env->NewGlobalRef(retroAchievementsCallback);

    AAssetManager* assetManager = AAssetManager_fromJava(env, globalAssetManager);
    androidCameraHandler = new MelonDSAndroidCameraHandler(jniEnvHandler, globalCameraManager);
    raCallback = new AndroidRACallback(jniEnvHandler, androidRaCallback);

    u32* textureBufferPointer = (u32*) env->GetDirectBufferAddress(textureBuffer);

    MelonDSAndroid::setConfiguration(finalEmulatorConfiguration);
    MelonDSAndroid::setup(assetManager, androidCameraHandler, raCallback, textureBufferPointer, true);
    paused = false;
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

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupAchievements(JNIEnv* env, jobject thiz, jobjectArray achievements, jstring richPresenceScript)
{
    std::list<RetroAchievements::RAAchievement> internalAchievements;
    mapAchievementsFromJava(env, achievements, internalAchievements);

    std::string* richPresence = nullptr;

    if (richPresenceScript != nullptr)
    {
        jboolean isStringCopy;
        const char* richPresenceString = env->GetStringUTFChars(richPresenceScript, &isStringCopy);
        richPresence = new std::string(richPresenceString);

        if (isStringCopy)
            env->ReleaseStringUTFChars(richPresenceScript, richPresenceString);
    }

    MelonDSAndroid::setupAchievements(internalAchievements, richPresence);
    delete richPresence;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_unloadAchievements(JNIEnv* env, jobject thiz, jobjectArray achievements)
{
    std::list<RetroAchievements::RAAchievement> internalAchievements;
    mapAchievementsFromJava(env, achievements, internalAchievements);

    MelonDSAndroid::unloadAchievements(internalAchievements);
}

JNIEXPORT jstring JNICALL
Java_me_magnum_melonds_MelonEmulator_getRichPresenceStatus(JNIEnv* env, jobject thiz)
{
    std::string richPresenceString = MelonDSAndroid::getRichPresenceStatus();
    if (richPresenceString.empty())
        return nullptr;
    else
        return env->NewStringUTF(richPresenceString.c_str());
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRomInternal(JNIEnv* env, jobject thiz, jstring romPath, jstring sramPath, jboolean loadGbaRom, jstring gbaRomPath, jstring gbaSramPath)
{
    jboolean isCopy = JNI_FALSE;
    const char* rom = romPath == nullptr ? nullptr : env->GetStringUTFChars(romPath, &isCopy);
    const char* sram = sramPath == nullptr ? nullptr : env->GetStringUTFChars(sramPath, &isCopy);
    const char* gbaRom = gbaRomPath == nullptr ? nullptr : env->GetStringUTFChars(gbaRomPath, &isCopy);
    const char* gbaSram = gbaSramPath == nullptr ? nullptr : env->GetStringUTFChars(gbaSramPath, &isCopy);

    int result = MelonDSAndroid::loadRom(const_cast<char*>(rom), const_cast<char*>(sram), loadGbaRom, const_cast<char*>(gbaRom), const_cast<char*>(gbaSram));

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
    isThreadReallyPaused = false;
    limitFps = true;
    targetFps = 60;
    isFastForwardEnabled = false;

    pthread_mutex_init(&emuThreadMutex, NULL);
    pthread_cond_init(&emuThreadCond, NULL);
    pthread_create(&emuThread, NULL, emulate, NULL);
    pthread_setname_np(emuThread, "EmulatorThread");

    started = true;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_getFPS(JNIEnv* env, jobject thiz)
{
    return fps;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_pauseEmulation(JNIEnv* env, jobject thiz)
{
    if (started) {
        pthread_mutex_lock(&emuThreadMutex);
    }

    if (!stop) {
        paused = true;
    }

    if (started) {
        pthread_mutex_unlock(&emuThreadMutex);
    }

    MelonDSAndroid::pause();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resumeEmulation(JNIEnv* env, jobject thiz)
{
    if (started) {
        pthread_mutex_lock(&emuThreadMutex);
    }

    if (!stop) {
        paused = false;
        if (started) {
            pthread_cond_broadcast(&emuThreadCond);
        }
    }

    if (started) {
        pthread_mutex_unlock(&emuThreadMutex);
    }

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
Java_me_magnum_melonds_MelonEmulator_saveStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::saveState(saveStatePath);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::loadState(saveStatePath);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRewindState(JNIEnv* env, jobject thiz, jobject rewindSaveState) {
    bool result = true;

    pthread_mutex_lock(&emuThreadMutex);
    if (!stop) {
        bool wasPaused = paused;
        if (paused) {
            pthread_mutex_unlock(&emuThreadMutex);
        } else {
            pthread_mutex_unlock(&emuThreadMutex);
            Java_me_magnum_melonds_MelonEmulator_pauseEmulation(env, thiz);
        }

        jclass rewindSaveStateClass = env->FindClass("me/magnum/melonds/ui/emulator/rewind/model/RewindSaveState");
        jfieldID bufferField = env->GetFieldID(rewindSaveStateClass, "buffer", "Ljava/nio/ByteBuffer;");
        jfieldID screenshotBufferField = env->GetFieldID(rewindSaveStateClass, "screenshotBuffer", "Ljava/nio/ByteBuffer;");
        jfieldID frameField = env->GetFieldID(rewindSaveStateClass, "frame", "I");
        jobject buffer = env->GetObjectField(rewindSaveState, bufferField);
        jobject screenshotBuffer = env->GetObjectField(rewindSaveState, screenshotBufferField);
        jint frame = (int) env->GetIntField(rewindSaveState, frameField);

        // Make sure that the thread is really paused to avoid data corruption
        while (!isThreadReallyPaused);

        RewindManager::RewindSaveState state = RewindManager::RewindSaveState {
            .buffer = (u8*) env->GetDirectBufferAddress(buffer),
            .bufferSize = (u32) env->GetDirectBufferCapacity(buffer),
            .screenshot = (u8*) env->GetDirectBufferAddress(screenshotBuffer),
            .screenshotSize = (u32) env->GetDirectBufferCapacity(screenshotBuffer),
            .frame = frame
        };

        result = MelonDSAndroid::loadRewindState(state);

        // Resume emulation if it was running
        if (!wasPaused) {
            Java_me_magnum_melonds_MelonEmulator_resumeEmulation(env, thiz);
        }
    } else {
        // If the emulation is stopping, just ignore it
        pthread_mutex_unlock(&emuThreadMutex);
    }

    return result;
}

JNIEXPORT jobject JNICALL
Java_me_magnum_melonds_MelonEmulator_getRewindWindow(JNIEnv* env, jobject thiz) {
    auto currentRewindWindow = MelonDSAndroid::getRewindWindow();

    jclass rewindSaveStateClass = env->FindClass("me/magnum/melonds/ui/emulator/rewind/model/RewindSaveState");
    jmethodID rewindSaveStateConstructor = env->GetMethodID(rewindSaveStateClass, "<init>", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;I)V");

    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAddMethod = env->GetMethodID(listClass, "add", "(ILjava/lang/Object;)V");
    jobject rewindStateList = env->NewObject(listClass, listConstructor);

    int index = 0;
    for (auto state : currentRewindWindow.rewindStates) {
        jobject stateBuffer = env->NewDirectByteBuffer(state.buffer, state.bufferSize);
        jobject stateScreenshot = env->NewDirectByteBuffer(state.screenshot, state.screenshotSize);
        jobject rewindSaveState = env->NewObject(rewindSaveStateClass, rewindSaveStateConstructor, stateBuffer, stateScreenshot, state.frame);
        env->CallVoidMethod(rewindStateList, listAddMethod, index++, rewindSaveState);
    }

    jclass rewindWindowClass = env->FindClass("me/magnum/melonds/ui/emulator/rewind/model/RewindWindow");
    jmethodID rewindWindowConstructor = env->GetMethodID(rewindWindowClass, "<init>", "(ILjava/util/ArrayList;)V");
    jobject rewindWindow = env->NewObject(rewindWindowClass, rewindWindowConstructor, currentRewindWindow.currentFrame, rewindStateList);
    return rewindWindow;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_stopEmulation(JNIEnv* env, jobject thiz)
{
    if (started)
    {
        pthread_mutex_lock(&emuThreadMutex);
        stop = true;
        paused = false;
        started = false;
        pthread_cond_broadcast(&emuThreadCond);
        pthread_mutex_unlock(&emuThreadMutex);

        pthread_join(emuThread, NULL);
        pthread_mutex_destroy(&emuThreadMutex);
        pthread_cond_destroy(&emuThreadCond);
    }

    env->DeleteGlobalRef(globalAssetManager);
    env->DeleteGlobalRef(globalCameraManager);
    env->DeleteGlobalRef(androidRaCallback);

    globalAssetManager = nullptr;
    globalCameraManager = nullptr;
    androidRaCallback = nullptr;

    delete androidCameraHandler;
    delete raCallback;
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
    MelonDSAndroid::EmulatorConfiguration newConfiguration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);
    MelonDSAndroid::updateEmulatorConfiguration(newConfiguration);
    fastForwardSpeedMultiplier = newConfiguration.fastForwardSpeedMultiplier;

    if (isFastForwardEnabled) {
        limitFps = fastForwardSpeedMultiplier > 0;
        targetFps = 60 * fastForwardSpeedMultiplier;
    }
}
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