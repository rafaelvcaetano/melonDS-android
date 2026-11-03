// Author a DSi SDIO command sequence; only the network send boundary is captured.
#include <Args.h>
#include <DSi.h>
#include <WifiAP.h>

#include <array>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

namespace melonDS::Platform
{
extern int CapturedNetLength;
extern std::array<u8, 2048> CapturedNetData;
}

using namespace melonDS;

static void WriteMailboxByte(DSi& dsi, u8 value, bool last)
{
    const u32 address = last ? 0xFF : 0;
    const u32 command = (1u << 31) | (1u << 28) | (address << 9) | value;
    dsi.SDIO.Write(0x004, command & 0xFFFF);
    dsi.SDIO.Write(0x006, command >> 16);
    dsi.SDIO.Write(0x000, 52);
}

static void WriteMailboxCommand(DSi& dsi, const std::vector<u8>& bytes)
{
    for (size_t i = 0; i < bytes.size(); ++i)
        WriteMailboxByte(dsi, bytes[i], i + 1 == bytes.size());
}

static void Push16(std::vector<u8>& bytes, u16 value)
{
    bytes.push_back(value & 0xFF);
    bytes.push_back(value >> 8);
}

static void SetReadyAndConnect(DSi& dsi, bool connect)
{
    WriteMailboxCommand(dsi, {1, 0, 0, 0}); // BMI_DONE
    WriteMailboxCommand(dsi, {0, 0, 0, 0, 0, 0, 4, 0}); // HTC setup complete
    if (!connect) return;

    std::vector<u8> message;
    Push16(message, 0); // command endpoint
    Push16(message, 54);
    Push16(message, 0);
    Push16(message, 1); // WMI connect
    message.insert(message.end(), {1, 1, 1, 1, 0, 1, 0, 7});
    std::array<u8, 32> ssid {};
    memcpy(ssid.data(), "melonAP", 7);
    message.insert(message.end(), ssid.begin(), ssid.end());
    Push16(message, 2437);
    message.insert(message.end(), WifiAP::APMac, WifiAP::APMac + 6);
    message.insert(message.end(), {0, 0, 0, 0});
    WriteMailboxCommand(dsi, message);
}

static std::vector<u8> DataCommand(const std::string& mode)
{
    const u16 payloadSize = mode == "oversized" || mode == "oversized-short-len" ? 2200 :
        mode == "max-buffered" ? 1506 : mode == "valid" ? 3 : 0;
    const u16 plen = mode == "short-plen" ? 7 :
        mode == "missing-payload" ? 11 : 8 + payloadSize;
    const u16 advertisedLen = mode == "oversized-short-len" ? 27 : 16 + plen;
    std::vector<u8> command;
    Push16(command, 2); // data endpoint
    Push16(command, advertisedLen);
    Push16(command, 0);
    command.push_back(0); // 16-bit WMI data header
    command.push_back(mode == "sync" ? 2 : 0);
    command.insert(command.end(), {0x04, 0, 0, 0, 0, 1}); // destination
    command.insert(command.end(), {0x02, 0, 0, 0, 0, 2}); // source
    command.push_back(plen >> 8); // big-endian plen
    command.push_back(plen & 0xFF);
    command.insert(command.end(), {static_cast<u8>(mode == "bad-llc" ? 0 : 0xAA), 0xAA, 0x03, 0, 0, 0});
    command.insert(command.end(), {0x08, 0x06}); // Ethernet type
    if (mode == "valid") command.insert(command.end(), {0x40, 0x41, 0x42});
    if (mode == "max-buffered")
        for (u16 i = 0; i < payloadSize; ++i) command.push_back(static_cast<u8>(0x40 + (i & 0x3F)));
    if (mode == "oversized" || mode == "oversized-short-len") command.push_back(0x40);
    return command;
}

int main(int argc, char** argv)
{
    if (argc != 2) return 2;
    const std::string mode = argv[1];
    FILE* nandFile = std::tmpfile();
    if (!nandFile || std::fseek(nandFile, 0x10EE00 + 4 * 1024 * 1024, SEEK_SET)) return 5;
    u8 footer[64] {};
    memcpy(footer, "DSi eMMC CID/CPU", 16);
    if (std::fwrite(footer, sizeof(footer), 1, nandFile) != 1) return 6;
    DSi_NAND::DSiKey key {};
    DSi_NAND::NANDImage nand(reinterpret_cast<Platform::FileHandle*>(nandFile), key);
    if (!nand) return 7;
    NDSArgs ndsArgs;
    ndsArgs.JIT = std::nullopt;
    DSiArgs args {std::move(ndsArgs),
                  std::make_unique<DSiBIOSImage>(BrokenBIOS<DSiBIOSSize>),
                  std::make_unique<DSiBIOSImage>(BrokenBIOS<DSiBIOSSize>),
                  std::move(nand), std::nullopt, false, false};
    auto dsi = std::make_unique<DSi>(std::move(args));
    dsi->SDIO.Reset();
    SetReadyAndConnect(*dsi, mode != "disconnected");
    Platform::CapturedNetLength = -1;
    WriteMailboxCommand(*dsi, DataCommand(mode));

    if (mode == "valid" || mode == "max-buffered")
    {
        const u8 expected[17] = {0x04, 0, 0, 0, 0, 1, 0x02, 0, 0, 0, 0, 2,
                                 0x08, 0x06, 0x40, 0x41, 0x42};
        if (mode == "valid")
        {
            if (Platform::CapturedNetLength != 17 ||
                memcmp(Platform::CapturedNetData.data(), expected, sizeof(expected))) return 3;
        }
        else
        {
            if (Platform::CapturedNetLength != 1520 ||
                memcmp(Platform::CapturedNetData.data(), expected, 14)) return 3;
            for (int i = 0; i < 1506; ++i)
                if (Platform::CapturedNetData[14 + i] != static_cast<u8>(0x40 + (i & 0x3F))) return 3;
        }
    }
    else if (Platform::CapturedNetLength != -1) return 4;

    printf("%s: PASS\n", mode.c_str());
    return 0;
}
