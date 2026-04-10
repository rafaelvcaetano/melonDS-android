#include "ThreadSafePerformanceHintSession.h"

ThreadSafePerformanceHintSession::ThreadSafePerformanceHintSession(std::unique_ptr<PerformanceHintManager> manager) : manager(std::move(manager))
{
}

void ThreadSafePerformanceHintSession::createSession(pid_t threadId, int64_t targetDurationNs)
{
    std::lock_guard<std::mutex> lock(sessionMutex);
    manager->createSession(threadId, targetDurationNs);
    sessionActive = true;
}

void ThreadSafePerformanceHintSession::reportActualWorkDuration(int64_t actualDurationNs)
{
    std::lock_guard<std::mutex> lock(sessionMutex);
    if (!sessionActive)
        return;

    manager->reportActualWorkDuration(actualDurationNs);
}

void ThreadSafePerformanceHintSession::updateTargetWorkDuration(int64_t targetDurationNs)
{
    std::lock_guard<std::mutex> lock(sessionMutex);
    if (!sessionActive)
        return;

    manager->updateTargetWorkDuration(targetDurationNs);
}

void ThreadSafePerformanceHintSession::destroySession()
{
    std::lock_guard<std::mutex> lock(sessionMutex);
    manager->destroySession();
    sessionActive = false;
}
