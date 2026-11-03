#include <array>
#include <cassert>
#include <cstdint>

#include "../../../app/src/main/cpp/retroachievements/MemoryPeekBounds.h"

int main()
{
    std::array<std::uint8_t, 8> memory{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

    assert(MelonDSAndroid::RetroAchievements::PeekSpanValid(7, 7, 1));
    assert(!MelonDSAndroid::RetroAchievements::PeekSpanValid(7, 7, 2));
    assert(MelonDSAndroid::RetroAchievements::PeekSpanValid(1, 7, 4));
    assert(!MelonDSAndroid::RetroAchievements::PeekSpanValid(6, 7, 4));
    assert(!MelonDSAndroid::RetroAchievements::PeekSpanValid(0, 7, 3));

    assert(MelonDSAndroid::RetroAchievements::ReadLittleEndian(memory.data(), 1, 2) == 0x0302U);
    assert(MelonDSAndroid::RetroAchievements::ReadLittleEndian(memory.data(), 1, 4) == 0x05040302U);
}
