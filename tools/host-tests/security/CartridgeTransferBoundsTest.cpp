#include <cstdio>
#include "CartridgeTransferBounds.h"

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
    using melonDS::CartridgeTransferBounds;
    Expect(CartridgeTransferBounds::Validate(0, 0, 0, 0x4000), "idle transfer state is valid");
    Expect(CartridgeTransferBounds::Validate(0x3FFC, 0x4000, 1, 0x4000), "last aligned transfer word is valid");
    Expect(!CartridgeTransferBounds::Validate(0x4000, 0x4004, 0, 0x4000), "transfer length beyond fixed buffer is rejected");
    Expect(!CartridgeTransferBounds::Validate(2, 4, 0, 0x4000), "unaligned transfer position is rejected");
    Expect(!CartridgeTransferBounds::Validate(0, 4, 2, 0x4000), "unknown transfer direction is rejected");
    Expect(CartridgeTransferBounds::WordFits(0x3FFC, 0x4000), "last word fits fixed transfer data");
    Expect(!CartridgeTransferBounds::WordFits(0x3FFD, 0x4000), "partial trailing word is rejected");
    Expect(!CartridgeTransferBounds::B7LengthValid(0, 0x4000), "zero-length B7 command is rejected");
    Expect(CartridgeTransferBounds::B7LengthValid(0x4000, 0x4000), "maximum B7 transfer is accepted");
    return Failures == 0 ? 0 : 1;
}
