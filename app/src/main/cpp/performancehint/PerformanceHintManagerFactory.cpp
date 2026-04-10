#include "PerformanceHintManagerFactory.h"

#include <android/api-level.h>
#include "NdkPerformanceHintManager.h"
#include "JniPerformanceHintManager.h"
#include "DummyPerformanceHintManager.h"

std::unique_ptr<PerformanceHintManager> PerformanceHintManagerFactory::create(JniEnvHandler* jniEnvHandler)
{
    return create(jniEnvHandler, android_get_device_api_level());
}

std::unique_ptr<PerformanceHintManager> PerformanceHintManagerFactory::create(JniEnvHandler* jniEnvHandler, int apiLevel)
{
    if (apiLevel >= 33)
        return std::make_unique<NdkPerformanceHintManager>();
    else if (apiLevel >= 31)
        return std::make_unique<JniPerformanceHintManager>(jniEnvHandler);
    else
        return std::make_unique<DummyPerformanceHintManager>();
}
