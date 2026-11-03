#include <cstdio>

#include "SavestateInvariant.h"

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
    using melonDS::SavestateInvariant;

    Expect(SavestateInvariant::CircularQueueValid(16, 0, 0, 0),
        "empty FIFO state is valid");
    Expect(SavestateInvariant::CircularQueueValid(16, 16, 7, 7),
        "full wrapped FIFO state is valid");
    Expect(!SavestateInvariant::CircularQueueValid(16, 0, 0, 16),
        "FIFO write cursor at capacity is rejected");
    Expect(!SavestateInvariant::CircularQueueValid(16, 17, 0, 1),
        "FIFO occupancy above capacity is rejected");
    Expect(!SavestateInvariant::CircularQueueValid(16, 2, 4, 9),
        "inconsistent FIFO cursors are rejected");

    Expect(SavestateInvariant::EventMaskValid(0x7, 3),
        "scheduler mask within the event table is valid");
    Expect(!SavestateInvariant::EventMaskValid(0x8, 3),
        "scheduler mask beyond the event table is rejected");
    Expect(!SavestateInvariant::EventFunctionValid(3, 3, true),
        "scheduler callback index at capacity is rejected");
    Expect(!SavestateInvariant::EventFunctionValid(0, 3, false),
        "unregistered scheduled callback is rejected");

    Expect(SavestateInvariant::SPUChannelValid(31, 7, 32, 88, 0),
        "bounded SPU FIFO and ADPCM state is valid");
    Expect(!SavestateInvariant::SPUChannelValid(32, 0, 0, 0, 0),
        "SPU byte cursor beyond its FIFO is rejected");
    Expect(!SavestateInvariant::SPUChannelValid(0, 8, 0, 0, 0),
        "SPU word cursor beyond its FIFO is rejected");
    Expect(!SavestateInvariant::SPUChannelValid(0, 0, 0, 89, 0),
        "SPU ADPCM table index is rejected");
    Expect(!SavestateInvariant::SPUCaptureValid(4, 0, 0),
        "capture read cursor beyond its FIFO is rejected");

    Expect(SavestateInvariant::GPUCountsValid(1, 6144, 2048, 1024, 2048),
        "maximum per-bank GPU counts are valid");
    Expect(!SavestateInvariant::GPUCountsValid(2, 0, 0, 0, 0),
        "invalid GPU bank is rejected");
    Expect(!SavestateInvariant::GPUCountsValid(0, 0, 2049, 0, 0),
        "polygon count beyond the active bank is rejected");
    Expect(!SavestateInvariant::GPUCountsValid(0, 0, 2, 3, 0),
        "opaque polygon count beyond total polygons is rejected");
    Expect(!SavestateInvariant::IndexOrNullValid(4096, 4096),
        "GPU pointer index at array capacity is rejected");
    Expect(SavestateInvariant::IndexOrNullValid(UINT32_MAX, 4096),
        "serialized null GPU pointer is valid");
    Expect(!SavestateInvariant::PolygonValid(11, 0, 0),
        "polygon vertex count beyond ten is rejected");
    Expect(!SavestateInvariant::PolygonValid(3, 3, 1),
        "polygon top vertex index beyond its vertices is rejected");
    Expect(SavestateInvariant::WifiRuntimeValid(3, 5, 2046, 2048),
        "simultaneous Wi-Fi TX/RX with bounded cursors is valid");
    Expect(!SavestateInvariant::WifiRuntimeValid(2, UINT32_MAX, 0, 2048),
        "Wi-Fi TX state with an invalid slot is rejected");
    Expect(!SavestateInvariant::WifiRuntimeValid(1, UINT32_MAX, 2048, 2048),
        "Wi-Fi RX state with an exhausted buffer cursor is rejected");
    Expect(SavestateInvariant::CountdownCanTick(0, 8),
        "zero Wi-Fi countdown can transition without signed overflow");
    Expect(!SavestateInvariant::CountdownCanTick(INT32_MIN, 8),
        "INT_MIN Wi-Fi countdown is rejected before subtraction");
    Expect(!SavestateInvariant::WifiClientMaskValid(0x0001),
        "Wi-Fi client mask cannot target reserved bit zero");
    Expect(SavestateInvariant::WifiClientMaskValid(0xFFFE),
        "Wi-Fi client mask accepts all real client bits");
    Expect(SavestateInvariant::AACCommandValid(1, 9), "partial AAC command remains valid");
    Expect(!SavestateInvariant::AACCommandValid(1, 10),
        "AAC parameter cursor at capacity is rejected before write");
    Expect(SavestateInvariant::RenderablePolygonValid(2), "two-vertex render record is bounded");
    Expect(!SavestateInvariant::RenderablePolygonValid(1), "one-vertex render record is rejected");

    return Failures == 0 ? 0 : 1;
}
