#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <cerrno>
#include <cstdint>
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
pthread_mutex_t emuThreadMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t emuThreadCond = PTHREAD_COND_INITIALIZER;
pthread_mutex_t coreOperationMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t pauseApiMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t pauseControlMutex = PTHREAD_MUTEX_INITIALIZER;

enum class EmulatorThreadState {
    NotStarted,
    Starting,
    Running,
    Stopping,
    Stopped,
    StartFailed,
};

enum class PauseResult {
    Success,
    AlreadyPaused,
    Timeout,
    Stopped,
    NotStarted,
};

enum class EmulationStatus {
    NotStarted,
    Starting,
    Running,
    Paused,
    PauseRequested,
    Stopping,
    Stopped,
    StartFailed,
};

EmulatorThreadState emuThreadState = EmulatorThreadState::NotStarted;
bool emuThreadJoinable = false;
bool stopRequested = false;
bool userPauseRequested = false;
unsigned int synchronizedOperationCount = 0;
bool emulatorAtSafePoint = false;
bool corePauseApplied = false;
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
static const int64_t PAUSE_TIMEOUT_MS = 2000;
ThreadSafePerformanceHintSession* performanceHintSession = nullptr;

static bool isPauseRequested()
{
    return userPauseRequested || synchronizedOperationCount > 0;
}

static void applyCorePauseState()
{
    pthread_mutex_lock(&pauseControlMutex);
    while (true) {
        pthread_mutex_lock(&emuThreadMutex);
        if (emuThreadState != EmulatorThreadState::Running) {
            pthread_mutex_unlock(&emuThreadMutex);
            break;
        }

        bool shouldPause = isPauseRequested();
        if (shouldPause == corePauseApplied) {
            pthread_mutex_unlock(&emuThreadMutex);
            break;
        }
        pthread_mutex_unlock(&emuThreadMutex);

        if (shouldPause) {
            MelonDSAndroid::pause();
        } else {
            MelonDSAndroid::resume();
        }

        pthread_mutex_lock(&emuThreadMutex);
        corePauseApplied = shouldPause;
        pthread_mutex_unlock(&emuThreadMutex);
    }
    pthread_mutex_unlock(&pauseControlMutex);
}

static timespec getTimeout(int64_t timeoutMs)
{
    timespec timeout;
    clock_gettime(CLOCK_REALTIME, &timeout);
    timeout.tv_sec += timeoutMs / 1000;
    timeout.tv_nsec += (timeoutMs % 1000) * 1000000;
    if (timeout.tv_nsec >= 1000000000) {
        timeout.tv_sec++;
        timeout.tv_nsec -= 1000000000;
    }
    return timeout;
}

static bool waitForThreadStartLocked(const timespec& timeout)
{
    while (emuThreadState == EmulatorThreadState::Starting && !stopRequested) {
        int result = pthread_cond_timedwait(&emuThreadCond, &emuThreadMutex, &timeout);
        if (result == ETIMEDOUT) {
            return false;
        }
    }
    return emuThreadState == EmulatorThreadState::Running && !stopRequested;
}

static bool waitForSafePointLocked(const timespec& timeout)
{
    while (!emulatorAtSafePoint && emuThreadState == EmulatorThreadState::Running && !stopRequested) {
        int result = pthread_cond_timedwait(&emuThreadCond, &emuThreadMutex, &timeout);
        if (result == ETIMEDOUT) {
            return false;
        }
    }
    return emulatorAtSafePoint && emuThreadState == EmulatorThreadState::Running && !stopRequested;
}

static PauseResult requestUserPause(int64_t timeoutMs)
{
    pthread_mutex_lock(&pauseApiMutex);
    pthread_mutex_lock(&emuThreadMutex);
    timespec timeout = getTimeout(timeoutMs);

    if (emuThreadState == EmulatorThreadState::NotStarted || emuThreadState == EmulatorThreadState::StartFailed) {
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&pauseApiMutex);
        return PauseResult::NotStarted;
    }
    if (emuThreadState == EmulatorThreadState::Starting && !waitForThreadStartLocked(timeout)) {
        PauseResult result = emuThreadState == EmulatorThreadState::Starting
            ? PauseResult::Timeout
            : PauseResult::Stopped;
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&pauseApiMutex);
        return result;
    }
    if (emuThreadState != EmulatorThreadState::Running || stopRequested) {
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&pauseApiMutex);
        return PauseResult::Stopped;
    }

    bool wasAlreadyPaused = userPauseRequested;
    userPauseRequested = true;
    pthread_mutex_unlock(&emuThreadMutex);
    applyCorePauseState();

    pthread_mutex_lock(&emuThreadMutex);
    bool reachedSafePoint = waitForSafePointLocked(timeout);
    PauseResult result;
    if (reachedSafePoint) {
        result = wasAlreadyPaused ? PauseResult::AlreadyPaused : PauseResult::Success;
    } else if (emuThreadState != EmulatorThreadState::Running || stopRequested) {
        result = PauseResult::Stopped;
    } else {
        result = PauseResult::Timeout;
    }

    pthread_mutex_unlock(&emuThreadMutex);
    pthread_mutex_unlock(&pauseApiMutex);
    return result;
}

