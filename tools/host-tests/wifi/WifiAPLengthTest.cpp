/*
 * Exercising the production WifiAP.cpp parser with a deterministic guest frame.
 * The small platform functions only capture the packet sent to Android's network adapter.
 */
#include "Wifi.h"
#include "WifiAP.h"
#include "Platform.h"

#include <array>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace melonDS
{
bool MACEqual(const u8* a, const u8* b) { return memcmp(a, b, 6) == 0; }
bool MACIsBroadcast(const u8* a)
{
    const u8 broadcast[6] = {0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF};
    return MACEqual(a, broadcast);
}
const u8* Wifi::GetMAC() const
{
    static const u8 mac[6] = {0x02, 0, 0, 0, 0, 1};
    return mac;
}
}

namespace
{
int sentLength = -1;
std::array<melonDS::u8, 2048> sentData {};
}

namespace melonDS::Platform
{
void Log(LogLevel, const char*, ...) {}
int Net_RecvPacket(u8*, void*) { return 0; }
int Net_SendPacket(u8* data, int len, void*)
{
    sentLength = len;
    if (len >= 0 && static_cast<size_t>(len) <= sentData.size())
        memcpy(sentData.data(), data, len);
    return len;
}
}

static std::vector<melonDS::u8> ManagementFrame(melonDS::u8 frameControl)
{
    std::vector<melonDS::u8> frame(12 + 24, 0);
    frame[9] = melonDS::WifiAP::APChannel;
    frame[12] = frameControl;
    memcpy(frame.data() + 12 + 16, melonDS::WifiAP::APMac, 6);
    frame[12 + 10] = 0x02;
    return frame;
}

static bool Associate(melonDS::WifiAP& ap)
{
    std::array<melonDS::u8, 2048> reply {};
    auto auth = ManagementFrame(0xB0);
    if (ap.SendPacket(auth.data(), auth.size()) != static_cast<int>(auth.size())) return false;
    if (!ap.RecvPacket(reply.data())) return false;
    auto assoc = ManagementFrame(0x00);
    if (ap.SendPacket(assoc.data(), assoc.size()) != static_cast<int>(assoc.size())) return false;
    return ap.RecvPacket(reply.data()) != 0;
}

static std::vector<melonDS::u8> DataFrame(size_t payloadSize)
{
    std::vector<melonDS::u8> frame(12 + 24 + 8 + payloadSize + 4, 0);
    frame[9] = melonDS::WifiAP::APChannel;
    auto* wlan = frame.data() + 12;
    wlan[0] = 0x08;
    wlan[1] = 0x01; // data frame, ToDS
    wlan[10] = 0x02;
    wlan[16] = 0x04;
    wlan[24] = 0xAA;
    wlan[25] = 0xAA;
    wlan[26] = 0x03;
    wlan[30] = 0x08;
    wlan[31] = 0x06;
    for (size_t i = 0; i < payloadSize; ++i) wlan[32 + i] = static_cast<melonDS::u8>(0x40 + (i & 0x3F));
    return frame;
}

int main(int argc, char** argv)
{
    if (argc != 2) return 2;
    const std::string mode = argv[1];
    melonDS::WifiAP ap(nullptr, nullptr);
    ap.Reset();
    if (mode != "unassociated" && !Associate(ap)) return 3;

    const size_t payloadSize = mode == "oversized" ? 2035 :
        mode == "max-payload" ? 2034 : mode == "zero-payload" ? 0 : 3;
    auto frame = DataFrame(payloadSize);
    const size_t logicalLength = mode == "tiny" ? 11 :
        mode == "short" ? 44 : mode == "missing-fcs" ? 47 : frame.size();
    // Wifi::TXSendFrame uses a fixed 8192-byte TXBuffer even when the guest
    // advertises a shorter frame. Preserve that backing capacity in this test.
    frame.resize(8192, 0xCD);
    if (mode == "wrong-channel") frame[9] = 1;
    if (mode == "bad-llc") frame[12 + 24] = 0;
    ap.SendPacket(frame.data(), logicalLength);

    if (mode == "valid" || mode == "zero-payload" || mode == "max-payload")
    {
        const auto* wlan = frame.data() + 12;
        if (sentLength != static_cast<int>(14 + payloadSize) ||
            memcmp(sentData.data(), wlan + 16, 6) ||
            memcmp(sentData.data() + 6, wlan + 10, 6) ||
            memcmp(sentData.data() + 12, wlan + 30, 2) ||
            memcmp(sentData.data() + 14, wlan + 32, payloadSize))
            return 4;
    }
    else if (sentLength != -1) return 5;

    printf("%s: PASS\n", mode.c_str());
    return 0;
}
