#pragma once

#include <cstdint>

namespace MelonDSAndroid::RetroAchievements
{

inline bool PeekSpanValid(std::uint32_t offset, std::uint32_t memoryMask, unsigned numBytes)
{
    if (numBytes != 1 && numBytes != 2 && numBytes != 4)
        return false;

    const std::uint64_t regionSize = static_cast<std::uint64_t>(memoryMask) + 1U;
    return offset < regionSize && numBytes <= regionSize - offset;
}

inline unsigned ReadLittleEndian(const std::uint8_t* memory, std::uint32_t offset, unsigned numBytes)
{
    unsigned value = 0;
    for (unsigned i = 0; i < numBytes; ++i)
        value |= static_cast<unsigned>(memory[offset + i]) << (i * 8U);
    return value;
}

}
