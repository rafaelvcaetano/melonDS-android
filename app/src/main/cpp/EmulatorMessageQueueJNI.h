#ifndef MELONDS_ANDROID_MESSAGEQUEUE_JNI_H
#define MELONDS_ANDROID_MESSAGEQUEUE_JNI_H

namespace MelonDSAndroid {
    void fireEmulatorEvent(int type, int dataLength, void* data);
    void fireEmulatorEvent(int type) { fireEmulatorEvent(type, 0, nullptr); };
}

#endif // MELONDS_ANDROID_MESSAGEQUEUE_JNI_H