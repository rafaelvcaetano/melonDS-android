#include <cstdio>
#include <limits>

#include "DSi_DSPBounds.h"
#include "DSi_NWifiBounds.h"

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
    using melonDS::DSi_DSPBounds;
    using melonDS::DSi_NWifiBounds;

    Expect(DSi_DSPBounds::CatchUpChunk(0) == 0,
        "empty DSP backlog produces no work");
    Expect(DSi_DSPBounds::CatchUpChunk(std::numeric_limits<melonDS::u32>::max()) ==
            std::numeric_limits<melonDS::u32>::max(),
        "maximum 32-bit DSP backlog is one complete chunk");
    Expect(DSi_DSPBounds::CatchUpChunk(0x100000000ULL) ==
            std::numeric_limits<melonDS::u32>::max(),
        "exact 2^32 DSP backlog produces positive bounded work");
    Expect(DSi_DSPBounds::CatchUpChunk(0x100000001ULL) ==
            std::numeric_limits<melonDS::u32>::max(),
        "DSP backlog above 2^32 remains positive and bounded");
    Expect(DSi_DSPBounds::RestoredClockValid(0, DSi_DSPBounds::MaxCatchUpCycles),
        "maximum supported restored DSP backlog is accepted");
    Expect(!DSi_DSPBounds::RestoredClockValid(0, DSi_DSPBounds::MaxCatchUpCycles + 1),
        "restored DSP backlog above the work budget is rejected");
    Expect(!DSi_DSPBounds::RestoredClockValid(0, 0x100000000ULL),
        "restored DSP backlog of exactly 2^32 cycles is rejected");
    Expect(!DSi_DSPBounds::RestoredClockValid(2, 1),
        "a restored DSP timestamp ahead of the ARM clock is rejected");

    Expect(DSi_NWifiBounds::MailboxPayloadValid(0x600, 0x600),
        "a complete maximum-size BMI mailbox payload is accepted");
    Expect(!DSi_NWifiBounds::MailboxPayloadValid(0x601, 0x600),
        "a BMI payload larger than the mailbox is rejected");
    Expect(!DSi_NWifiBounds::MailboxPayloadValid(8, 7),
        "a truncated BMI payload is rejected before its loop");

    const melonDS::u8 bytes[] = {0x10, 0x21, 0x32, 0x43, 0x54};
    melonDS::u32 value = 0;
    Expect(DSi_NWifiBounds::ReadLE32(bytes, sizeof(bytes), 1, value) &&
            value == 0x54433221,
        "an in-range unaligned EEPROM word is assembled little-endian");
    value = 0xA5A5A5A5;
    Expect(!DSi_NWifiBounds::ReadLE32(bytes, sizeof(bytes), 2, value) &&
            value == 0xA5A5A5A5,
        "an EEPROM tail overread is rejected without changing the result");

    return Failures == 0 ? 0 : 1;
}
