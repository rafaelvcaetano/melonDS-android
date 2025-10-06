#ifndef MELONDSANDROIDRUMBLEMANAGER_H
#define MELONDSANDROIDRUMBLEMANAGER_H

#include <jni.h>
#include <AndroidRumbleManager.h>
#include "JniEnvHandler.h"
#include "types.h"

using namespace melonDS;

class MelonDSAndroidRumbleManager : public MelonDSAndroid::AndroidRumbleManager {
private:
    JniEnvHandler* jniEnvHandler;
    jobject rumbleManager;

public:
    MelonDSAndroidRumbleManager(JniEnvHandler* jniEnvHandler, jobject rumbleManager);
    void startRumble(u32 duration) override;
    void stopRumble() override;
    virtual ~MelonDSAndroidRumbleManager();
};

#endif //MELONDSANDROIDRUMBLEMANAGER_H
