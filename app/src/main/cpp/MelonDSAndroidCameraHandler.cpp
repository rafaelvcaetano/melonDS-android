#include "MelonDSAndroidCameraHandler.h"

MelonDSAndroidCameraHandler::MelonDSAndroidCameraHandler(JniEnvHandler* jniEnvHandler, jobject cameraManager) : jniEnvHandler(jniEnvHandler), cameraManager(cameraManager)
{
}

void MelonDSAndroidCameraHandler::startCamera(int camera)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();

    jclass cameraManagerClass = env->GetObjectClass(cameraManager);
    jmethodID startCameraMethod = env->GetMethodID(cameraManagerClass, "startCamera", "(I)V");
    env->CallVoidMethod(cameraManager, startCameraMethod, camera);
    env->DeleteLocalRef(cameraManagerClass);
}

void MelonDSAndroidCameraHandler::stopCamera(int camera)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();

    jclass cameraManagerClass = env->GetObjectClass(cameraManager);
    jmethodID stopCameraMethod = env->GetMethodID(cameraManagerClass, "stopCamera", "(I)V");
    env->CallVoidMethod(cameraManager, stopCameraMethod, camera);
    env->DeleteLocalRef(cameraManagerClass);
}

void MelonDSAndroidCameraHandler::captureFrame(int camera, u32* frameBuffer, int width, int height, bool isYuv)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    if (!env || !frameBuffer)
        return;

    jbyteArray javaBuffer = env->NewByteArray(BUFFER_SIZE);
    if (!javaBuffer || env->ExceptionCheck())
        return;

    jclass cameraManagerClass = env->GetObjectClass(cameraManager);
    if (!cameraManagerClass || env->ExceptionCheck())
    {
        env->DeleteLocalRef(javaBuffer);
        return;
    }

    jmethodID captureFrameMethod = env->GetMethodID(cameraManagerClass, "captureFrame", "(I[BIIZ)V");
    if (!captureFrameMethod || env->ExceptionCheck())
    {
        env->DeleteLocalRef(cameraManagerClass);
        env->DeleteLocalRef(javaBuffer);
        return;
    }

    env->CallVoidMethod(cameraManager, captureFrameMethod, camera, javaBuffer, width, height, isYuv);
    env->DeleteLocalRef(cameraManagerClass);
    if (!env->ExceptionCheck())
        env->GetByteArrayRegion(javaBuffer, 0, BUFFER_SIZE, (jbyte*) frameBuffer);

    env->DeleteLocalRef(javaBuffer);
}

MelonDSAndroidCameraHandler::~MelonDSAndroidCameraHandler()
{
}
