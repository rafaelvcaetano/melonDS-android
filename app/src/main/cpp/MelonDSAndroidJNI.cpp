#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <jni.h>
#include <string>
#include <sstream>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdlib>
#include <time.h>
#include <MelonDS.h>
#include <MelonDSAudio.h>
#include <RomGbaSlotConfig.h>
#include <android/asset_manager_jni.h>
#include "UriFileHandler.h"
#include "JniEnvHandler.h"
#include "AndroidMelonEventMessenger.h"
#include "MelonDSAndroidInterface.h"
#include "MelonDSAndroidConfiguration.h"
#include "MelonDSAndroidCameraHandler.h"
#include "RetroAchievementsMapper.h"
#include "performancehint/ThreadSafePerformanceHintSession.h"
#include "performancehint/PerformanceHintManagerFactory.h"

#include "Platform.h"

enum GbaSlotType {
    NONE = 0,
    GBA_ROM = 1,
    RUMBLE_PAK = 2,
    MEMORY_EXPANSION = 3,
    MOTION_PAK_HOMEBREW = 4,
    MOTION_PAK_RETAIL = 5,
};

void* emulate(void*);
MelonDSAndroid::RomGbaSlotConfig* buildGbaSlotConfig(GbaSlotType slotType, const char* romPath, const char* savePath);

pthread_t emuThread;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;

bool started = false;
bool stop;
bool paused;
std::atomic_bool isThreadReallyPaused = false;
int observedFrames = 0;
float fps = 0;
int targetFps;
float fastForwardSpeedMultiplier;
bool limitFps = true;
bool isFastForwardEnabled = false;

jobject globalCameraManager;
MelonDSAndroidCameraHandler* androidCameraHandler;

