#ifndef MELONDS_ANDROID_PERFORMANCEHINTMANAGERFACTORY_H
#define MELONDS_ANDROID_PERFORMANCEHINTMANAGERFACTORY_H

#include <memory>
#include <jni.h>
#include "PerformanceHintManager.h"
#include "../JniEnvHandler.h"

class PerformanceHintManagerFactory
{
public:
    static std::unique_ptr<PerformanceHintManager> create(JniEnvHandler* jniEnvHandler);
    static std::unique_ptr<PerformanceHintManager> create(JniEnvHandler* jniEnvHandler, int apiLevel);
};

#endif
