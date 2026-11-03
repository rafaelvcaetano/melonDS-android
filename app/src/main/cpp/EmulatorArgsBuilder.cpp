#include <assert.h>
#include <codecvt>
#include <optional>
#include "Args.h"
#include "Configuration.h"
#include "EmulatorArgsBuilder.h"
#include "FreeBIOS.h"
#include "Platform.h"
#include "PlatformAndroid.h"
#include "SDCardArgsBuilder.h"
#include "SPU.h"

using namespace melonDS;
using namespace melonDS::Platform;

namespace MelonDSAndroid
{

std::unique_ptr<ARM9BIOSImage> loadARM9BIOS(const EmulatorConfiguration& configuration) noexcept
{
    if (configuration.userInternalFirmwareAndBios)
    {
        return std::make_unique<ARM9BIOSImage>(bios_arm9_bin);
    }

    std::string path = configuration.dsBios9Path;

    if (FileHandle* f = OpenLocalFile(path, Read))
    {
        std::unique_ptr<ARM9BIOSImage> bios = std::make_unique<ARM9BIOSImage>();
        FileRewind(f);
        FileRead(bios->data(), bios->size(), 1, f);
        CloseFile(f);
        Log(Info, "ARM9 BIOS loaded from %s\n", path.c_str());
        return bios;
    }

    Log(Warn, "ARM9 BIOS not found\n");
    return nullptr;
}

std::unique_ptr<ARM7BIOSImage> loadARM7BIOS(const EmulatorConfiguration& configuration) noexcept
{
    if (configuration.userInternalFirmwareAndBios)
    {
        return std::make_unique<ARM7BIOSImage>(bios_arm7_bin);
    }

    std::string path = configuration.dsBios7Path;

    if (FileHandle* f = OpenLocalFile(path, Read))
    {
        std::unique_ptr<ARM7BIOSImage> bios = std::make_unique<ARM7BIOSImage>();
        FileRead(bios->data(), bios->size(), 1, f);
        CloseFile(f);
        Log(Info, "ARM7 BIOS loaded from %s\n", path.c_str());
        return bios;
    }

    Log(Warn, "ARM7 BIOS not found\n");
    return nullptr;
}

bool parseMacAddress(std::string mac, void* data)
{
    u8* mac_out = (u8*)data;

    int o = 0;
    u8 tmp = 0;
    for (int i = 0; i < 18; i++)
    {
        char c = mac[i];
        if (c == '\0') break;

        int n;
        if      (c >= '0' && c <= '9') n = c - '0';
        else if (c >= 'a' && c <= 'f') n = c - 'a' + 10;
        else if (c >= 'A' && c <= 'F') n = c - 'A' + 10;
        else continue;

        if (!(o & 1))
            tmp = n;
        else
            mac_out[o >> 1] = n | (tmp << 4);

        o++;
        if (o >= 12) return true;
    }

    return false;
}

void customizeFirmware(const EmulatorConfiguration& configuration, Firmware& firmware, bool isInternalFirmware, int instanceId) noexcept
{
    auto firmwareConfig = configuration.firmwareConfiguration;

    if (isInternalFirmware)
    {
        auto &currentData = firmware.GetEffectiveUserData();

        // setting up username
        const std::u16string& username = firmwareConfig.username;
        if (!username.empty())
        { // If the frontend defines a username, take it. If not, leave the existing one.
            size_t usernameLength = std::min((int) username.length(), 10);
            currentData.NameLength = usernameLength;
            memcpy(currentData.Nickname, username.data(), usernameLength * sizeof(char16_t));
        }

        auto language = static_cast<Firmware::Language>(firmwareConfig.language);
        if (language != Firmware::Language::Reserved)
        { // If the frontend specifies a language (rather than using the existing value)...
            currentData.Settings &= ~Firmware::Language::Reserved; // ..clear the existing language...
            currentData.Settings |= language; // ...and set the new one.
        }

        // setting up color
        u8 favoritecolor = firmwareConfig.favouriteColour;
        if (favoritecolor != 0xFF)
        {
            currentData.FavoriteColor = favoritecolor;
        }

        u8 birthmonth = firmwareConfig.birthdayMonth;
        if (birthmonth != 0)
        { // If the frontend specifies a birth month (rather than using the existing value)...
            currentData.BirthdayMonth = birthmonth;
        }

        u8 birthday = firmwareConfig.birthdayDay;
        if (birthday != 0)
        { // If the frontend specifies a birthday (rather than using the existing value)...
            currentData.BirthdayDay = birthday;
        }

        // setup message
        const std::u16string& message = firmwareConfig.message;
        if (!message.empty())
        {
            size_t messageLength = std::min((int) message.length(), 26);
            currentData.MessageLength = messageLength;
            memcpy(currentData.Message, message.data(), messageLength * sizeof(char16_t));
        }
    }

    MacAddress mac;
    bool replaceMac = false;
    auto& header = firmware.GetHeader();

    memcpy(&mac, header.MacAddr.data(), sizeof(MacAddress));

    MacAddress configuredMac;
    if (firmwareConfig.randomizeMacAddress)
    {
        configuredMac = mac;
        configuredMac[3] = rand() % 256;
        configuredMac[4] = rand() % 256;
        configuredMac[5] = rand() % 256;
        replaceMac = true;
    }
    else if (isInternalFirmware)
    {
        replaceMac = parseMacAddress(firmwareConfig.macAddress.c_str(), &configuredMac);
    }

    if (replaceMac)
        mac = configuredMac;

    if (instanceId > 0)
    {
        replaceMac = true;
        mac[3] += instanceId;
        mac[4] += instanceId * 0x44;
        mac[5] += instanceId * 0x10;
    }

    if (replaceMac)
    {
        mac[0] &= 0xFC; // ensure the MAC isn't a broadcast MAC
        header.MacAddr = mac;
        header.UpdateChecksum();
    }

    firmware.UpdateChecksums();
}

Firmware generateFirmware(const EmulatorConfiguration& configuration, int type, int instanceId) noexcept
{
    const std::string kWifiSettingsPath = "wfcsettings.bin";

    // Construct the default firmware...
    std::string settingspath;
    Firmware firmware = Firmware(type);
    assert(firmware.Buffer() != nullptr);

    // If using generated firmware, we keep the wi-fi settings on the host disk separately.
    // Wi-fi access point data includes Nintendo WFC settings,
    // and if we didn't keep them then the player would have to reset them in each session.
    // We don't need to save the whole firmware, just the part that may actually change.
    if (FileHandle* f = OpenInternalFile(kWifiSettingsPath, Read))
    {// If we have Wi-fi settings to load...
        constexpr unsigned TOTAL_WFC_SETTINGS_SIZE = 3 * (sizeof(Firmware::WifiAccessPoint) + sizeof(Firmware::ExtendedWifiAccessPoint));

        if (!FileRead(firmware.GetExtendedAccessPointPosition(), TOTAL_WFC_SETTINGS_SIZE, 1, f))
        { // If we couldn't read the Wi-fi settings from this file...
            Log(Warn, "Failed to read Wi-fi settings from \"%s\"; using defaults instead\n", kWifiSettingsPath.c_str());

            // The access point and extended access point segments might
            // be in different locations depending on the firmware revision,
            // but our generated firmware always keeps them next to each other.
            // (Extended access points first, then regular ones.)
            firmware.GetAccessPoints() = {
                Firmware::WifiAccessPoint(type),
                Firmware::WifiAccessPoint(),
                Firmware::WifiAccessPoint(),
            };

            firmware.GetExtendedAccessPoints() = {
                Firmware::ExtendedWifiAccessPoint(),
                Firmware::ExtendedWifiAccessPoint(),
                Firmware::ExtendedWifiAccessPoint(),
            };
            firmware.UpdateChecksums();
        }

        // Close on both paths: the successful read used to leak the handle on every ROM boot.
        CloseFile(f);
    }

    customizeFirmware(configuration, firmware, true, instanceId);

    // If we don't have Wi-fi settings to load,
    // then the defaults will have already been populated by the constructor.
    return firmware;
}

std::unique_ptr<DSiBIOSImage> loadDSiARM9BIOS(const EmulatorConfiguration& configuration) noexcept
{
    std::string path = configuration.dsiBios9Path;

    if (FileHandle* f = OpenFile(path, Read))
    {
        std::unique_ptr<DSiBIOSImage> bios = std::make_unique<DSiBIOSImage>();
        FileRead(bios->data(), bios->size(), 1, f);
        CloseFile(f);

        if (!configuration.showBootScreen)
        {
            // herp
            *(u32*)bios->data() = 0xEAFFFFFE; // overwrites the reset vector

            // TODO!!!!
            // hax the upper 32K out of the goddamn DSi
            // done that :)  -pcy
        }
        Log(Info, "ARM9i BIOS loaded from %s\n", path.c_str());
        return bios;
    }

    Log(Warn, "ARM9i BIOS not found\n");
    return nullptr;
}

std::unique_ptr<DSiBIOSImage> loadDSiARM7BIOS(const EmulatorConfiguration& configuration) noexcept
{
    std::string path = configuration.dsiBios7Path;

    if (FileHandle* f = OpenFile(path, Read))
    {
        std::unique_ptr<DSiBIOSImage> bios = std::make_unique<DSiBIOSImage>();
        FileRead(bios->data(), bios->size(), 1, f);
        CloseFile(f);

        if (!configuration.showBootScreen)
        {
            // herp
            *(u32*)bios->data() = 0xEAFFFFFE; // overwrites the reset vector

            // TODO!!!!
            // hax the upper 32K out of the goddamn DSi
            // done that :)  -pcy
        }
        Log(Info, "ARM7i BIOS loaded from %s\n", path.c_str());
        return bios;
    }

    Log(Warn, "ARM7i BIOS not found\n");
    return nullptr;
}

std::optional<Firmware> loadFirmware(const EmulatorConfiguration& configuration, int type, int instanceId) noexcept
{
    if (configuration.userInternalFirmwareAndBios)
    { // If we're using built-in firmware...
        if (type == 1)
        {
            // TODO: support generating a firmware for DSi mode
        }
        else
        {
            return generateFirmware(std::move(configuration), type, instanceId);
        }
    }

    std::string firmwarePath;
    if (type == 1)
        firmwarePath = configuration.dsiFirmwarePath;
    else
        firmwarePath = configuration.dsFirmwarePath;

    std::string fwpath_inst = firmwarePath; // TODO: Add support for one firmware file per instance

    Log(Debug, "Loading firmware from file %s\n", fwpath_inst.c_str());
    FileHandle* file = OpenFile(fwpath_inst, Read);

    if (!file)
    {
        Log(Debug, "Loading firmware from file %s\n", firmwarePath.c_str());
        file = OpenFile(firmwarePath, Read);
        if (!file)
        {
            Log(Error, "Couldn't open firmware file!\n");
            return std::nullopt;
        }
    }

    Firmware firmware(file);
    CloseFile(file);

    if (!firmware.Buffer())
    {
        Log(Error, "Couldn't read firmware file!\n");
        return std::nullopt;
    }

    customizeFirmware(std::move(configuration), firmware, false, instanceId);

    return firmware;
}

std::optional<DSi_NAND::NANDImage> loadNAND(const EmulatorConfiguration& configuration, const std::array<u8, DSiBIOSSize>& arm7ibios) noexcept
{
    std::string path = configuration.dsiNandPath;

    FileHandle* nandfile = OpenFile(path, ReadWriteExisting);
    if (!nandfile)
        return std::nullopt;

    DSi_NAND::NANDImage nandImage(nandfile, &arm7ibios[0x8308]);
    if (!nandImage)
    {
        Log(Error, "Failed to parse DSi NAND\n");
        return std::nullopt;
        // the NANDImage takes ownership of the FileHandle, no need to clean it up here
    }

    // scoped so that mount isn't alive when we move the NAND image to DSi::NANDImage
    {
        auto mount = DSi_NAND::NANDMount(nandImage);
        if (!mount)
        {
            Log(Error, "Failed to mount DSi NAND\n");
            return std::nullopt;
        }

        DSi_NAND::DSiFirmwareSystemSettings settings {};
        if (!mount.ReadUserData(settings))
        {
            Log(Error, "Failed to read DSi NAND user data\n");
            return std::nullopt;
        }

        // override user settings, if needed
        if (false)
        {
            auto firmcfg = configuration.firmwareConfiguration;

            // we store relevant strings as UTF-8, so we need to convert them to UTF-16
            //auto converter = wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{};

            // setting up username
            const std::u16string& username = firmcfg.username;
            size_t usernameLength = std::min((int) username.length(), 10);
            memset(&settings.Nickname, 0, sizeof(settings.Nickname));
            memcpy(&settings.Nickname, username.data(), usernameLength * sizeof(char16_t));

            // setting language
            settings.Language = static_cast<Firmware::Language>(firmcfg.language);

            // setting up color
            settings.FavoriteColor = firmcfg.favouriteColour;

            // setting up birthday
            settings.BirthdayMonth = firmcfg.birthdayMonth;
            settings.BirthdayDay = firmcfg.birthdayDay;

            // setup message
            const std::u16string& message = firmcfg.message;
            size_t messageLength = std::min((int) message.length(), 26);
            memset(&settings.Message, 0, sizeof(settings.Message));
            memcpy(&settings.Message, message.data(), messageLength * sizeof(char16_t));

            // TODO: make other items configurable?
        }

        // fix touchscreen coords
        settings.TouchCalibrationADC1 = {0, 0};
        settings.TouchCalibrationPixel1 = {0, 0};
        settings.TouchCalibrationADC2 = {255 << 4, 191 << 4};
        settings.TouchCalibrationPixel2 = {255, 191};

        settings.UpdateHash();

        if (!mount.ApplyUserData(settings))
        {
            Log(LogLevel::Error, "Failed to write patched DSi NAND user data\n");
            return std::nullopt;
        }
    }

    return nandImage;
}

std::optional<FATStorage> loadSDCard(SdCardSettings settings) noexcept
{
    auto args = getSDCardArgs(settings);
    if (!args.has_value())
        return std::nullopt;

    return FATStorage(args.value());
}

std::optional<std::unique_ptr<NDSArgs>> BuildArgsFromConfiguration(const EmulatorConfiguration& configuration, int instanceId)
{
#ifdef JIT_ENABLED
    JITArgs _jitArgs {
        .MaxBlockSize = 32,
        .LiteralOptimizations = true,
        .BranchOptimizations = true,
        .FastMemory = true,
    };
    auto jitArgs = configuration.useJit ? std::make_optional(_jitArgs) : std::nullopt;
#else
    std::optional<JITArgs> jitArgs = std::nullopt;
#endif

    auto arm9bios = loadARM9BIOS(configuration);
    if (!arm9bios)
        return std::nullopt;

    auto arm7bios = loadARM7BIOS(configuration);
    if (!arm7bios)
        return std::nullopt;

    auto firmware = loadFirmware(configuration, configuration.consoleType, instanceId);
    if (!firmware)
        return std::nullopt;

    NDSArgs* _ndsArgs = new NDSArgs {
        .ARM9BIOS = std::move(arm9bios),
        .ARM7BIOS = std::move(arm7bios),
        .Firmware = std::move(*firmware),
        .JIT = jitArgs,
        .BitDepth = static_cast<AudioBitDepth>(configuration.audioSettings.audioBitrate),
        .Interpolation = static_cast<AudioInterpolation>(configuration.audioSettings.audioInterpolation),
        .OutputSampleRate = 48000.0,
        .GDB = std::nullopt,
    };
    std::unique_ptr<NDSArgs> ndsArgs = std::unique_ptr<NDSArgs>(_ndsArgs);

    if (configuration.consoleType == 1)
    {
        auto arm7ibios = loadDSiARM7BIOS(configuration);
        if (!arm7ibios)
            return std::nullopt;

        auto arm9ibios = loadDSiARM9BIOS(configuration);
        if (!arm9ibios)
            return std::nullopt;

        auto nand = loadNAND(configuration, *arm7ibios);
        if (!nand)
            return std::nullopt;

        auto sdcard = loadSDCard(configuration.dsiSdCardSettings);

        DSiArgs _dsiArgs = DSiArgs {
            std::move(*ndsArgs),
            std::move(arm9ibios),
            std::move(arm7ibios),
            std::move(*nand),
            std::move(sdcard),
            false,
            true,
        };

        std::unique_ptr<DSiArgs> uniqueArgs = std::make_unique<DSiArgs>(std::move(_dsiArgs));
        return uniqueArgs;
    }
    else
    {
        return ndsArgs;
    }
}

}
