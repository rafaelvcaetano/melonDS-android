#ifndef ANDROIDFRAMERENDEREDCALLBACK_H
#define ANDROIDFRAMERENDEREDCALLBACK_H

#include "JniEnvHandler.h"
#include <FrameRenderedCallback.h>
#include <jni.h>

class AndroidFrameRenderedCallback : public FrameRenderedCallback
{
private:
    JniEnvHandler* jniEnvHandler;
    jobject androidFrameRenderedListener;

public:
    AndroidFrameRenderedCallback(JniEnvHandler* jniEnvHandler, jobject androidFrameRenderedListener);
    void onFrameRendered(long syncFence, int textureId);
};


#endif //ANDROIDFRAMERENDEREDCALLBACK_H