static bool beginSynchronizedOperation()
{
    pthread_mutex_lock(&coreOperationMutex);
    pthread_mutex_lock(&emuThreadMutex);
    timespec timeout = getTimeout(PAUSE_TIMEOUT_MS);

    if ((emuThreadState == EmulatorThreadState::Starting && !waitForThreadStartLocked(timeout)) ||
        emuThreadState != EmulatorThreadState::Running || stopRequested) {
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&coreOperationMutex);
        return false;
    }

    synchronizedOperationCount++;
    pthread_mutex_unlock(&emuThreadMutex);
    applyCorePauseState();

    pthread_mutex_lock(&emuThreadMutex);
    if (!waitForSafePointLocked(timeout)) {
        synchronizedOperationCount--;
        pthread_cond_broadcast(&emuThreadCond);
        pthread_mutex_unlock(&emuThreadMutex);
        applyCorePauseState();
        pthread_mutex_unlock(&coreOperationMutex);
        return false;
    }

    pthread_mutex_unlock(&emuThreadMutex);
    return true;
}

static void endSynchronizedOperation()
{
    pthread_mutex_lock(&emuThreadMutex);
    synchronizedOperationCount--;
    pthread_cond_broadcast(&emuThreadCond);
    pthread_mutex_unlock(&emuThreadMutex);
    applyCorePauseState();
    pthread_mutex_unlock(&coreOperationMutex);
}

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
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupCheats(JNIEnv* env, jobject thiz, jobjectArray cheats)
{
    jsize cheatCount = env->GetArrayLength(cheats);
    if (cheatCount < 1) {
        MelonDSAndroid::setCodeList(std::list<MelonDSAndroid::Cheat>());
        return;
    }

    jclass cheatClass = env->GetObjectClass(env->GetObjectArrayElement(cheats, 0));
    jfieldID codeField = env->GetFieldID(cheatClass, "code", "Ljava/lang/String;");

    std::list<MelonDSAndroid::Cheat> internalCheats;

    for (int i = 0; i < cheatCount; ++i) {
        jobject cheat = env->GetObjectArrayElement(cheats, i);
        jstring code = (jstring) env->GetObjectField(cheat, codeField);
        const char* codeStringPtr = env->GetStringUTFChars(code, JNI_FALSE);
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

        if (isBad) {
            continue;
        }

        internalCheats.push_back(internalCheat);
    }

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
        jboolean isStringCopy;
        const char* richPresenceString = env->GetStringUTFChars(richPresenceScript, &isStringCopy);
        richPresence = richPresenceString;

        if (isStringCopy)
            env->ReleaseStringUTFChars(richPresenceScript, richPresenceString);
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
    }

    return achievements;
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRomInternal(JNIEnv* env, jobject thiz, jstring romPath, jstring sramPath, jint gbaSlotType, jstring gbaRomPath, jstring gbaSramPath)
{
    jboolean isCopy = JNI_FALSE;
    const char* rom = romPath == nullptr ? nullptr : env->GetStringUTFChars(romPath, &isCopy);
    const char* sram = sramPath == nullptr ? nullptr : env->GetStringUTFChars(sramPath, &isCopy);
    const char* gbaRom = gbaRomPath == nullptr ? nullptr : env->GetStringUTFChars(gbaRomPath, &isCopy);
    const char* gbaSram = gbaSramPath == nullptr ? nullptr : env->GetStringUTFChars(gbaSramPath, &isCopy);

    MelonDSAndroid::RomGbaSlotConfig* gbaSlotConfig = buildGbaSlotConfig((GbaSlotType) gbaSlotType, gbaRom, gbaSram);
    int result = MelonDSAndroid::loadRom(rom, sram, gbaSlotConfig);
    delete gbaSlotConfig;

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

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_startEmulation(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&coreOperationMutex);
    pthread_mutex_lock(&emuThreadMutex);
    if (emuThreadJoinable || emuThreadState == EmulatorThreadState::Starting ||
        emuThreadState == EmulatorThreadState::Running || emuThreadState == EmulatorThreadState::Stopping) {
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&coreOperationMutex);
        return JNI_FALSE;
    }

    stopRequested = false;
    userPauseRequested = false;
    synchronizedOperationCount = 0;
    emulatorAtSafePoint = false;
    corePauseApplied = false;
    limitFps = true;
    targetFps = 60;
    isFastForwardEnabled = false;

    int createResult = pthread_create(&emuThread, NULL, emulate, NULL);
    if (createResult != 0) {
        emuThreadState = EmulatorThreadState::StartFailed;
        pthread_cond_broadcast(&emuThreadCond);
        pthread_mutex_unlock(&emuThreadMutex);
        pthread_mutex_unlock(&coreOperationMutex);
        return JNI_FALSE;
    }

    emuThreadJoinable = true;
    emuThreadState = EmulatorThreadState::Starting;
    pthread_setname_np(emuThread, "EmulatorThread");
    pthread_mutex_unlock(&emuThreadMutex);
    pthread_mutex_unlock(&coreOperationMutex);

    return JNI_TRUE;
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

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_pauseEmulationInternal(JNIEnv* env, jobject thiz, jlong timeoutMs)
{
    return static_cast<jint>(requestUserPause(timeoutMs));
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resumeEmulation(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&pauseApiMutex);
    pthread_mutex_lock(&emuThreadMutex);
    if (emuThreadState == EmulatorThreadState::Running && !stopRequested) {
        userPauseRequested = false;
        pthread_cond_broadcast(&emuThreadCond);
    }
    pthread_mutex_unlock(&emuThreadMutex);
    applyCorePauseState();
    pthread_mutex_unlock(&pauseApiMutex);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resetEmulation(JNIEnv* env, jobject thiz) {
    if (beginSynchronizedOperation()) {
        MelonDSAndroid::reset();
        endSynchronizedOperation();
    }
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_saveStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    if (!beginSynchronizedOperation()) {
        if (path != nullptr) {
            env->ReleaseStringUTFChars(path, saveStatePath);
        }
        return JNI_FALSE;
    }

    bool result = MelonDSAndroid::saveState(saveStatePath);
    endSynchronizedOperation();
    if (path != nullptr) {
        env->ReleaseStringUTFChars(path, saveStatePath);
    }
    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadStateInternal(JNIEnv* env, jobject thiz, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    if (!beginSynchronizedOperation()) {
        if (path != nullptr) {
            env->ReleaseStringUTFChars(path, saveStatePath);
        }
        return JNI_FALSE;
    }

    bool result = MelonDSAndroid::loadState(saveStatePath);
    endSynchronizedOperation();
    if (path != nullptr) {
        env->ReleaseStringUTFChars(path, saveStatePath);
    }
    return result;
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRewindState(JNIEnv* env, jobject thiz, jobject rewindSaveState) {
    if (!beginSynchronizedOperation()) {
        return JNI_FALSE;
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

    melonDS::RewindSaveState state = melonDS::RewindSaveState {
        .buffer = (u8*) env->GetDirectBufferAddress(buffer),
        .bufferSize = (u32) env->GetDirectBufferCapacity(buffer),
        .bufferContentSize = (u32) bufferContentSize,
        .screenshot = (u8*) env->GetDirectBufferAddress(screenshotBuffer),
        .screenshotSize = (u32) env->GetDirectBufferCapacity(screenshotBuffer),
        .frame = frame
    };

    bool result = MelonDSAndroid::loadRewindState(state);
    endSynchronizedOperation();
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
    pthread_mutex_lock(&coreOperationMutex);
    pthread_mutex_lock(&emuThreadMutex);
    bool shouldJoin = emuThreadJoinable;
    if (shouldJoin) {
        stopRequested = true;
        userPauseRequested = false;
        emuThreadState = EmulatorThreadState::Stopping;
        pthread_cond_broadcast(&emuThreadCond);
    }
    pthread_mutex_unlock(&emuThreadMutex);

    if (shouldJoin) {
        pthread_join(emuThread, NULL);

        pthread_mutex_lock(&emuThreadMutex);
        emuThreadJoinable = false;
        emuThreadState = EmulatorThreadState::Stopped;
        pthread_mutex_unlock(&emuThreadMutex);
    }
    pthread_mutex_unlock(&coreOperationMutex);

    MelonDSAndroid::cleanup();

    if (globalCameraManager != nullptr) {
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
    if (key != 16 + 7) {
        MelonDSAndroid::pressKey(key);
    } else if (beginSynchronizedOperation()) {
        MelonDSAndroid::pressKey(key);
        endSynchronizedOperation();
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onKeyRelease(JNIEnv* env, jobject thiz, jint key)
{
    if (key != 16 + 7) {
        MelonDSAndroid::releaseKey(key);
    } else if (beginSynchronizedOperation()) {
        MelonDSAndroid::releaseKey(key);
        endSynchronizedOperation();
    }
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_updateMotionData(JNIEnv* env, jobject thiz, jfloat ax, jfloat ay, jfloat az, jfloat rx, jfloat ry, jfloat rz)
{
    MelonDSAndroid::updateMotionData(ax, ay, az, rx, ry, rz);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_getEmulationStatusInternal(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&emuThreadMutex);
    EmulationStatus status;
    if (emuThreadState == EmulatorThreadState::StartFailed) {
        status = EmulationStatus::StartFailed;
    } else if (emuThreadState == EmulatorThreadState::NotStarted) {
        status = EmulationStatus::NotStarted;
    } else if (emuThreadState == EmulatorThreadState::Starting) {
        status = EmulationStatus::Starting;
    } else if (emuThreadState == EmulatorThreadState::Stopping) {
        status = EmulationStatus::Stopping;
    } else if (emuThreadState == EmulatorThreadState::Stopped) {
        status = EmulationStatus::Stopped;
    } else if (emulatorAtSafePoint && isPauseRequested()) {
        status = EmulationStatus::Paused;
    } else if (isPauseRequested()) {
        status = EmulationStatus::PauseRequested;
    } else {
        status = EmulationStatus::Running;
    }
    pthread_mutex_unlock(&emuThreadMutex);
    return static_cast<jint>(status);
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

    MelonDSAndroid::updateEmulatorConfiguration(std::make_unique<MelonDSAndroid::EmulatorConfiguration>(std::move(newConfiguration)));

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

    pthread_mutex_lock(&emuThreadMutex);
    if (emuThreadState == EmulatorThreadState::Starting) {
        emuThreadState = stopRequested ? EmulatorThreadState::Stopping : EmulatorThreadState::Running;
    }
    pthread_cond_broadcast(&emuThreadCond);
    pthread_mutex_unlock(&emuThreadMutex);

    auto manager = PerformanceHintManagerFactory::create(jniEnvHandler);
    performanceHintSession = new ThreadSafePerformanceHintSession(std::move(manager));
    if (performanceHintSession != nullptr) {
        performanceHintSession->createSession(gettid(), FRAME_DURATION_60FPS_NS);
    }

    for (;;)
    {
        pthread_mutex_lock(&emuThreadMutex);
        if (isPauseRequested()) {
            emulatorAtSafePoint = true;
            pthread_cond_broadcast(&emuThreadCond);
            while (isPauseRequested() && !stopRequested)
                pthread_cond_wait(&emuThreadCond, &emuThreadMutex);

            frameLimitError = 0;
            lastTick = getCurrentMillis();
            emulatorAtSafePoint = false;
            pthread_cond_broadcast(&emuThreadCond);
        }

        if (stopRequested) {
            emulatorAtSafePoint = false;
            pthread_cond_broadcast(&emuThreadCond);
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

    pthread_mutex_lock(&emuThreadMutex);
    emulatorAtSafePoint = false;
    pthread_cond_broadcast(&emuThreadCond);
    pthread_mutex_unlock(&emuThreadMutex);

    if (performanceHintSession != nullptr) {
        performanceHintSession->destroySession();

        delete performanceHintSession;
        performanceHintSession = nullptr;
    }

    MelonDSAndroid::stop();

    pthread_mutex_lock(&emuThreadMutex);
    if (emuThreadState != EmulatorThreadState::Stopping) {
        emuThreadState = EmulatorThreadState::Stopped;
    }
    pthread_cond_broadcast(&emuThreadCond);
    pthread_mutex_unlock(&emuThreadMutex);
    return nullptr;
}
