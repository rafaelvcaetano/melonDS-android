#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <cstdlib>
#include <MelonDS.h>
#include <InputAndroid.h>
#include <android/asset_manager_jni.h>

void* emulate(void*);

pthread_t emuThread;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;

bool stop;
bool paused;
int observedFrames = 0;
int fps = 0;
bool limitFps = true;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupEmulator(JNIEnv* env, jclass type, jstring configDir, jobject javaAssetManager)
{
    const char* dir = env->GetStringUTFChars(configDir, JNI_FALSE);

    jobject globalAssetManager = env->NewGlobalRef(javaAssetManager);
    AAssetManager* assetManager = AAssetManager_fromJava(env, globalAssetManager);

    MelonDSAndroid::setup(const_cast<char *>(dir), assetManager);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRomInternal(JNIEnv* env, jclass type, jstring romPath, jstring sramPath, jboolean loadDirect, jboolean loadGbaRom, jstring gbaRomPath, jstring gbaSramPath)
{
    const char* rom = romPath == nullptr ? nullptr : env->GetStringUTFChars(romPath, JNI_FALSE);
    const char* sram = sramPath == nullptr ? nullptr : env->GetStringUTFChars(sramPath, JNI_FALSE);
    const char* gbaRom = gbaRomPath == nullptr ? nullptr : env->GetStringUTFChars(gbaRomPath, JNI_FALSE);
    const char* gbaSram = gbaSramPath == nullptr ? nullptr : env->GetStringUTFChars(gbaSramPath, JNI_FALSE);

    return MelonDSAndroid::loadRom(const_cast<char*>(rom), const_cast<char*>(sram), loadDirect, loadGbaRom, const_cast<char*>(gbaRom), const_cast<char*>(gbaSram));
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_startEmulation( JNIEnv* env, jclass type)
{
    stop = false;
    paused = false;
    limitFps = true;

    pthread_mutex_init(&emuThreadMutex, NULL);
    pthread_cond_init(&emuThreadCond, NULL);
    pthread_create(&emuThread, NULL, emulate, NULL);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_copyFrameBuffer( JNIEnv* env, jclass type, jobject frameBuffer)
{
    void* buf = env->GetDirectBufferAddress(frameBuffer);
    MelonDSAndroid::copyFrameBuffer(buf);
}

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_MelonEmulator_getFPS( JNIEnv* env, jclass type)
{
    return fps;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_pauseEmulation( JNIEnv* env, jclass type)
{
    pthread_mutex_lock(&emuThreadMutex);
    if (!stop)
        paused = true;
    pthread_mutex_unlock(&emuThreadMutex);

    MelonDSAndroid::pause();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_resumeEmulation( JNIEnv* env, jclass type)
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
Java_me_magnum_melonds_MelonEmulator_saveState( JNIEnv* env, jclass type, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::saveState(saveStatePath);
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadState( JNIEnv* env, jclass type, jstring path)
{
    const char* saveStatePath = path == nullptr ? nullptr : env->GetStringUTFChars(path, JNI_FALSE);
    return MelonDSAndroid::loadState(saveStatePath);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_stopEmulation( JNIEnv* env, jclass type)
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
Java_me_magnum_melonds_MelonEmulator_onScreenTouch( JNIEnv* env, jclass type, jint x, jint y)
{
    MelonDSAndroid::touchScreen(x, y);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onScreenRelease( JNIEnv* env, jclass type)
{
    MelonDSAndroid::releaseScreen();
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onKeyPress( JNIEnv* env, jclass type, jint key)
{
    MelonDSAndroid::pressKey(key);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_onKeyRelease( JNIEnv* env, jclass type, jint key)
{
    MelonDSAndroid::releaseKey(key);
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setFastForwardEnabled( JNIEnv* env, jclass type, jboolean enabled)
{
    limitFps = !enabled;
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
    int fpsLimitCount = 0;

    MelonDSAndroid::start();

    for (;;)
    {
        pthread_mutex_lock(&emuThreadMutex);
        while (paused && !stop)
            pthread_cond_wait(&emuThreadCond, &emuThreadMutex);

        if (stop) {
            pthread_mutex_unlock(&emuThreadMutex);
            break;
        }

        pthread_mutex_unlock(&emuThreadMutex);

        u32 nLines = MelonDSAndroid::loop();

        float frameRate = (1000.0f * nLines) / (60.0f * 263.0f);

        double currentTick = getCurrentMillis();
        double delay = currentTick - lastTick;

        if (limitFps)
        {
            double wantedTickF = startTick + (frameRate * (fpsLimitCount + 1));
            u64 wantedTick = (u64) ceil(wantedTickF);
            if (currentTick < wantedTick)
                usleep((wantedTick - currentTick) * 1000);

            lastTick = getCurrentMillis();
            fpsLimitCount++;
            if ((abs(wantedTickF - (float) wantedTick) < 0.001312) || fpsLimitCount > 60)
            {
                fpsLimitCount = 0;
                startTick = lastTick;
            }
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