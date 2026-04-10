#ifndef MELONDS_ANDROID_JNIPERFORMANCEHINTMANAGER_H
#define MELONDS_ANDROID_JNIPERFORMANCEHINTMANAGER_H

#include <jni.h>
#include "PerformanceHintManager.h"
#include "../JniEnvHandler.h"

class JniPerformanceHintManager : public PerformanceHintManager
{
public:
    explicit JniPerformanceHintManager(JniEnvHandler* jniEnvHandler);
    void createSession(pid_t threadId, int64_t targetDurationNs) override;
    void destroySession() override;
    void reportActualWorkDuration(int64_t actualDurationNs) override;
    void updateTargetWorkDuration(int64_t targetDurationNs) override;

private:
    JniEnvHandler* jniEnvHandler;
    jobject hintSession;
    jmethodID reportMethod;
    jmethodID updateTargetMethod;
    jmethodID closeMethod;
};

#endif
