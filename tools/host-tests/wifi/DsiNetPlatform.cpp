#define Net_SendPacket UnusedHostNetSend
#include "../jit/HostPlatform.cpp"
#undef Net_SendPacket

#include <array>
#include <cstring>

namespace melonDS::Platform
{
int CapturedNetLength = -1;
std::array<u8, 2048> CapturedNetData {};

int Net_SendPacket(u8* data, int len, void*)
{
    CapturedNetLength = len;
    if (len >= 0 && static_cast<size_t>(len) <= CapturedNetData.size())
        memcpy(CapturedNetData.data(), data, len);
    return len;
}
}
