#ifndef ANDROIDMELONEVENTMESSENGER_H
#define ANDROIDMELONEVENTMESSENGER_H

#include <MelonEventMessenger.h>

class AndroidMelonEventMessenger : public MelonDSAndroid::MelonEventMessenger
{
public:
    void onRumbleStart(int durationMs) override;
    void onRumbleStop() override;
    void onEmulatorStop(melonDS::Platform::StopReason reason) override;

private:
    // Event type constants
    static constexpr int EVENT_RUMBLE_START = 100;
    static constexpr int EVENT_RUMBLE_STOP = 101;
    static constexpr int EVENT_EMULATOR_STOP = 102;
};

#endif // ANDROIDMELONEVENTMESSENGER_H
