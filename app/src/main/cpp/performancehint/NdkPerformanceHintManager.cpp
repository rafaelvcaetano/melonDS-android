#include "NdkPerformanceHintManager.h"
#include <dlfcn.h>

NdkPerformanceHintManager::NdkPerformanceHintManager()
{
    void* handle = dlopen("libandroid.so", RTLD_NOW);
    if (handle == nullptr)
        return;

    fn_getManager = reinterpret_cast<PFN_APerformanceHint_getManager>(dlsym(handle, "APerformanceHint_getManager"));
    fn_createSession = reinterpret_cast<PFN_APerformanceHint_createSession>(dlsym(handle, "APerformanceHint_createSession"));
    fn_closeSession = reinterpret_cast<PFN_APerformanceHint_closeSession>(dlsym(handle, "APerformanceHint_closeSession"));
    fn_reportActualWorkDuration = reinterpret_cast<PFN_APerformanceHint_reportActualWorkDuration>(dlsym(handle, "APerformanceHint_reportActualWorkDuration"));
    fn_updateTargetWorkDuration = reinterpret_cast<PFN_APerformanceHint_updateTargetWorkDuration>(dlsym(handle, "APerformanceHint_updateTargetWorkDuration"));

    // If any symbol is missing, treat the whole API as unavailable
    if (!fn_getManager || !fn_createSession || !fn_closeSession || !fn_reportActualWorkDuration || !fn_updateTargetWorkDuration)
    {
        fn_getManager = nullptr;
        return;
    }

    manager = fn_getManager();
}

void NdkPerformanceHintManager::createSession(pid_t threadId, int64_t targetDurationNs)
{
    if (manager == nullptr)
        return;

    int32_t tids[] = { static_cast<int32_t>(threadId) };
    session = fn_createSession(manager, tids, 1, targetDurationNs);
}

void NdkPerformanceHintManager::reportActualWorkDuration(int64_t actualDurationNs)
{
    if (session == nullptr)
        return;

    fn_reportActualWorkDuration(session, actualDurationNs);
}

void NdkPerformanceHintManager::updateTargetWorkDuration(int64_t targetDurationNs)
{
    if (session == nullptr)
        return;

    fn_updateTargetWorkDuration(session, targetDurationNs);
}

void NdkPerformanceHintManager::destroySession()
{
    if (session != nullptr)
    {
        fn_closeSession(session);
        session = nullptr;
    }
}
