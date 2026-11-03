#include <cstdio>
#include "WifiTxBounds.h"

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
    using melonDS::WifiTxBounds;
    Expect(WifiTxBounds::DescriptorFits(0x1FF4), "last complete TX descriptor fits");
    Expect(!WifiTxBounds::DescriptorFits(0x1FF6), "descriptor beyond Wi-Fi RAM is rejected");
    Expect(WifiTxBounds::FrameFits(0, 0x1FF4), "largest frame at address zero fits");
    Expect(!WifiTxBounds::FrameFits(2, 0x1FF4), "copy length includes the 12-byte TX header");
    Expect(!WifiTxBounds::FrameFits(0x1FF4, 1), "one byte beyond the last descriptor is rejected");
    Expect(WifiTxBounds::ContainsFrameBytes(24, 22, 2), "sequence field fits at the exact boundary");
    Expect(!WifiTxBounds::ContainsFrameBytes(23, 22, 2), "truncated sequence field is rejected");
    Expect(!WifiTxBounds::ContainsFrameBytes(6, 0, 7), "short WEP frame cannot underflow its FCS offset");
    Expect(WifiTxBounds::PreparedSlotCanStart(true), "validated TX slot may enter the busy state");
    Expect(!WifiTxBounds::PreparedSlotCanStart(false), "invalid TX slot cannot enter the busy state");
    Expect(WifiTxBounds::BusyMask(5) == 0x80, "MP reply slot uses its dedicated busy bit");
    Expect(WifiTxBounds::RuntimeSlotValid(5, false, 10), "default MP reply preamble is valid without a descriptor");
    Expect(WifiTxBounds::RuntimeSlotValid(5, false, 11), "default MP reply transfer is valid without a descriptor");
    Expect(!WifiTxBounds::RuntimeSlotValid(5, false, 0), "ordinary MP reply still requires a valid descriptor");
    Expect(!WifiTxBounds::RuntimeSlotValid(0, true, 0xFF), "unknown TX phase is rejected before it can stall busy state");
    Expect(WifiTxBounds::RuntimeSlotValid(1, true, 13), "MP command timeout phase remains valid");
    return Failures == 0 ? 0 : 1;
}
