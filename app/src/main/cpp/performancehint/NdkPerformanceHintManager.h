#ifndef MELONDS_ANDROID_NDKPERFORMANCEHINTMANAGER_H
#define MELONDS_ANDROID_NDKPERFORMANCEHINTMANAGER_H

#include <cstdint>
#include "PerformanceHintManager.h"

typedef void* (*PFN_APerformanceHint_getManager)();
typedef void* (*PFN_APerformanceHint_createSession)(void* manager, const int32_t* threadIds, size_t size, int64_t initialTargetWorkDurationNanos);
typedef void  (*PFN_APerformanceHint_closeSession)(void* session);
typedef int   (*PFN_APerformanceHint_reportActualWorkDuration)(void* session, int64_t actualDurationNanos);
typedef int   (*PFN_APerformanceHint_updateTargetWorkDuration)(void* session, int64_t targetDurationNanos);

class NdkPerformanceHintManager : public PerformanceHintManager
{
public:
    NdkPerformanceHintManager();
    void createSession(pid_t threadId, int64_t targetDurationNs) override;
    void destroySession() override;
    void reportActualWorkDuration(int64_t actualDurationNs) override;
    void updateTargetWorkDuration(int64_t targetDurationNs) override;

private:
    void* manager = nullptr;
    void* session = nullptr;

    PFN_APerformanceHint_getManager fn_getManager = nullptr;
    PFN_APerformanceHint_createSession fn_createSession = nullptr;
    PFN_APerformanceHint_closeSession fn_closeSession = nullptr;
    PFN_APerformanceHint_reportActualWorkDuration fn_reportActualWorkDuration = nullptr;
    PFN_APerformanceHint_updateTargetWorkDuration fn_updateTargetWorkDuration = nullptr;
};

#endif
