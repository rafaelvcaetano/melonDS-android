#include <array>
#include <cstdio>

#include "WifiMemory.h"

namespace {
int Failures = 0;
void Expect(bool condition, const char* message)
{
    if (condition) std::printf("PASS: %s\n", message);
    else { std::fprintf(stderr, "FAIL: %s\n", message); ++Failures; }
}
}

int main()
{
    std::array<melonDS::u8, 24> bytes {};
    melonDS::WifiMemory::Write32(bytes.data() + 2, 0x22334466);
    Expect(melonDS::WifiMemory::Read32(bytes.data() + 2) == 0x22334466,
        "32-bit Wi-Fi FCS survives a two-byte-aligned address under UBSan");

    melonDS::WifiMemory::Write64(bytes.data() + 4, 0x1122334455667788ULL);
    Expect(melonDS::WifiMemory::Read64(bytes.data() + 4) == 0x1122334455667788ULL,
        "64-bit Wi-Fi timestamp survives a four-byte-aligned address under UBSan");
    return Failures == 0 ? 0 : 1;
}
