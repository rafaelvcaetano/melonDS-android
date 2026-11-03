#include <cstdio>
#include "DSiSaveGeometry.h"

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
    melonDS::DSi_NAND::SaveGeometry geometry{};
    Expect(melonDS::DSi_NAND::CalculateSaveGeometry(0x200, geometry), "minimum FAT save geometry is accepted");
    Expect(geometry.totalSectors > 0, "minimum geometry has forward progress");
    Expect(melonDS::DSi_NAND::CalculateSaveGeometry(0x01FFFE00, geometry), "65535-sector geometry terminates and is accepted");
    Expect(geometry.totalSectors <= 0xFFFF, "serialized FAT16 sector count stays representable");
    Expect(!melonDS::DSi_NAND::CalculateSaveGeometry(0x02000000, geometry), "unrepresentable 65536-sector geometry is rejected");
    Expect(!melonDS::DSi_NAND::CalculateSaveGeometry(0x1FF, geometry), "sub-sector save is rejected");
    return Failures == 0 ? 0 : 1;
}
