#include "JniPerformanceHintManager.h"
#include <android/log.h>
#include <Platform.h>

JniPerformanceHintManager::JniPerformanceHintManager(JniEnvHandler* jniEnvHandler) : jniEnvHandler(jniEnvHandler)
{
    hintSession = nullptr;
    reportMethod = nullptr;
    updateTargetMethod = nullptr;
    closeMethod = nullptr;
}

void JniPerformanceHintManager::createSession(pid_t threadId, int64_t targetDurationNs)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (env == nullptr)
        return;

    // Get application context via ActivityThread.currentApplication()
    jclass activityThreadClass = env->FindClass("android/app/ActivityThread");
    if (activityThreadClass == nullptr)
    {
        env->ExceptionClear();
        return;
    }

    jmethodID currentAppMethod = env->GetStaticMethodID(activityThreadClass, "currentApplication", "()Landroid/app/Application;");
    if (currentAppMethod == nullptr)
    {
        env->ExceptionClear();
        env->DeleteLocalRef(activityThreadClass);
        return;
    }

    jobject context = env->CallStaticObjectMethod(activityThreadClass, currentAppMethod);
    env->DeleteLocalRef(activityThreadClass);
    if (context == nullptr)
    {
        env->ExceptionClear();
        return;
    }

    jclass contextClass = env->FindClass("android/content/Context");
    if (contextClass == nullptr)
    {
        env->ExceptionClear();
        env->DeleteLocalRef(context);
        return;
    }

    jmethodID getSystemServiceMethod = env->GetMethodID(contextClass, "getSystemService", "(Ljava/lang/String;)Ljava/lang/Object;");
    env->DeleteLocalRef(contextClass);
    if (getSystemServiceMethod == nullptr)
    {
        env->ExceptionClear();
        env->DeleteLocalRef(context);
        return;
    }

    jstring serviceName = env->NewStringUTF("performance_hint");
    jobject manager = env->CallObjectMethod(context, getSystemServiceMethod, serviceName);
    env->DeleteLocalRef(serviceName);
    env->DeleteLocalRef(context);
    if (manager == nullptr)
    {
        env->ExceptionClear();
        return;
    }

    jintArray tidsArray = env->NewIntArray(1);
    jint tid = static_cast<jint>(threadId);
    env->SetIntArrayRegion(tidsArray, 0, 1, &tid);

    jclass managerClass = env->FindClass("android/os/PerformanceHintManager");
    jmethodID createSessionMethod = env->GetMethodID(managerClass, "createHintSession", "([IJ)Landroid/os/PerformanceHintManager$Session;");
    env->DeleteLocalRef(managerClass);
    if (createSessionMethod == nullptr)
    {
        env->ExceptionClear();
        env->DeleteLocalRef(tidsArray);
        env->DeleteLocalRef(manager);
        return;
    }

    jobject session = env->CallObjectMethod(manager, createSessionMethod, tidsArray, static_cast<jlong>(targetDurationNs));
    env->DeleteLocalRef(tidsArray);
    env->DeleteLocalRef(manager);
    if (session == nullptr)
    {
        env->ExceptionClear();
        return;
    }

    hintSession = env->NewGlobalRef(session);
    env->DeleteLocalRef(session);

    jclass sessionClass = env->GetObjectClass(hintSession);
    reportMethod = env->GetMethodID(sessionClass, "reportActualWorkDuration", "(J)V");
    updateTargetMethod = env->GetMethodID(sessionClass, "updateTargetWorkDuration", "(J)V");
    closeMethod = env->GetMethodID(sessionClass, "close", "()V");
    env->DeleteLocalRef(sessionClass);

    if (reportMethod == nullptr || updateTargetMethod == nullptr || closeMethod == nullptr)
    {
        env->ExceptionClear();
        melonDS::Platform::Log(melonDS::Platform::LogLevel::Error, "Failed to cache session method IDs");
        env->DeleteGlobalRef(hintSession);
        hintSession = nullptr;
        reportMethod = nullptr;
        updateTargetMethod = nullptr;
        closeMethod = nullptr;
    }
}

void JniPerformanceHintManager::reportActualWorkDuration(int64_t actualDurationNs)
{
    if (hintSession == nullptr)
        return;

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (env == nullptr)
        return;

    env->CallVoidMethod(hintSession, reportMethod, static_cast<jlong>(actualDurationNs));
}

void JniPerformanceHintManager::updateTargetWorkDuration(int64_t targetDurationNs)
{
    if (hintSession == nullptr)
        return;

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (env == nullptr)
        return;

    env->CallVoidMethod(hintSession, updateTargetMethod, static_cast<jlong>(targetDurationNs));
}

void JniPerformanceHintManager::destroySession()
{
    if (hintSession == nullptr)
        return;

    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (env == nullptr)
        return;

    env->CallVoidMethod(hintSession, closeMethod);
    env->DeleteGlobalRef(hintSession);
    hintSession = nullptr;
    reportMethod = nullptr;
    updateTargetMethod = nullptr;
    closeMethod = nullptr;
}
