#include <jni.h>
#include <pthread.h>
#include <MelonDS.h>
#include <InputAndroid.h>

void* emulate(void*);

pthread_t emuThread;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;

bool stop;
bool paused;

extern "C"
{
JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_setupEmulator(JNIEnv* env, jclass type, jstring configDir)
{
    const char* dir = env->GetStringUTFChars(configDir, JNI_FALSE);
    MelonDSAndroid::setup(const_cast<char *>(dir));
}

JNIEXPORT jboolean JNICALL
Java_me_magnum_melonds_MelonEmulator_loadRom(JNIEnv* env, jclass type, jstring romPath, jstring sramPath, jboolean loadDirect)
{
    const char* rom = env->GetStringUTFChars(romPath, JNI_FALSE);
    const char* sram = env->GetStringUTFChars(sramPath, JNI_FALSE);

    return MelonDSAndroid::loadRom(const_cast<char*>(rom), const_cast<char*>(sram), loadDirect) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_MelonEmulator_startEmulation( JNIEnv* env, jclass type)
{
    stop = false;
    paused = false;

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
    return MelonDSAndroid::getFPS();
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
}

void* emulate(void*)
{
    struct timespec now;
    double nowMillis;
    clock_gettime(CLOCK_REALTIME, &now);

    nowMillis = (now.tv_sec * 1000.0) + now.tv_nsec / 1000000.0;
    MelonDSAndroid::start(nowMillis);

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

        clock_gettime(CLOCK_REALTIME, &now);
        nowMillis = (now.tv_sec * 1000.0) + now.tv_nsec / 1000000.0;
        MelonDSAndroid::loop(nowMillis);
    }

    MelonDSAndroid::cleanup();
    pthread_exit(NULL);
}