static const int64_t FRAME_DURATION_60FPS_NS = 16666666;
static const int64_t FRAME_DURATION_1000FPS_NS = 1000000; // 1ms. Used as frame time when fast-forward is enabled
ThreadSafePerformanceHintSession* performanceHintSession = nullptr;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupEmulator(JNIEnv* env, jobject thiz, jobject emulatorConfiguration, jobject cameraManager, jobject screenshotBuffer)
{
    MelonDSAndroid::EmulatorConfiguration finalEmulatorConfiguration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);
    fastForwardSpeedMultiplier = finalEmulatorConfiguration.fastForwardSpeedMultiplier;

    globalCameraManager = env->NewGlobalRef(cameraManager);

    auto androidEventMessenger = std::make_shared<AndroidMelonEventMessenger>();
    androidCameraHandler = new MelonDSAndroidCameraHandler(jniEnvHandler, globalCameraManager);
    u32* screenshotBufferPointer = (u32*) env->GetDirectBufferAddress(screenshotBuffer);

    MelonDSAndroid::setConfiguration(std::move(finalEmulatorConfiguration));
    MelonDSAndroid::setup(androidCameraHandler, std::move(androidEventMessenger), screenshotBufferPointer, 0);
    paused = false;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupCheats(JNIEnv* env, jobject thiz, jobjectArray cheats)
{
    jsize cheatCount = env->GetArrayLength(cheats);
    if (cheatCount < 1) {
        MelonDSAndroid::setCodeList(std::list<MelonDSAndroid::Cheat>());
        return;
    }

    jobject firstCheat = env->GetObjectArrayElement(cheats, 0);
    jclass cheatClass = env->GetObjectClass(firstCheat);
    env->DeleteLocalRef(firstCheat);
    jfieldID codeField = env->GetFieldID(cheatClass, "code", "Ljava/lang/String;");

    std::list<MelonDSAndroid::Cheat> internalCheats;

    for (int i = 0; i < cheatCount; ++i) {
        jobject cheat = env->GetObjectArrayElement(cheats, i);
        jstring code = (jstring) env->GetObjectField(cheat, codeField);
        const char* codeStringPtr = env->GetStringUTFChars(code, JNI_FALSE);
        if (!codeStringPtr)
        {
            env->DeleteLocalRef(code);
            env->DeleteLocalRef(cheat);
            break;
        }
        std::string codeString = codeStringPtr;
        // Since each part of a cheat code has 8 characters (4 bytes), we can add 1 to the length (to ensure that each part has a matching space separator) and divide by 9
        // (part length + space separator) to calculate the total number of parts in the cheat
        size_t codeLength = (codeString.size() + 1) / 9;

        bool isBad = false;
        std::size_t start = 0;
        std::size_t end = 0;

        MelonDSAndroid::Cheat internalCheat;
        internalCheat.code.reserve(codeLength);

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
                    internalCheat.code.push_back((u32) section);
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
                internalCheat.code.push_back((u32) section);
            }
        }

        env->ReleaseStringUTFChars(code, codeStringPtr);
        env->DeleteLocalRef(code);
        env->DeleteLocalRef(cheat);

        if (isBad) {
            continue;
        }

        internalCheats.push_back(internalCheat);
    }

    env->DeleteLocalRef(cheatClass);

    MelonDSAndroid::setCodeList(internalCheats);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupAchievements(JNIEnv* env, jobject thiz, jobjectArray achievements, jobjectArray leaderboards, jstring richPresenceScript)
{
    std::list<MelonDSAndroid::RetroAchievements::RAAchievement> internalAchievements;
    std::list<MelonDSAndroid::RetroAchievements::RALeaderboard> internalLeaderboards;
    mapAchievementsFromJava(env, achievements, internalAchievements);
    mapLeaderboardsFromJava(env, leaderboards, internalLeaderboards);

    std::optional<std::string> richPresence = std::nullopt;

    if (richPresenceScript != nullptr)
    {
        const char* richPresenceString = env->GetStringUTFChars(richPresenceScript, nullptr);
        if (richPresenceString)
        {
            richPresence = richPresenceString;
            env->ReleaseStringUTFChars(richPresenceScript, richPresenceString);
        }
    }

    MelonDSAndroid::setupAchievements(internalAchievements, internalLeaderboards, richPresence);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_unloadRetroAchievementsData(JNIEnv* env, jobject thiz)
{
    MelonDSAndroid::unloadRetroAchievementsData();
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

JNIEXPORT jobjectArray JNICALL
Java_me_magnum_melonds_MelonEmulator_getRuntimeAchievements(JNIEnv* env, jobject thiz)
{
    jclass simpleRuntimeAchievementClass = env->FindClass("me/magnum/melonds/domain/model/retroachievements/RASimpleRuntimeAchievement");
    jmethodID simpleRuntimeAchievementConstructor = env->GetMethodID(simpleRuntimeAchievementClass, "<init>", "(JII)V");

    auto runtimeAchievements = MelonDSAndroid::getRuntimeAchievements();

    jobjectArray achievements = env->NewObjectArray(runtimeAchievements.size(), simpleRuntimeAchievementClass, nullptr);

    int index = 0;
    for (const auto &item: runtimeAchievements)
    {
        jobject simpleRuntimeAchievement = env->NewObject(simpleRuntimeAchievementClass, simpleRuntimeAchievementConstructor, item.id, (jint) item.value, (jint) item.target);
        env->SetObjectArrayElement(achievements, index++, simpleRuntimeAchievement);
        env->DeleteLocalRef(simpleRuntimeAchievement);
    }

    env->DeleteLocalRef(simpleRuntimeAchievementClass);

    return achievements;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRomInternal(JNIEnv* env, jobject thiz, jstring romPath, jstring sramPath, jint gbaSlotType, jstring gbaRomPath, jstring gbaSramPath)
{
    const char* rom = romPath == nullptr ? nullptr : env->GetStringUTFChars(romPath, nullptr);
    const char* sram = sramPath == nullptr ? nullptr : env->GetStringUTFChars(sramPath, nullptr);
    const char* gbaRom = gbaRomPath == nullptr ? nullptr : env->GetStringUTFChars(gbaRomPath, nullptr);
    const char* gbaSram = gbaSramPath == nullptr ? nullptr : env->GetStringUTFChars(gbaSramPath, nullptr);

    MelonDSAndroid::RomGbaSlotConfig* gbaSlotConfig = buildGbaSlotConfig((GbaSlotType) gbaSlotType, gbaRom, gbaSram);
    int result = (rom && sram) ? MelonDSAndroid::loadRom(rom, sram, gbaSlotConfig) : 2;
    delete gbaSlotConfig;

    if (rom) env->ReleaseStringUTFChars(romPath, rom);
    if (sram) env->ReleaseStringUTFChars(sramPath, sram);
    if (gbaRom) env->ReleaseStringUTFChars(gbaRomPath, gbaRom);
    if (gbaSram) env->ReleaseStringUTFChars(gbaSramPath, gbaSram);

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
    MelonDSAndroid::setAudioFastForwardActive(false);

    pthread_mutex_init(&emuThreadMutex, NULL);
    pthread_cond_init(&emuThreadCond, NULL);
    pthread_create(&emuThread, NULL, emulate, NULL);
    pthread_setname_np(emuThread, "EmulatorThread");

    started = true;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_presentFrame(JNIEnv* env, jobject thiz, jlong deadlineNs, jobject renderFrameCallback)
{
    jclass presentFrameWrapperClass = env->GetObjectClass(renderFrameCallback);
    jmethodID renderFrameMethodId = env->GetMethodID(presentFrameWrapperClass, "renderFrame", "(ZI)V");

    std::optional<std::chrono::time_point<std::chrono::steady_clock>> deadlineTime;
    if (deadlineNs > 0)
    {
        std::chrono::nanoseconds deadline(deadlineNs);
        deadlineTime = std::make_optional(std::chrono::time_point<std::chrono::steady_clock>(deadline));
    }
    else
    {
        deadlineTime = std::nullopt;
    }

    Frame* presentationFrame = MelonDSAndroid::getPresentationFrame(deadlineTime);
    EGLDisplay currentDisplay = eglGetCurrentDisplay();

    if (presentationFrame != nullptr && presentationFrame->presentFence)
    {
        eglDestroySyncKHR(currentDisplay, presentationFrame->presentFence);
        presentationFrame->presentFence = 0;
    }

    if (presentationFrame != nullptr)
    {
        eglWaitSyncKHR(currentDisplay, presentationFrame->renderFence, 0);
        env->CallVoidMethod(renderFrameCallback, renderFrameMethodId, true, (jint) presentationFrame->frameTexture);
        EGLSyncKHR presentFence = eglCreateSyncKHR(currentDisplay, EGL_SYNC_FENCE_KHR, nullptr);
        presentationFrame->presentFence = presentFence;
    }
    else
    {
        env->CallVoidMethod(renderFrameCallback, renderFrameMethodId, false, 0);
    }
}

JNIEXPORT jfloat JNICALL
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
        // Resume under the emulator mutex, before releasing the frame loop.
        MelonDSAndroid::resume();
        paused = false;
        if (started) {
            pthread_cond_broadcast(&emuThreadCond);
        }
    }

    if (started) {
        pthread_mutex_unlock(&emuThreadMutex);
    }
}

// Stops the emulation thread for an operation that touches the whole machine state, the way
// resetEmulation and loadRewindState already do it. Returns whether it was already paused, which
// is what resumeEmuThreadAfterSyncOperation() needs to restore the previous state.
static bool pauseEmuThreadForSyncOperation(JNIEnv* env, jobject thiz)
{
    // Nothing to synchronise against before startEmulation(): emuThreadMutex isn't even
    // initialised yet and isThreadReallyPaused would never become true.
    if (!started)
        return true;

    pthread_mutex_lock(&emuThreadMutex);
    if (stop) {
        // The emulation is stopping; leave it alone, as the other entry points do.
        pthread_mutex_unlock(&emuThreadMutex);
        return true;
    }

    bool wasPaused = paused;
    pthread_mutex_unlock(&emuThreadMutex);

    if (!wasPaused) {
        Java_me_magnum_melonds_MelonEmulator_pauseEmulation(env, thiz);
    }

    // Make sure that the thread is really paused to avoid data corruption
    while (!isThreadReallyPaused);

    return wasPaused;
}

static void resumeEmuThreadAfterSyncOperation(JNIEnv* env, jobject thiz, bool wasPaused)
{
    // Resume emulation only if it was running
    if (!wasPaused) {
        Java_me_magnum_melonds_MelonEmulator_resumeEmulation(env, thiz);
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resetEmulation(JNIEnv* env, jobject thiz) {
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
        MelonDSAndroid::reset();
        Java_me_magnum_melonds_MelonEmulator_resumeEmulation(env, thiz);
    } else {
        // If the emulation is stopping, just ignore it
        pthread_mutex_unlock(&emuThreadMutex);
    }
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_saveStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    // Serialising the whole machine while the emulation thread keeps running yields a torn state.
    bool wasPaused = pauseEmuThreadForSyncOperation(env, thiz);
    jboolean result = saveStatePath && MelonDSAndroid::saveState(saveStatePath);
    resumeEmuThreadAfterSyncOperation(env, thiz, wasPaused);
    if (saveStatePath) env->ReleaseStringUTFChars(path, saveStatePath);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    // Loading rewrites the machine state and makes the core request a save flush, which would
    // race the CheckFlush() the emulation thread runs at the end of every frame.
    bool wasPaused = pauseEmuThreadForSyncOperation(env, thiz);
    jboolean result = saveStatePath && MelonDSAndroid::loadState(saveStatePath);
    resumeEmuThreadAfterSyncOperation(env, thiz, wasPaused);
    if (saveStatePath) env->ReleaseStringUTFChars(path, saveStatePath);
    return result;
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
        jfieldID bufferContentSizeField = env->GetFieldID(rewindSaveStateClass, "bufferContentSize", "J");
        jfieldID screenshotBufferField = env->GetFieldID(rewindSaveStateClass, "screenshotBuffer", "Ljava/nio/ByteBuffer;");
        jfieldID frameField = env->GetFieldID(rewindSaveStateClass, "frame", "I");
        jobject buffer = env->GetObjectField(rewindSaveState, bufferField);
        jlong bufferContentSize = env->GetLongField(rewindSaveState, bufferContentSizeField);
        jobject screenshotBuffer = env->GetObjectField(rewindSaveState, screenshotBufferField);
        jint frame = (int) env->GetIntField(rewindSaveState, frameField);

        // Make sure that the thread is really paused to avoid data corruption
        while (!isThreadReallyPaused);

        melonDS::RewindSaveState state = melonDS::RewindSaveState {
            .buffer = (u8*) env->GetDirectBufferAddress(buffer),
            .bufferSize = (u32) env->GetDirectBufferCapacity(buffer),
            .bufferContentSize = (u32) bufferContentSize,
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
    jmethodID rewindSaveStateConstructor = env->GetMethodID(rewindSaveStateClass, "<init>", "(Ljava/nio/ByteBuffer;JLjava/nio/ByteBuffer;I)V");

    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    jmethodID listAddMethod = env->GetMethodID(listClass, "add", "(ILjava/lang/Object;)V");
    jobject rewindStateList = env->NewObject(listClass, listConstructor);

    int index = 0;
    for (auto state : currentRewindWindow.rewindStates) {
        jobject stateBuffer = env->NewDirectByteBuffer(state.buffer, state.bufferSize);
        jobject stateScreenshot = env->NewDirectByteBuffer(state.screenshot, state.screenshotSize);
        jobject rewindSaveState = env->NewObject(rewindSaveStateClass, rewindSaveStateConstructor, stateBuffer, (jlong) state.bufferContentSize, stateScreenshot, state.frame);
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

    // After the join and before the SaveManagers are destroyed: the emulator is stopped, so
    // copying the primary buffer is safe here and nowhere else. Without this, a write that was
    // requested but never staged is invisible to the final flush, which then reports success
    // with nothing pending.
    MelonDSAndroid::stageSaves();

    MelonDSAndroid::cleanup();

    if (globalCameraManager != nullptr)
    {
        env->DeleteGlobalRef(globalCameraManager);
        globalCameraManager = nullptr;
    }

    delete androidCameraHandler;
    androidCameraHandler = nullptr;
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
Java_me_magnum_melonds_MelonEmulator_updateMotionData(JNIEnv* env, jobject thiz, jfloat ax, jfloat ay, jfloat az, jfloat rx, jfloat ry, jfloat rz)
{
    MelonDSAndroid::updateMotionData(ax, ay, az, rx, ry, rz);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_takeScreenshot(JNIEnv* env, jobject thiz)
{
    return MelonDSAndroid::takeScreenshot();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setFastForwardEnabled(JNIEnv* env, jobject thiz, jboolean enabled)
{
    isFastForwardEnabled = enabled;
    MelonDSAndroid::setAudioFastForwardActive(enabled);
    if (enabled) {
        limitFps = fastForwardSpeedMultiplier > 0;
        targetFps = 60 * fastForwardSpeedMultiplier;
    } else {
        limitFps = true;
        targetFps = 60;
    }

    if (performanceHintSession != nullptr) {
        if (enabled) {
            if (fastForwardSpeedMultiplier > 0) {
                auto frameDurationNs = static_cast<int64_t>(FRAME_DURATION_60FPS_NS / fastForwardSpeedMultiplier);
                performanceHintSession->updateTargetWorkDuration(frameDurationNs);
            } else {
                performanceHintSession->updateTargetWorkDuration(FRAME_DURATION_1000FPS_NS);
            }
        } else {
            performanceHintSession->updateTargetWorkDuration(FRAME_DURATION_60FPS_NS);
        }
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setMicrophoneEnabled(JNIEnv* env, jobject thiz, jboolean enabled)
{
    if (enabled)
        MelonDSAndroid::userEnableMic();
    else
        MelonDSAndroid::userDisableMic();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_updateEmulatorConfiguration(JNIEnv* env, jobject thiz, jobject emulatorConfiguration)
{
    MelonDSAndroid::EmulatorConfiguration newConfiguration = MelonDSAndroidConfiguration::buildEmulatorConfiguration(env, emulatorConfiguration);

    fastForwardSpeedMultiplier = newConfiguration.fastForwardSpeedMultiplier;

    // Applying the configuration swaps the shared_ptr the emulation thread reads on every frame
    // and pokes the SPU, so stop the thread first, like resetEmulation does.
    bool wasPaused = pauseEmuThreadForSyncOperation(env, thiz);
    MelonDSAndroid::updateEmulatorConfiguration(std::make_unique<MelonDSAndroid::EmulatorConfiguration>(std::move(newConfiguration)));
    resumeEmuThreadAfterSyncOperation(env, thiz, wasPaused);

    if (isFastForwardEnabled) {
        limitFps = fastForwardSpeedMultiplier > 0;
        targetFps = 60 * fastForwardSpeedMultiplier;

        if (performanceHintSession != nullptr) {
            if (fastForwardSpeedMultiplier > 0) {
                auto frameDurationNs = static_cast<int64_t>(FRAME_DURATION_60FPS_NS / fastForwardSpeedMultiplier);
                performanceHintSession->updateTargetWorkDuration(frameDurationNs);
            } else {
                performanceHintSession->updateTargetWorkDuration(FRAME_DURATION_1000FPS_NS);
            }
        }
    }
}
}

// The two sync helpers above, exposed to the other JNI translation units (WfcSettingsJNI.cpp)
// that need to touch the machine state from a non-emulator thread. Neither pauseEmulation() nor
// resumeEmulation() reads its JNI arguments, so there is nothing to forward.
namespace MelonDSAndroid {
    bool pauseEmulationThreadForSyncOperation()
    {
        return pauseEmuThreadForSyncOperation(nullptr, nullptr);
    }

    void resumeEmulationThreadAfterSyncOperation(bool wasPaused)
    {
        resumeEmuThreadAfterSyncOperation(nullptr, nullptr, wasPaused);
    }
}

MelonDSAndroid::RomGbaSlotConfig* buildGbaSlotConfig(GbaSlotType slotType, const char* romPath, const char* savePath)
{
    if (slotType == GbaSlotType::GBA_ROM && romPath != nullptr)
    {
        MelonDSAndroid::RomGbaSlotConfigGbaRom* gbaSlotConfigGbaRom = new MelonDSAndroid::RomGbaSlotConfigGbaRom {
            .romPath = std::string(romPath),
            .savePath = savePath ? std::string(savePath) : "",
        };
        return (MelonDSAndroid::RomGbaSlotConfig*) gbaSlotConfigGbaRom;
    }
    else if (slotType == GbaSlotType::RUMBLE_PAK)
    {
        return (MelonDSAndroid::RomGbaSlotConfig*) new MelonDSAndroid::RomGbaSlotRumblePak;
    }
    else if (slotType == GbaSlotType::MEMORY_EXPANSION)
    {
        return (MelonDSAndroid::RomGbaSlotConfig*) new MelonDSAndroid::RomGbaSlotConfigMemoryExpansion;
    }
    else if (slotType == GbaSlotType::MOTION_PAK_HOMEBREW)
    {
        return (MelonDSAndroid::RomGbaSlotConfig*) new MelonDSAndroid::RomGbaSlotMotionPakHomebrew;
    }
    else if (slotType == GbaSlotType::MOTION_PAK_RETAIL)
    {
        return (MelonDSAndroid::RomGbaSlotConfig*) new MelonDSAndroid::RomGbaSlotMotionPakRetail;
    }
    else
    {
        return (MelonDSAndroid::RomGbaSlotConfig*) new MelonDSAndroid::RomGbaSlotConfigNone;
    }
}

double getCurrentMillis() {
    timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (now.tv_sec * 1000.0) + now.tv_nsec / 1000000.0;
}

void* emulate(void*)
{
    double startTick = getCurrentMillis();
    double lastTick = startTick;
    double lastMeasureFpsTick = startTick;
    double frameLimitError = 0.0;

    MelonDSAndroid::start();

    auto manager = PerformanceHintManagerFactory::create(jniEnvHandler);
    performanceHintSession = new ThreadSafePerformanceHintSession(std::move(manager));
    if (performanceHintSession != nullptr) {
        performanceHintSession->createSession(gettid(), FRAME_DURATION_60FPS_NS);
    }

    for (;;)
    {
        pthread_mutex_lock(&emuThreadMutex);
        if (paused) {
            isThreadReallyPaused = true;
            while (paused && !stop)
                pthread_cond_wait(&emuThreadCond, &emuThreadMutex);

            frameLimitError = 0;
            lastTick = getCurrentMillis();
            isThreadReallyPaused = false;
        }

        if (stop) {
            pthread_mutex_unlock(&emuThreadMutex);
            break;
        }

        pthread_mutex_unlock(&emuThreadMutex);

        auto frameStart = std::chrono::steady_clock::now();

        u32 nLines = MelonDSAndroid::loop();

        auto frameDuration = std::chrono::steady_clock::now() - frameStart;
        if (performanceHintSession != nullptr)
            performanceHintSession->reportActualWorkDuration(std::chrono::nanoseconds(frameDuration).count());

        double currentTick = getCurrentMillis();
        double delay = currentTick - lastTick;

        // All times are in ms
        double frameTimeStep = (double) nLines / ((float) targetFps * 263.0) * 1000.0;
        if (frameTimeStep < 1)
            frameTimeStep = 1;

        if (limitFps)
        {
            frameLimitError += frameTimeStep - delay;
            if (frameLimitError < -frameTimeStep)
                frameLimitError = -frameTimeStep;
            if (frameLimitError > frameTimeStep)
                frameLimitError = frameTimeStep;

            if (round(frameLimitError) > 0.0)
            {
                timespec sleepTime = {
                    .tv_sec = 0,
                    .tv_nsec = (long) (frameLimitError * 1000000),
                };
                clock_nanosleep(CLOCK_MONOTONIC, 0, &sleepTime, nullptr);
                double timeAfterSleep = getCurrentMillis();
                frameLimitError -= timeAfterSleep - currentTick;
                currentTick = timeAfterSleep;
            }

            lastTick = currentTick;
        } else {
            frameLimitError = 0;
            lastTick = getCurrentMillis();
        }

        observedFrames++;
        if (observedFrames >= 30) {
            fps = (observedFrames * 1000.0) / (lastTick - lastMeasureFpsTick);
            lastMeasureFpsTick = lastTick;
            observedFrames = 0;
        }
    }

    if (performanceHintSession != nullptr) {
        performanceHintSession->destroySession();

        delete performanceHintSession;
        performanceHintSession = nullptr;
    }

    MelonDSAndroid::stop();
    pthread_exit(NULL);
}
