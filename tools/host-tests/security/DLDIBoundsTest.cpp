#include <cstdio>
#include "DLDIBounds.h"

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
    using melonDS::DLDIBounds;
    Expect(DLDIBounds::SearchProbeFits(0, 12), "exact DLDI marker probe fits");
    Expect(!DLDIBounds::SearchProbeFits(1, 12), "DLDI marker probe cannot read past section end");
    Expect(DLDIBounds::PatchFits(0x100, 0x80, 0x80, 0x80, 0x200), "bounded DLDI replacement fits");
    Expect(!DLDIBounds::PatchFits(0x1C0, 0x80, 0x80, 0x80, 0x200), "DLDI capacity beyond ROM is rejected");
    Expect(!DLDIBounds::PatchFits(0x100, 0x80, 0x81, 0x80, 0x200), "DLDI initialized bytes beyond declared size are rejected");
    Expect(DLDIBounds::PatchFits(0x100, 0x400, 548, 0x400, 0x800), "trimmed builtin DLDI fits its declared footprint");
    Expect(!DLDIBounds::PatchFits(0x100, 0x200, 548, 0x400, 0x800), "declared DLDI footprint beyond reservation is rejected");
    Expect(DLDIBounds::FixRangeFits(0, 0x80, 0x80), "DLDI relocation range fits patch bytes");
    Expect(!DLDIBounds::FixRangeFits(4, 0x84, 0x80), "DLDI relocation range beyond patch is rejected");
    return Failures == 0 ? 0 : 1;
}
