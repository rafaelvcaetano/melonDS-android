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

    Expect(SavestateInvariant::BPTWLCursorValid(0xFF),
        "last BPTWL register cursor is valid");
    Expect(SavestateInvariant::BPTWLCursorValid(UINT32_MAX),
        "BPTWL idle cursor sentinel is valid");
    Expect(!SavestateInvariant::BPTWLCursorValid(0x100),
        "BPTWL cursor beyond the register file is rejected");

    Expect(SavestateInvariant::SDHostStateValid(1, 0x200, 0x200),
        "last SD FIFO selector with sector-sized lengths is valid");
    Expect(!SavestateInvariant::SDHostStateValid(2, 0x200, 0x200),
        "SD FIFO selector beyond the two FIFOs is rejected");
    Expect(!SavestateInvariant::SDHostStateValid(0, 0x201, 0x200),
        "SD 16-bit length beyond a sector is rejected");

    Expect(SavestateInvariant::CameraStateValid(1, 512, 0, 3, 512),
        "bounded camera cursors remain valid without imposing read-before-write ordering");
    Expect(!SavestateInvariant::CameraStateValid(2, 0, 0, 0, 0),
        "camera buffer selector beyond the double buffer is rejected");
    Expect(!SavestateInvariant::CameraStateValid(0, 513, 0, 0, 0),
        "camera cursor beyond the fixed buffer is rejected");
    Expect(SavestateInvariant::CameraFrameDimensionsValid(2, 2),
        "camera accepts the minimum even frame");
    Expect(SavestateInvariant::CameraFrameDimensionsValid(640, 480),
        "camera accepts the native frame");
    Expect(!SavestateInvariant::CameraFrameDimensionsValid(1, 2),
        "camera rejects a one-pixel frame");
    Expect(!SavestateInvariant::CameraFrameDimensionsValid(3, 2),
        "camera rejects odd widths");
    Expect(!SavestateInvariant::CameraFrameDimensionsValid(642, 480),
        "camera rejects oversized frames");

    Expect(SavestateInvariant::GPU2DFIFOValid(15, 14),
        "last coherent GPU2D FIFO cursors are valid");
    Expect(!SavestateInvariant::GPU2DFIFOValid(16, 14),
        "GPU2D read cursor at capacity is rejected");
    Expect(!SavestateInvariant::GPU2DFIFOValid(0, 15),
        "GPU2D paired write cursor must be even and in range");

    Expect(SavestateInvariant::TeakraDMAChannelValid(7),
        "last Teakra DMA channel is valid");
    Expect(!SavestateInvariant::TeakraDMAChannelValid(8),
        "Teakra DMA channel beyond the native array is rejected");
    Expect(SavestateInvariant::BtdmpQueueValid(15),
        "odd DSP audio queue is valid between guest writes");
    Expect(!SavestateInvariant::BtdmpQueueValid(17),
        "DSP audio queue beyond hardware capacity is rejected");

    Expect(SavestateInvariant::SPUMixIntervalValid(704),
        "47 kHz SPU mix interval is valid");
    Expect(SavestateInvariant::SPUMixIntervalValid(1024),
        "32 kHz SPU mix interval is valid");
    Expect(!SavestateInvariant::SPUMixIntervalValid(0),
        "zero SPU mix interval is rejected");
    Expect(SavestateInvariant::SPUInterpolationPosition(0x100, 0x200) == 0,
        "live timer reload transitions clamp interpolation before the table");
    Expect(SavestateInvariant::SPUInterpolationPosition(0xFFFF, 0) == 255,
        "SPU interpolation clamps the last table position");

    Expect(SavestateInvariant::VRAMMappingValid(0x1FF),
        "all nine GPU VRAM bank bits are valid");
    Expect(!SavestateInvariant::VRAMMappingValid(0x200),
        "GPU VRAM mapping bits beyond the bank table are rejected");
    Expect(SavestateInvariant::AESModeValid(3), "last DSi AES mode is valid");
    Expect(!SavestateInvariant::AESModeValid(4), "unknown DSi AES mode is rejected");
    Expect(SavestateInvariant::GPUVertexStateValid(0, 2),
        "triangle mode accepts two pending vertices");
    Expect(!SavestateInvariant::GPUVertexStateValid(0, 3),
        "triangle mode rejects a completed-count pending vertex index");
    Expect(SavestateInvariant::GPUVertexStateValid(3, 3),
        "quad-strip mode accepts three pending vertices");
    Expect(!SavestateInvariant::GPUVertexStateValid(4, 0),
        "unknown polygon mode is rejected");
    Expect(SavestateInvariant::RTCCursorsValid(7, 7, 7, 7),
        "last RTC byte and bit cursors are valid");
    Expect(!SavestateInvariant::RTCCursorsValid(8, 0, 0, 0),
        "RTC input bit beyond one byte is rejected");
    Expect(!SavestateInvariant::RTCCursorsValid(0, 0, 0, 8),
        "RTC output position beyond its array is rejected");
    Expect(SavestateInvariant::SolarLevelValid(10), "last solar sensor level is valid");
    Expect(!SavestateInvariant::SolarLevelValid(11), "solar sensor table overflow is rejected");

    return Failures == 0 ? 0 : 1;
}
