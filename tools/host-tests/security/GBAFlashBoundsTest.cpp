#include <cstdio>
#include "GBAFlashBounds.h"

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
    using melonDS::GBAFlashBounds;
    Expect(GBAFlashBounds::BankFits(0, 65536), "bank zero fits 512 Kbit flash");
    Expect(!GBAFlashBounds::BankFits(1, 65536), "bank one is rejected for 512 Kbit flash");
    Expect(GBAFlashBounds::BankFits(1, 128 * 1024), "bank one fits 1 Mbit flash");
    Expect(!GBAFlashBounds::BankFits(2, 128 * 1024 + 16), "RTC tail does not create a third flash bank");
    Expect(GBAFlashBounds::ByteOffset(1, 0xFFFF, 128 * 1024) == 0x1FFFF, "last byte of bank one is bounded");
    Expect(GBAFlashBounds::ByteOffset(2, 0, 128 * 1024) == GBAFlashBounds::InvalidOffset, "invalid bank has no host offset");
    Expect(GBAFlashBounds::SectorOffset(1, 0xFFFF, 128 * 1024) == 0x1F000, "sector erase is aligned to 4 KiB");
    return Failures == 0 ? 0 : 1;
}
