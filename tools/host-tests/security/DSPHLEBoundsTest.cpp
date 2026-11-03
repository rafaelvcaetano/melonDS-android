#include <cstdio>
#include "DSPHLEBounds.h"

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
    using melonDS::DSPHLEBounds;
    Expect(DSPHLEBounds::PipeValid(0x1000, 0x200, 0, 0), "normal DSP pipe descriptor is valid");
    Expect(!DSPHLEBounds::PipeValid(0x3FFF, 4, 0, 0), "pipe ring cannot cross a mapped 32 KiB page");
    Expect(!DSPHLEBounds::PipeValid(0x1000, 0, 0, 0), "zero-length pipe is rejected");
    Expect(!DSPHLEBounds::PipeValid(0x1000, 3, 0, 0), "odd pipe length is rejected");
    Expect(!DSPHLEBounds::PipeValid(0x1000, 0x200, 0x200, 0), "read pointer at ring end is rejected");
    Expect(!DSPHLEBounds::PipeValid(0x1000, 0x200, 1, 0), "odd read pointer is rejected");
    Expect(DSPHLEBounds::MemoryTransferValid(0), "zero-byte memory transfer is a safe no-op");
    Expect(!DSPHLEBounds::MemoryTransferValid(1), "odd memory transfer is rejected");
    Expect(DSPHLEBounds::MicBufferValid(0x2003, 0x1000, 0),
        "normal DSP microphone ring fits its mapped page");
    Expect(!DSPHLEBounds::MicBufferValid(0x2003, 0xFFFF, 0x3FFF),
        "oversized DSP microphone ring is rejected");
    Expect(DSPHLEBounds::ScalingValid(10, 3, 3, 1, 1), "minimal one-third scaling is valid");
    Expect(!DSPHLEBounds::ScalingValid(10, 0, 3, 0, 1), "zero-width one-third scaling is rejected");
    Expect(!DSPHLEBounds::ScalingValid(3, 3, 4, 2, 2), "bicubic source smaller than four pixels is rejected");
    Expect(DSPHLEBounds::ScaledDimension(65535, 65535) == 4294836,
        "scaled dimensions use wide multiplication");
    Expect(DSPHLEBounds::YuvTransferValid(4), "one packed YUV pair is accepted");
    Expect(!DSPHLEBounds::YuvTransferValid(2), "partial packed YUV input is rejected");
    Expect(!DSPHLEBounds::YuvTransferValid(UINT32_MAX & ~3U),
        "YUV work that overflows the scheduler delay is rejected");
    return Failures == 0 ? 0 : 1;
}
