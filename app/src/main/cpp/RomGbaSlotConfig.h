#ifndef ROMGBASLOTCONFIG_H
#define ROMGBASLOTCONFIG_H

#include <string>

namespace MelonDSAndroid
{
    enum RomGbaSlotConfigType {
        NONE = 0,
        GBA_ROM = 1,
        RUMBLE_PAK = 2,
        MEMORY_EXPANSION = 3,
        MOTION_PAK_HOMEBREW = 4,
        MOTION_PAK_RETAIL = 5
    } ;

    struct RomGbaSlotConfig {
        RomGbaSlotConfigType type;
    };

    struct RomGbaSlotConfigNone {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::NONE };
    };

    struct RomGbaSlotConfigGbaRom {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::GBA_ROM };
        std::string romPath;
        std::string savePath;
    };

    struct RomGbaSlotRumblePak {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::RUMBLE_PAK };
    };

    struct RomGbaSlotConfigMemoryExpansion {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::MEMORY_EXPANSION };
    };

    struct RomGbaSlotMotionPakHomebrew {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::MOTION_PAK_HOMEBREW };
    };

    struct RomGbaSlotMotionPakRetail {
        RomGbaSlotConfig _base = { .type = RomGbaSlotConfigType::MOTION_PAK_RETAIL };
    };
}

#endif //ROMGBASLOTCONFIG_H
