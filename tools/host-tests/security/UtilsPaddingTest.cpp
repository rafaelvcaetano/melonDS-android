#include <cstdio>
#include <memory>

#include "Utils.h"

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
    using melonDS::u8;

    const u8 source[] = {0x11, 0x22, 0x33};
    auto copied = melonDS::PadToPowerOf2(source, 3);
    Expect(copied.second == 4, "copied ROM is padded to the next power of two");
    Expect(copied.first && copied.first[0] == 0x11 && copied.first[2] == 0x33,
        "copied ROM preserves logical bytes");
    Expect(copied.first && copied.first[3] == 0,
        "copied ROM padding is initialized deterministically");

    auto ownedSource = std::make_unique<u8[]>(3);
    ownedSource[0] = 0x44;
    ownedSource[1] = 0x55;
    ownedSource[2] = 0x66;
    auto moved = melonDS::PadToPowerOf2(std::move(ownedSource), 3);
    Expect(moved.second == 4, "owned ROM is padded to the next power of two");
    Expect(moved.first && moved.first[3] == 0,
        "owned ROM padding is initialized deterministically");

    return Failures == 0 ? 0 : 1;
}
