#ifndef MELONDS_ANDROID_DUMMYPERFORMANCEHINTMANAGER_H
#define MELONDS_ANDROID_DUMMYPERFORMANCEHINTMANAGER_H

#include "PerformanceHintManager.h"

class DummyPerformanceHintManager : public PerformanceHintManager
{
public:
    void createSession(pid_t threadId, int64_t targetDurationNs) override { }
    void destroySession() override { }
    void reportActualWorkDuration(int64_t actualDurationNs) override { }
    void updateTargetWorkDuration(int64_t targetDurationNs) override { }
};

#endif
