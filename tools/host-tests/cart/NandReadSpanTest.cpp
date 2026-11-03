#include <Args.h>
#include <NDS.h>
#include <NDSCart.h>

#include <algorithm>
#include <array>
#include <cstdio>
#include <memory>
#include <string>
#include <vector>

using namespace melonDS;
using namespace melonDS::NDSCart;

static std::array<u8, 8> Command(u8 opcode, u32 address)
{
    return {opcode, static_cast<u8>(address >> 24), static_cast<u8>(address >> 16),
            static_cast<u8>(address >> 8), static_cast<u8>(address), 0, 0, 0};
}

int main(int argc, char** argv)
{
    if (argc != 2) return 2;
    const std::string mode = argv[1];
    constexpr u32 base = 0x100000;
    constexpr u32 saveLength = 8 * 1024 * 1024;
    std::vector<u8> rom(0x20000, 0);
    rom[0x96] = base >> 17;
    auto save = std::make_unique<u8[]>(saveLength);
    for (u32 i = 0; i < saveLength; ++i)
        save[i] = static_cast<u8>(0x40 + (i & 0x3F));

    ROMListEntry params {};
    params.SaveMemType = 8;
    CartRetailNAND cart(rom.data(), rom.size(), 0, params,
                        std::move(save), saveLength, nullptr);
    cart.Reset();
    auto* stored = cart.GetSaveMemory();
    for (u32 i = 0; i < 16; ++i)
        stored[saveLength - 16 + i] = static_cast<u8>(0xA0 + i);
    for (u32 i = 0; i < 256; ++i)
        stored[saveLength - 0x1000 + i] = static_cast<u8>(0xC0 + (i & 0x3F));
    for (u32 i = 0; i < 256; ++i)
    {
        stored[0x20000 + i] = static_cast<u8>(0xD0 + (i & 0x1F));
        stored[0x21000 + i] = static_cast<u8>(0xE0 + (i & 0x1F));
    }

    NDSArgs args;
    args.JIT = std::nullopt;
    auto nds = std::make_unique<NDS>(std::move(args));
    cart.SetupDirectBoot("synthetic.nds", *nds);

    const u32 window = mode == "invalid-window" ? base + saveLength :
        mode == "valid" || mode == "page-wrap" ? base + 0x20000 :
        base + saveLength - 0x20000;
    const auto b2 = Command(0xB2, window);
    std::array<u8, 256> output {};
    cart.ROMCommandStart(*nds, nds->NDSCartSlot, b2.data(), output.data(), 0);

    const u32 address = mode == "valid" ? base + 0x20100 :
        mode == "page-wrap" ? base + 0x20FF0 :
        mode == "outside-start" ? base + saveLength : base + saveLength - 16;
    const u32 length = mode == "valid" ? 32 : mode == "near-end-exact" ? 16 : 256;
    const auto b7 = Command(0xB7, address);
    cart.ROMCommandStart(*nds, nds->NDSCartSlot, b7.data(), output.data(), length);

    for (u32 i = 0; i < length; ++i)
    {
        const u8 expected = mode == "valid" ? stored[address - base + i] :
            mode == "page-wrap" ? stored[0x20000 + ((0xFF0 + i) & 0xFFF)] :
            (mode == "near-end" || mode == "near-end-exact") && i < 16 ?
                static_cast<u8>(0xA0 + i) :
            mode == "near-end" ? stored[saveLength - 0x1000 + i - 16] : 0xFF;
        if (output[i] != expected)
        {
            std::fprintf(stderr, "%s: mismatch at %u: %02X != %02X\n",
                         mode.c_str(), i, output[i], expected);
            return 3;
        }
    }
    std::printf("%s: PASS bytes=%u\n", mode.c_str(), length);
    return 0;
}
