#include "AndroidMelonEventMessenger.h"
#include "EmulatorMessageQueueJNI.h"

void AndroidMelonEventMessenger::onRumbleStart(int durationMs)
{
    MelonDSAndroid::fireEmulatorEvent(EVENT_RUMBLE_START, sizeof(durationMs), &durationMs);
}

void AndroidMelonEventMessenger::onRumbleStop()
{
    MelonDSAndroid::fireEmulatorEvent(EVENT_RUMBLE_STOP);
}

void AndroidMelonEventMessenger::onEmulatorStop(melonDS::Platform::StopReason reason)
{
    int32_t reasonInt = (int32_t) reason;
    MelonDSAndroid::fireEmulatorEvent(EVENT_EMULATOR_STOP, sizeof(reasonInt), &reasonInt);
}
