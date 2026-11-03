#include <cstdio>
#include "FirmwareLayout.h"

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
    using melonDS::FirmwareLayout;
    Expect(FirmwareLayout::Validate(0x20000, 0x1FE00, false, 0x138), "valid DS layout is accepted");
    Expect(FirmwareLayout::Validate(0x20000, 0x1FE00, true, 0x138), "valid DSi layout is accepted");
    Expect(!FirmwareLayout::Validate(0x20000, 0x200, false, 0x138), "Wi-Fi offset underflow is rejected");
    Expect(!FirmwareLayout::Validate(0x20000, 0x1FF00, false, 0x138), "second user settings record beyond EOF is rejected");
    Expect(!FirmwareLayout::Validate(0x20000, 0x800, true, 0x138), "DSi extended Wi-Fi offset underflow is rejected");
    Expect(!FirmwareLayout::Validate(0x20000, 0x1FE00, false, 0x20000), "header checksum range beyond EOF is rejected");
    Expect(!FirmwareLayout::IsSupportedLength(0x80001), "provider length drift above allocation is rejected");
    return Failures == 0 ? 0 : 1;
}
