#ifndef MELONDSANDROIDCAMERAHANDLER_H
#define MELONDSANDROIDCAMERAHANDLER_H

#include <jni.h>
#include <AndroidCameraHandler.h>
#include "JniEnvHandler.h"

class MelonDSAndroidCameraHandler : public MelonDSAndroid::AndroidCameraHandler {
private:
    const int BUFFER_SIZE = 640 * 480 * 2;

    JniEnvHandler* jniEnvHandler;
    jobject cameraManager;

public:
    MelonDSAndroidCameraHandler(JniEnvHandler* jniEnvHandler, jobject cameraManager);
    void startCamera(int camera);
    void stopCamera(int camera);
    void captureFrame(int camera, u32* frameBuffer, int width, int height, bool isYuv);
    virtual ~MelonDSAndroidCameraHandler();
};

#endif //MELONDSANDROIDCAMERAHANDLER_H
