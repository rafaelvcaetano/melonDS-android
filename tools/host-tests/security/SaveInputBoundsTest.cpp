#include <cstdio>
#include <limits>

#include "SaveInputBounds.h"

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
    using melonDS::SaveInputBounds;
    Expect(SaveInputBounds::NDSLengthValid(0), "empty NDS save is valid");
    Expect(SaveInputBounds::NDSLengthValid(32ULL * 1024 * 1024),
        "largest supported NDS sidecar is valid");
    Expect(!SaveInputBounds::NDSLengthValid(32ULL * 1024 * 1024 + 1),
        "oversized NDS sidecar is rejected before allocation");
    Expect(SaveInputBounds::GBALengthValid(0), "empty GBA save is valid");
    Expect(SaveInputBounds::GBALengthValid(512), "GBA EEPROM save is valid");
    Expect(SaveInputBounds::GBALengthValid(131088), "GBA flash plus RTC tail is valid");
    Expect(!SaveInputBounds::GBALengthValid(131089), "unknown GBA sidecar size is rejected");
    Expect(!SaveInputBounds::NDSLengthValid(std::numeric_limits<melonDS::u64>::max()),
        "failed or wrapping length cannot narrow to an allocation");
    return Failures == 0 ? 0 : 1;
}
