#include <cstdio>
#include <limits>
#include "CartridgeImageBounds.h"

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
    melonDS::NDSHeader header{};
    Expect(melonDS::CartridgeImageBounds::ValidateNDS(header, sizeof(header)), "minimal header-only NDS image is bounded");
    Expect(!melonDS::CartridgeImageBounds::ValidateNDS(header, sizeof(header) - 1), "truncated 4096-byte NDS header is rejected");
    header.ARM9ROMOffset = sizeof(header) - 2;
    header.ARM9Size = 4;
    Expect(!melonDS::CartridgeImageBounds::ValidateNDS(header, sizeof(header)), "ARM9 range beyond original image is rejected");
    header.ARM9ROMOffset = std::numeric_limits<melonDS::u32>::max() - 1;
    header.ARM9Size = 8;
    Expect(!melonDS::CartridgeImageBounds::ValidateNDS(header, 0x10000), "wrapping ARM9 range is rejected");
    header = {};
    header.ARM9ROMOffset = 0x4000;
    Expect(!melonDS::CartridgeImageBounds::ValidateNDS(header, 0x47FF), "truncated secure area is rejected before padding");
    Expect(melonDS::CartridgeImageBounds::ValidateNDS(header, 0x4800), "complete secure area is accepted");
    Expect(!melonDS::CartridgeImageBounds::IsNDSLengthSupported(0x20000001ULL), "NDS image above 512 MiB is rejected before allocation");
    Expect(melonDS::CartridgeImageBounds::IsGBALengthSupported(0xC0), "minimum GBA header is accepted");
    Expect(!melonDS::CartridgeImageBounds::IsGBALengthSupported(0xBF), "truncated GBA header is rejected");
    Expect(!melonDS::CartridgeImageBounds::IsGBALengthSupported(0x2000001), "GBA image above its 32 MiB address space is rejected");
    Expect(melonDS::CartridgeImageBounds::DirectBootReadWidth(0x1001, 0x1000) == 1,
        "direct boot copies the final partial word using only the logical ROM byte");
    Expect(melonDS::CartridgeImageBounds::DirectBootReadWidth(0x1004, 0x1000) == 4,
        "direct boot preserves a complete word read");
    Expect(melonDS::CartridgeImageBounds::DirectBootReadWidth(0x1000, 0x1000) == 0,
        "direct boot performs no read at logical EOF");
    return Failures == 0 ? 0 : 1;
}
