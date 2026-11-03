#include <optional>
#include <new>
#include "CartLoader.h"
#include "Platform.h"
#include "SDCardArgsBuilder.h"
#include "CartridgeImageBounds.h"

using namespace melonDS;
using namespace melonDS::Platform;

namespace MelonDSAndroid
{

std::optional<NDSCart::NDSCartArgs> BuildNdsCartArgs(EmulatorConfiguration configuration, std::string romPath, std::string sramPath)
{
    std::unique_ptr<u8[]> romData = nullptr;
    std::unique_ptr<u8[]> sramData = nullptr;
    u32 romFileLength = 0;
    u32 sramFileLength = 0;

    // ROM file loading
    Platform::FileHandle* romFile = Platform::OpenFile(romPath, FileMode::Read);
    if (!romFile) return std::nullopt;

    u64 length = Platform::FileLength(romFile);
    if (!CartridgeImageBounds::IsNDSLengthSupported(length))
    {
        Platform::CloseFile(romFile);
        return std::nullopt;
    }

    romFileLength = (u32) length;
    Platform::FileRewind(romFile);
    romData.reset(new (std::nothrow) u8[romFileLength]);
    if (!romData)
    {
        Platform::CloseFile(romFile);
        return std::nullopt;
    }
    size_t nread = Platform::FileRead(romData.get(), (size_t) romFileLength, 1, romFile);
    Platform::CloseFile(romFile);
    if (nread != 1)
    {
        romData = nullptr;
        return std::nullopt;
    }

    // SRAM file loading
    FileHandle* sramFile = Platform::OpenFile(sramPath, FileMode::Read);
    if (!sramFile)
    {
        return std::nullopt;
    }
    else if (!Platform::CheckFileWritable(sramPath))
    {
        CloseFile(sramFile);
        return std::nullopt;
    }

    if (sramFile)
    {
        const u64 rawSramLength = Platform::FileLength(sramFile);
        if (rawSramLength > UINT32_MAX)
        {
            CloseFile(sramFile);
            return std::nullopt;
        }
        sramFileLength = static_cast<u32>(rawSramLength);

        FileRewind(sramFile);
        if (sramFileLength)
        {
            sramData.reset(new (std::nothrow) u8[sramFileLength]);
            if (!sramData || FileRead(sramData.get(), sramFileLength, 1, sramFile) != 1)
            {
                CloseFile(sramFile);
                return std::nullopt;
            }
        }
        CloseFile(sramFile);
    }

    NDSCart::NDSCartArgs cartargs {
        // Don't load the SD card itself yet, because we don't know if
        // the ROM is homebrew or not.
        // So this is the card we *would* load if the ROM were homebrew.
        .SDCard = getSDCardArgs(configuration.dldiSdCardSettings),
        .SRAM = std::move(sramData),
        .SRAMLength = sramFileLength,
    };

    // nullptr as user-data is not OK!
    auto cart = NDSCart::ParseROM(std::move(romData), romFileLength, nullptr, std::move(cartargs));
    if (!cart)
    {
        return std::nullopt;
    }

    return cartargs;
}

}
