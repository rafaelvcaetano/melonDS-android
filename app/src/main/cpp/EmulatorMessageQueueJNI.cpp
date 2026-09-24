#include <array>
#include <cerrno>
#include <cstring>
#include <limits.h>
#include <signal.h>
#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <pthread.h>
#include <Platform.h>

// messagePipes[0] -> read
// messagePipes[1] -> write
static int messagePipes[2] = { -1, -1 };
static pthread_mutex_t messagePipeMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t messagePipeLifecycleMutex = PTHREAD_MUTEX_INITIALIZER;
static constexpr int MAX_EVENT_DATA_SIZE = 128;

struct EventHeader {
    int type;
    int dataLength;
};

static_assert(sizeof(EventHeader) + MAX_EVENT_DATA_SIZE <= PIPE_BUF);

static bool writeWithoutSigPipe(int fd, const void* data, size_t size)
{
    sigset_t sigpipeSet;
    sigset_t oldSet;
    sigset_t pendingSignals;
    sigemptyset(&sigpipeSet);
    sigaddset(&sigpipeSet, SIGPIPE);

    bool signalMaskChanged = pthread_sigmask(SIG_BLOCK, &sigpipeSet, &oldSet) == 0;
    bool sigpipeWasPending = false;
    if (signalMaskChanged && sigpending(&pendingSignals) == 0) {
        sigpipeWasPending = sigismember(&pendingSignals, SIGPIPE) == 1;
    }

    const auto* bytes = static_cast<const unsigned char*>(data);
    size_t written = 0;
    while (written < size) {
        ssize_t result = write(fd, bytes + written, size - written);
        if (result > 0) {
            written += static_cast<size_t>(result);
            continue;
        }
        if (result == -1 && errno == EINTR) {
            continue;
        }
        break;
    }

    int writeError = written == size ? 0 : errno;
    if (signalMaskChanged && writeError == EPIPE && !sigpipeWasPending) {
        timespec noWait = {};
        while (sigtimedwait(&sigpipeSet, nullptr, &noWait) == -1 && errno == EINTR) {
        }
    }
    if (signalMaskChanged) {
        pthread_sigmask(SIG_SETMASK, &oldSet, nullptr);
    }

    errno = writeError;
    return written == size;
}

extern "C"
{

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_impl_emulator_EmulatorMessageQueue_initMessagePipe(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&messagePipeLifecycleMutex);
    pthread_mutex_lock(&messagePipeMutex);
    if (messagePipes[0] != -1) {
        int readFd = messagePipes[0];
        pthread_mutex_unlock(&messagePipeMutex);
        pthread_mutex_unlock(&messagePipeLifecycleMutex);
        return readFd;
    }

    if (pipe(messagePipes) == -1) {
        melonDS::Platform::Log(melonDS::Platform::LogLevel::Error, "Failed to create message queue pipes");
        pthread_mutex_unlock(&messagePipeMutex);
        pthread_mutex_unlock(&messagePipeLifecycleMutex);
        return -1;
    }

    int readFlags = fcntl(messagePipes[0], F_GETFL, 0);
    if (readFlags == -1 || fcntl(messagePipes[0], F_SETFL, readFlags | O_NONBLOCK) == -1) {
        melonDS::Platform::Log(melonDS::Platform::LogLevel::Error, "Failed to configure message queue pipe");
        close(messagePipes[0]);
        close(messagePipes[1]);
        messagePipes[0] = -1;
        messagePipes[1] = -1;
        pthread_mutex_unlock(&messagePipeMutex);
        pthread_mutex_unlock(&messagePipeLifecycleMutex);
        return -1;
    }

    int readFd = messagePipes[0];
    pthread_mutex_unlock(&messagePipeMutex);
    pthread_mutex_unlock(&messagePipeLifecycleMutex);
    return readFd;
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_impl_emulator_EmulatorMessageQueue_closeMessagePipe(JNIEnv* env, jobject thiz)
{
    pthread_mutex_lock(&messagePipeLifecycleMutex);
    if (messagePipes[0] != -1) {
        close(messagePipes[0]);
        messagePipes[0] = -1;
    }

    pthread_mutex_lock(&messagePipeMutex);
    if (messagePipes[1] != -1) {
        close(messagePipes[1]);
        messagePipes[1] = -1;
    }
    pthread_mutex_unlock(&messagePipeMutex);
    pthread_mutex_unlock(&messagePipeLifecycleMutex);
}

}

namespace MelonDSAndroid {
    void fireEmulatorEvent(int type, int dataLength, void* data) {
        if (dataLength < 0 || dataLength > MAX_EVENT_DATA_SIZE || (dataLength > 0 && data == nullptr)) {
            melonDS::Platform::Log(melonDS::Platform::LogLevel::Error, "Invalid emulator event payload");
            return;
        }

        std::array<unsigned char, sizeof(EventHeader) + MAX_EVENT_DATA_SIZE> frame;
        EventHeader event = { type, dataLength };
        memcpy(frame.data(), &event, sizeof(event));
        if (dataLength > 0) {
            memcpy(frame.data() + sizeof(event), data, static_cast<size_t>(dataLength));
        }

        pthread_mutex_lock(&messagePipeMutex);
        if (messagePipes[1] != -1 &&
            !writeWithoutSigPipe(messagePipes[1], frame.data(), sizeof(event) + static_cast<size_t>(dataLength)) &&
            errno != EAGAIN && errno != EWOULDBLOCK) {
            melonDS::Platform::Log(melonDS::Platform::LogLevel::Warn, "Failed to write emulator event");
        }
        pthread_mutex_unlock(&messagePipeMutex);
    }
}