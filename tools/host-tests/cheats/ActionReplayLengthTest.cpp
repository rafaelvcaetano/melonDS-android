#include <ARM.h>
#include <Args.h>
#include <NDS.h>

#include <cstdio>
#include <memory>
#include <string>
#include <vector>

using namespace melonDS;

int main(int argc, char** argv)
{
    if (argc != 2) return 2;
    const std::string mode = argv[1];
    NDSArgs args;
    args.JIT = std::nullopt;
    auto nds = std::make_unique<NDS>(std::move(args));
    nds->Reset();

    ARCode cheat {};
    cheat.Enabled = mode != "disabled";
    if (mode == "valid")
        cheat.Code = {0xE2000000, 8, 0x12345678, 0x9ABCDEF0};
    else if (mode == "one-byte")
        cheat.Code = {0xE2000000, 1, 0x123456EF, 0};
    else if (mode == "five-byte")
        cheat.Code = {0xE2000000, 5, 0x12345678, 0x9ABCDEEF};
    else if (mode == "truncated" || mode == "disabled")
        cheat.Code = {0xE2000000, 8};
    else if (mode == "huge-length")
        cheat.Code = {0xE2000000, 0xFFFFFFFF};
    else if (mode == "one-word")
        cheat.Code = {0xE2000000};
    else if (mode == "empty")
        cheat.Code = {};
    else if (mode == "conditional-skip")
        cheat.Code = {0x52000000, 0xFFFFFFFF, 0xE2000000, 8};
    else if (mode == "conditional-follow")
        cheat.Code = {0x52000000, 0xFFFFFFFF, 0xE2000000, 8,
                      0xAAAAAAAA, 0xBBBBBBBB, 0xD0000000, 0,
                      0x02000000, 0xCAFEBABE};
    else
        return 2;

    nds->AREngine.Cheats.push_back(std::move(cheat));
    nds->IE[1] = 1 << IRQ_VBlank;
    nds->IF[1] = 1 << IRQ_VBlank;
    nds->ARM7.CPSR &= ~0x80;
    nds->ARM7.TriggerIRQ();

    const u32 first = nds->ARM7Read32(0x02000000);
    const u32 second = nds->ARM7Read32(0x02000004);
    if (mode == "valid")
    {
        if (first != 0x12345678 || second != 0x9ABCDEF0) return 3;
    }
    else if (mode == "one-byte")
    {
        if (first != 0xEF || second != 0) return 3;
    }
    else if (mode == "five-byte")
    {
        if (first != 0x12345678 || second != 0xEF) return 3;
    }
    else if (mode == "conditional-follow")
    {
        if (first != 0xCAFEBABE || second != 0) return 3;
    }
    else if (first != 0 || second != 0)
        return 4;

    std::printf("%s: PASS first=%08X second=%08X\n", mode.c_str(), first, second);
    return 0;
}
