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
}

void MelonDSAndroidCameraHandler::stopCamera(int camera)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();

    jclass cameraManagerClass = env->GetObjectClass(cameraManager);
    jmethodID stopCameraMethod = env->GetMethodID(cameraManagerClass, "stopCamera", "(I)V");
    env->CallVoidMethod(cameraManager, stopCameraMethod, camera);
}

void MelonDSAndroidCameraHandler::captureFrame(int camera, u32* frameBuffer, int width, int height, bool isYuv)
{
    JNIEnv* env = jniEnvHandler->getCurrentThreadEnv();
    jbyteArray javaBuffer = env->NewByteArray(BUFFER_SIZE);
    jclass cameraManagerClass = env->GetObjectClass(cameraManager);
    jmethodID captureFrameMethod = env->GetMethodID(cameraManagerClass, "captureFrame", "(I[BIIZ)V");
    env->CallVoidMethod(cameraManager, captureFrameMethod, camera, javaBuffer, width, height, isYuv);

    env->GetByteArrayRegion(javaBuffer, 0, BUFFER_SIZE, (jbyte*) frameBuffer);

    env->DeleteLocalRef(javaBuffer);
}

MelonDSAndroidCameraHandler::~MelonDSAndroidCameraHandler()
{
}