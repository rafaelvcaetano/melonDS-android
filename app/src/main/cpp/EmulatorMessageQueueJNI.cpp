#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <Platform.h>

// messagePipes[0] -> read
// messagePipes[1] -> write
static int messagePipes[2] = { -1, -1 };

extern "C"
{

JNIEXPORT jint JNICALL
Java_me_magnum_melonds_impl_emulator_EmulatorMessageQueue_initMessagePipe(JNIEnv* env, jobject thiz)
{
    if (messagePipes[0] != -1) {
        return messagePipes[0];
    }

    if (pipe(messagePipes) == -1) {
        melonDS::Platform::Log(melonDS::Platform::LogLevel::Error, "Failed to create message queue pipes");
        return -1;
    }

    // Make read end non-blocking
    int flags = fcntl(messagePipes[0], F_GETFL, 0);
    fcntl(messagePipes[0], F_SETFL, flags | O_NONBLOCK);

    return messagePipes[0];
}

JNIEXPORT void JNICALL
Java_me_magnum_melonds_impl_emulator_EmulatorMessageQueue_closeMessagePipe(JNIEnv* env, jobject thiz)
{
    if (messagePipes[0] != -1) {
        close(messagePipes[0]);
        messagePipes[0] = -1;
    }
    if (messagePipes[1] != -1) {
        close(messagePipes[1]);
        messagePipes[1] = -1;
    }
}

}

namespace MelonDSAndroid {
    void fireEmulatorEvent(int type, int dataLength, void* data) {
        if (messagePipes[1] == -1) {
            return;
        }

        struct {
            int type;
            int dataLength;
        } event = { type, dataLength };

        write(messagePipes[1], &event, sizeof(event));
        if (data != nullptr)
            write(messagePipes[1], data, dataLength);
    }
}