#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <initializer_list>
#include <pthread.h>

using JNIEnv = void;
using jobject = void*;
#define JNIEXPORT
#define JNICALL

bool started = false;
bool stop = false;
bool paused = true;
pthread_mutex_t emuThreadMutex;
pthread_cond_t emuThreadCond;
static int resumeCalls;

static void require(bool condition, const char* message)
{
    if (!condition) {
        std::fprintf(stderr, "FAIL: %s\n", message);
        std::exit(1);
    }
}

namespace MelonDSAndroid {
// This boundary observes dispatch and lock ownership; RTC behavior has separate real-core tests.
void resume()
{
    ++resumeCalls;
    if (started) {
        require(pthread_mutex_trylock(&emuThreadMutex) == EBUSY,
                "resume callback must run while the emulator mutex is held");
    }
}
}

#include "ResumeDispatchFunction.h"

static void check(bool threadStarted, bool stopped, bool initiallyPaused)
{
    started = threadStarted;
    stop = stopped;
    paused = initiallyPaused;
    resumeCalls = 0;
    if (started) {
        require(pthread_mutex_init(&emuThreadMutex, nullptr) == 0, "mutex initialization");
        require(pthread_cond_init(&emuThreadCond, nullptr) == 0, "condition initialization");
    }

    Java_me_magnum_melonds_MelonEmulator_resumeEmulation(nullptr, nullptr);

    require(resumeCalls == (stopped ? 0 : 1), "stopped sessions must not dispatch resume");
    require(paused == (stopped ? initiallyPaused : false), "pause state must follow the stop guard");
    if (started) {
        require(pthread_mutex_trylock(&emuThreadMutex) == 0, "resume must release the mutex");
        pthread_mutex_unlock(&emuThreadMutex);
        require(pthread_cond_destroy(&emuThreadCond) == 0, "condition destruction");
        require(pthread_mutex_destroy(&emuThreadMutex) == 0, "mutex destruction");
    }
}

int main()
{
    for (bool threadStarted : {false, true})
        for (bool stopped : {false, true})
            for (bool initiallyPaused : {false, true})
                check(threadStarted, stopped, initiallyPaused);
    std::puts("Resume dispatch: 8 cases passed");
}
