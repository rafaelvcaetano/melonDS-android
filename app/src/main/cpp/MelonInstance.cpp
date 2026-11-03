#include <cstring>
#include <ctime>
#include <chrono>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <filesystem>
#include <new>
#include <GLES3/gl3.h>
#include "Args.h"
#include "GPU3D_Compute.h"
#include "Configuration.h"
#include "DSi.h"
#include "DSiSupport.h"
#include "DSi_I2C.h"
#include "GPU3D_OpenGL.h"
#include "MelonDS.h"
#include "MelonInstance.h"
#include "NDS.h"
#include "NDSCart.h"
#include "net/Net_Slirp.h"
#include "Platform.h"
#include "RtcSync.h"
#include "SDCardArgsBuilder.h"
#include "EmulatorMessageQueueJNI.h"
#include "WfcSaveRange.h"
#include "CartridgeImageBounds.h"

using namespace std;
using namespace melonDS;
using namespace melonDS::Platform;

namespace MelonDSAndroid
{

namespace {
struct WildEncounterMemory {
    NDS& nds;
    uint8_t read8(uint32_t address) { return nds.ARM9Read8(address); }
    uint32_t read32(uint32_t address) { return nds.ARM9Read32(address); }
    void write8(uint32_t address, uint8_t value) { nds.ARM9Write8(address, value); }
    void write16(uint32_t address, uint16_t value) { nds.ARM9Write16(address, value); }
};
}

const int kRewindBufferSize = 1024 * 1024 * 20; // Use 20MB per savestate
const int kRewindScreenshotSize = 256 * 384 * 4;

MelonInstance::MelonInstance(int instanceId, std::shared_ptr<EmulatorConfiguration> configuration, std::unique_ptr<melonDS::NDSArgs> args, std::shared_ptr<Net> net, std::unique_ptr<ScreenshotRenderer> screenshotRenderer, int consoleType) :
    instanceId(instanceId),
    currentConfiguration(configuration),
    net(net),
    screenshotRenderer(std::move(screenshotRenderer)),
    consoleType(consoleType),
    rewindManager(configuration->rewindEnabled, configuration->rewindLengthSeconds, configuration->rewindCaptureSpacingSeconds, kRewindBufferSize, kRewindScreenshotSize)
{
    // Software renderer is always used during initialisation. Actual renderer will be set of first frame run
    currentRenderer = Renderer::Software;
    isRenderConfigurationDirty = true;
    inputMask = 0xFFF;
    frame = 0;

    net->RegisterInstance(instanceId);

    if (consoleType == 1)
    {
        melonDS::DSiArgs &dsiArgs = static_cast<melonDS::DSiArgs &>(*args);
        nds = new DSi(std::move(dsiArgs), this);
    }
    else
    {
        nds = new NDS(std::move(*args), this);
    }

    // Only generated firmware keeps its Wi-fi settings in wfcsettings.bin. DSi always boots from
    // the user's firmware file (see loadFirmware() in EmulatorArgsBuilder.cpp), and a save manager
    // pointed at wfcsettings.bin would dump the whole 128 KB firmware into it.
    if (configuration->userInternalFirmwareAndBios && consoleType != 1)
    {
        std::filesystem::path firmwarePath = MelonDSAndroid::internalFilesDir;
        firmwarePath /= "wfcsettings.bin";
        firmwareSave = std::make_unique<SaveManager>(firmwarePath);
    }
    else
    {
        std::string firmwarePathString;
        if (consoleType == 1)
            firmwarePathString = configuration->dsiFirmwarePath;
        else
            firmwarePathString = configuration->dsFirmwarePath;

        firmwareSave = std::make_unique<SaveManager>(firmwarePathString);
    }

    // All instances have a RetroAchievements manager, but only the first instance will actually load achievements
    retroAchievementsManager = std::make_unique<RetroAchievements::RetroAchievementsManager>(nds);

    nds->Reset();
    setBatteryLevels();
    setDateTime();
}

MelonInstance::~MelonInstance()
{
    frameQueue.clear();
    net->UnregisterInstance(instanceId);
    delete nds;
}

bool MelonInstance::loadRom(std::string romPath, std::string sramPath)
{
    unique_ptr<u8[]> romData;
    unique_ptr<u8[]> sramData;
    u32 romFileLength = 0;
    u32 sramFileLength = 0;

    // ROM file loading
    Platform::FileHandle* romFile = Platform::OpenFile(romPath, FileMode::Read);
    if (!romFile)
        return false;

    u64 length = Platform::FileLength(romFile);
    if (!CartridgeImageBounds::IsNDSLengthSupported(length))
    {
        Platform::CloseFile(romFile);
        return false;
    }

    romFileLength = (u32) length;
    Platform::FileRewind(romFile);
    romData.reset(new (std::nothrow) u8[romFileLength]);
    if (!romData)
    {
        Platform::CloseFile(romFile);
        return false;
    }
    size_t nread = Platform::FileRead(romData.get(), (size_t) romFileLength, 1, romFile);
    Platform::CloseFile(romFile);
    if (nread != 1)
    {
        return false;
    }

    // SRAM file loading
    FileHandle* sramFile = Platform::OpenFile(sramPath, FileMode::Read);
    if (!sramFile)
    {
        return false;
    }
    else if (!Platform::CheckFileWritable(sramPath))
    {
        return false;
    }

    sramFileLength = (u32) Platform::FileLength(sramFile);

    FileRewind(sramFile);
    sramData = std::make_unique<u8[]>(sramFileLength);
    FileRead(sramData.get(), sramFileLength, 1, sramFile);
    CloseFile(sramFile);

    NDSCart::NDSCartArgs cartargs{
        // Don't load the SD card itself yet, because we don't know if
        // the ROM is homebrew or not.
        // So this is the card we *would* load if the ROM were homebrew.
        .SDCard = std::nullopt, // getSDCardArgs("DLDI"), // TODO: Re-enable this
        .SRAM = std::move(sramData),
        .SRAMLength = sramFileLength,
    };

    const bool supportsWildSelector = WildEncounterToggle::supportsHeader(romData.get(), romFileLength);
    auto cart = NDSCart::ParseROM(std::move(romData), romFileLength, this, std::move(cartargs));
    if (!cart)
    {
        return false;
    }

    nds->SetNDSCart(std::move(cart));
    wildEncounterSupported = supportsWildSelector;
    const bool wasWildActive = wildEncounterToggle.active();
    wildEncounterToggle.configure(std::nullopt, inputMask);
    wildEncounterToggle.reset(inputMask);
    notifyWildEncounterChange(wasWildActive);
    ndsSave = std::make_unique<SaveManager>(sramPath);

    return true;
}

bool MelonInstance::loadGbaRom(std::string romPath, std::string sramPath)
{
    unique_ptr<u8[]> romData;
    unique_ptr<u8[]> sramData = nullptr;
    u32 romFileLength = 0;
    u32 sramFileLength = 0;

    // ROM file loading
    Platform::FileHandle* romFile = Platform::OpenFile(romPath, FileMode::Read);
    if (!romFile)
        return false;

    u64 length = Platform::FileLength(romFile);
    if (!CartridgeImageBounds::IsGBALengthSupported(length))
    {
        Platform::CloseFile(romFile);
        return false;
    }

    romFileLength = length;
    Platform::FileRewind(romFile);
    romData = make_unique<u8[]>(romFileLength);
    size_t nread = Platform::FileRead(romData.get(), (size_t) romFileLength, 1, romFile);
    Platform::CloseFile(romFile);
    if (nread != 1)
    {
        return false;
    }

    FileHandle* saveFile = Platform::OpenFile(sramPath, FileMode::Read);
    if (!saveFile)
    {
        return false;
    }
    else if (!Platform::CheckFileWritable(sramPath))
    {
        return false;
    }

    sramFileLength = (u32) FileLength(saveFile);

    if (sramFileLength > 0)
    {
        FileRewind(saveFile);
        sramData = std::make_unique<u8[]>(sramFileLength);
        FileRead(sramData.get(), sramFileLength, 1, saveFile);
    }
    CloseFile(saveFile);

    auto cart = GBACart::ParseROM(std::move(romData), romFileLength, std::move(sramData), sramFileLength, this);
    if (!cart)
    {
        return false;
    }

    nds->SetGBACart(std::move(cart));
    gbaSave = std::make_unique<SaveManager>(sramPath);

    return true;
}

void MelonInstance::loadRumblePak()
{
    auto rumblePakCart = GBACart::LoadAddon(GBAAddon_RumblePak, this);
    nds->SetGBACart(std::move(rumblePakCart));
}

void MelonInstance::loadGbaMemoryExpansion()
{
    auto memoryExpansionCart = GBACart::LoadAddon(GBAAddon_RAMExpansion, this);
    nds->SetGBACart(std::move(memoryExpansionCart));
}

void MelonInstance::loadMotionPakHomebrew()
{
    auto motionPakCart = GBACart::LoadAddon(GBAAddon_MotionPakHomebrew, this);
    nds->SetGBACart(std::move(motionPakCart));
}

void MelonInstance::loadMotionPakRetail()
{
    auto motionPakCart = GBACart::LoadAddon(GBAAddon_MotionPakRetail, this);
    nds->SetGBACart(std::move(motionPakCart));
}

bool MelonInstance::bootFirmware()
{
    if (nds->NeedsDirectBoot())
        return false;

    return true;
}

void MelonInstance::start()
{
    auto cart = nds->NDSCartSlot.GetCart();
    if (nds->ConsoleType == 1 && cart != nullptr && cart->GetHeader().IsDSiWare() && !currentConfiguration->showBootScreen)
    {
        auto dsi = (DSi*) nds;
        DSiSupport::SetupDSiDirectBoot(dsi);
    }
    else if (!currentConfiguration->showBootScreen || nds->NeedsDirectBoot())
    {
        // This seems to be unused, but it's required
        std::string romName;
        nds->SetupDirectBoot(romName);
    }
    nds->Start();

    screenshotRenderer->init();
}

void MelonInstance::reset()
{
    const bool wasWildActive = wildEncounterToggle.active();
    wildEncounterToggle.reset(inputMask);
    notifyWildEncounterChange(wasWildActive);
    nds->Reset();
    setBatteryLevels();
    setDateTime();

    // If there is a cart inserted, check if direct boot is required
    if (nds->GetNDSCart())
    {
        if (!currentConfiguration->showBootScreen || nds->NeedsDirectBoot())
        {
            // This seems to be unused, but it's required
            std::string romName;
            nds->SetupDirectBoot(romName);
        }
    }

    rewindManager.Reset();
    retroAchievementsManager->Reset();
    nds->Start();
}

u32 MelonInstance::runFrame()
{
    if (isRenderConfigurationDirty)
    {
        updateRenderer();
        isRenderConfigurationDirty = false;
    }

    int screenWidth;
    int screenHeight;
    if (currentRenderer == Renderer::OpenGl)
    {
        int scale = static_cast<GLRenderer &>(nds->GPU.GetRenderer3D()).GetScaleFactor();
        screenWidth = 256 * scale;
        screenHeight = (192 + 1) * scale;
    }
    else if (currentRenderer == Renderer::Compute)
    {
        auto computeRenderSettings = static_cast<ComputeRenderSettings&>(*currentConfiguration->renderSettings);
        int scale = computeRenderSettings.scale;
        screenWidth = 256 * scale;
        screenHeight = (192 + 1) * scale;
    }
    else
    {
        screenWidth = 256;
        screenHeight = 192 + 1;
    }

    Frame* renderFrame = frameQueue.getRenderFrame();

    EGLDisplay currentDisplay = eglGetCurrentDisplay();
    // Delete old render fence
    if (renderFrame->renderFence)
    {
        eglDestroySyncKHR(currentDisplay, renderFrame->renderFence);
        renderFrame->renderFence = 0;
    }

    // Ensure presentation is finished
    if (renderFrame->presentFence)
    {
        eglWaitSyncKHR(currentDisplay, renderFrame->presentFence, 0);
    }

    // Validate frame after ensuring that the frame has finished presenting
    frameQueue.validateRenderFrame(renderFrame, screenWidth, screenHeight * 2);

    [[unlikely]] if (nds->GPU.GetRenderer3D().NeedsShaderCompile())
    {
        // Compile all required shaders at once
        do
        {
            int currentShader;
            int shadersCount;
            nds->GPU.GetRenderer3D().ShaderCompileStep(currentShader, shadersCount);
        }
        while (nds->GPU.GetRenderer3D().NeedsShaderCompile());
    }

    bool isRendererAccelerated = nds->GPU.GetRenderer3D().Accelerated;
    if (isRendererAccelerated)
    {
        int backBuffer = nds->GPU.FrontBuffer ? 0 : 1;
        nds->GPU.GetRenderer3D().SetOutputTexture(backBuffer, renderFrame->frameTexture);
    }

    WildEncounterMemory wildMemory{*nds};
    const bool wasWildActive = wildEncounterToggle.active();
    wildEncounterToggle.beforeFrame(wildMemory, inputMask);
    notifyWildEncounterChange(wasWildActive);
    u32 nLines = nds->RunFrame();
    wildEncounterToggle.afterFrame(wildMemory);
    retroAchievementsManager->FrameUpdate();

    if (!isRendererAccelerated)
    {
        int frontbuf = nds->GPU.FrontBuffer;
        if (nds->GPU.Framebuffer[frontbuf][0] && nds->GPU.Framebuffer[frontbuf][1])
        {
            glBindTexture(GL_TEXTURE_2D, renderFrame->frameTexture);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 256, 192, GL_RGBA, GL_UNSIGNED_BYTE, nds->GPU.Framebuffer[frontbuf][0].get());
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 192 + 2, 256, 192, GL_RGBA, GL_UNSIGNED_BYTE, nds->GPU.Framebuffer[frontbuf][1].get());
            glBindTexture(GL_TEXTURE_2D, 0);
        }
    }
    else
    {
        // Do nothing. Emulator already renders into the texture, which was set-up above
    }

    bool isSleeping = nds->CPUStop & CPUStop_Sleep;
    if (!isSleeping) [[likely]]
    {
        renderFrame->renderFence = eglCreateSyncKHR(currentDisplay, EGL_SYNC_FENCE_KHR, nullptr);
        glFlush();
        frameQueue.pushRenderedFrame(renderFrame);
    }
    else
    {
        frameQueue.discardRenderedFrame(renderFrame);
    }

    stageSaves();

    frame++;
    bool needsRewindCapture = rewindManager.ShouldCaptureState(frame);
    bool needsScreenshot = screenshotRenderer->isScreenshotPending();

    if (needsRewindCapture || needsScreenshot) [[unlikely]]
        screenshotRenderer->renderScreenshot(&nds->GPU, currentRenderer, renderFrame);

    if (needsRewindCapture)
    {
        auto nextRewindState = rewindManager.GetNextRewindSaveState(frame);
        saveRewindState(nextRewindState);
    }

    return nLines;
}

void MelonInstance::stageSaves()
{
    if (ndsSave)
        ndsSave->CheckFlush();

    if (gbaSave)
        gbaSave->CheckFlush();

    if (firmwareSave)
        firmwareSave->CheckFlush();
}

void MelonInstance::stop()
{
    retroAchievementsManager = nullptr;
    screenshotRenderer->cleanup();
}

void MelonInstance::updateMotionData(float ax, float ay, float az, float rx, float ry, float rz)
{
    motionData[MotionQueryType::MotionAccelerationX].store(ax, std::memory_order_relaxed);
    motionData[MotionQueryType::MotionAccelerationY].store(ay, std::memory_order_relaxed);
    motionData[MotionQueryType::MotionAccelerationZ].store(az, std::memory_order_relaxed);
    motionData[MotionQueryType::MotionRotationX].store(rx, std::memory_order_relaxed);
    motionData[MotionQueryType::MotionRotationY].store(ry, std::memory_order_relaxed);
    motionData[MotionQueryType::MotionRotationZ].store(rz, std::memory_order_relaxed);
}

float MelonInstance::getMotionData(MotionQueryType type)
{
    if (type < MotionQueryType::MotionAccelerationX || type > MotionQueryType::MotionRotationZ)
        return 0.0f;

    return motionData[type].load(std::memory_order_relaxed);
}

void MelonInstance::touchScreen(u16 x, u16 y)
{
    nds->TouchScreen(x, y);
}

void MelonInstance::releaseScreen()
{
    nds->ReleaseScreen();
}

void MelonInstance::pressKey(u32 key)
{
    // Special handling for Lid input
    if (key == 16 + 7)
    {
        nds->SetLidClosed(true);
    }
    else
    {
        inputMask &= ~(1 << key);
        nds->SetKeyMask(inputMask);
    }
}

void MelonInstance::releaseKey(u32 key)
{
    // Special handling for Lid input
    if (key == 16 + 7)
    {
        nds->SetLidClosed(false);
    }
    else
    {
        inputMask |= (1 << key);
        nds->SetKeyMask(inputMask);
    }
}

int MelonInstance::readAudioOutput(s16* buffer, int length)
{
    return nds->SPU.ReadOutput(buffer, length);
}

void MelonInstance::setAudioOutputSkew(double skew)
{
    nds->SPU.SetOutputSkew(skew);
}

bool MelonInstance::takeScreenshot()
{
    return screenshotRenderer->takeScreenshot();
}

void MelonInstance::notifyWildEncounterChange(bool previous)
{
    if (previous == wildEncounterToggle.active()) return;
    int32_t active = wildEncounterToggle.active() ? 1 : 0;
    fireEmulatorEvent(EVENT_WILD_ENCOUNTER_TOGGLE, sizeof(active), &active);
}

void MelonInstance::loadCheats(std::list<Cheat> cheats)
{
    std::vector<ARCode> codeList;
    std::optional<WildEncounterToggle::Selection> wildSelection;

    for (auto cheat : cheats)
    {
        if (wildEncounterSupported)
        {
            if (auto selection = WildEncounterToggle::parse(cheat.code))
            {
                wildSelection = selection;
                continue; // The wrapper owns its transient writes and L+R state.
            }
        }
        ARCode arCode {
            .Enabled = true,
            .Code = cheat.code,
        };
        codeList.push_back(arCode);
    }

    nds->AREngine.Cheats = codeList;
    const bool wasWildActive = wildEncounterToggle.active();
    wildEncounterToggle.configure(wildSelection, inputMask);
    notifyWildEncounterChange(wasWildActive);
}

int MelonInstance::sendNetPacket(u8* data, int length)
{
    return net->SendPacket(data, length, instanceId);
}

int MelonInstance::receiveNetPacket(u8* data)
{
    return net->RecvPacket(data, instanceId);
}

Frame* MelonInstance::getPresentationFrame(std::optional<std::chrono::time_point<std::chrono::steady_clock>> deadline)
{
    return frameQueue.getPresentFrame(deadline);
}

void MelonInstance::updateConfiguration(std::shared_ptr<EmulatorConfiguration> newConfiguration)
{
    if (nds)
    {
        nds->SPU.SetInterpolation(static_cast<AudioInterpolation>(newConfiguration->audioSettings.audioInterpolation));
        nds->SPU.SetDegrade10Bit(static_cast<AudioBitDepth>(newConfiguration->audioSettings.audioBitrate));
    }

    rewindManager.UpdateRewindSettings(newConfiguration->rewindEnabled, newConfiguration->rewindLengthSeconds, newConfiguration->rewindCaptureSpacingSeconds);

    currentConfiguration = newConfiguration;
    isRenderConfigurationDirty = true;
}

void MelonInstance::requestNdsSaveWrite(const u8* saveData, u32 saveLength, u32 writeOffset, u32 writeLength)
{
    if (ndsSave)
        ndsSave->RequestFlush(saveData, saveLength, writeOffset, writeLength);
}

void MelonInstance::requestGbaSaveWrite(const u8* saveData, u32 saveLength, u32 writeOffset, u32 writeLength)
{
    if (gbaSave)
        gbaSave->RequestFlush(saveData, saveLength, writeOffset, writeLength);
}

void MelonInstance::requestFirmwareSaveWrite(const u8* saveData, u32 saveLength, u32 writeOffset, u32 writeLength)
{
    if (firmwareSave)
        firmwareSave->RequestFlush(saveData, saveLength, writeOffset, writeLength);
}

void applyWfcSlotData(Firmware::WifiAccessPoint& accessPoint, const WfcSlotData& slotData)
{
    accessPoint.Status = slotData.enabled ? Firmware::AccessPointStatus::Normal : Firmware::AccessPointStatus::NotConfigured;

    size_t nameLength = strnlen(slotData.name, sizeof(accessPoint.SSID));
    if (nameLength > 0)
    {
        memset(accessPoint.SSID, 0, sizeof(accessPoint.SSID));
        memcpy(accessPoint.SSID, slotData.name, nameLength);
        accessPoint.SSIDLength = (u8) nameLength;
    }

    memcpy(accessPoint.PrimaryDns.data(), slotData.primaryDns, sizeof(slotData.primaryDns));
    memcpy(accessPoint.SecondaryDns.data(), slotData.secondaryDns, sizeof(slotData.secondaryDns));
    accessPoint.UpdateChecksum();
}

void extractWfcSlotData(const Firmware::WifiAccessPoint& accessPoint, WfcSlotData& slotData)
{
    slotData.enabled = accessPoint.Status != Firmware::AccessPointStatus::NotConfigured;

    // The SSID is a fixed 32-byte field and isn't guaranteed to be NUL-terminated when full.
    size_t nameLength = strnlen(accessPoint.SSID, sizeof(accessPoint.SSID));
    memset(slotData.name, 0, sizeof(slotData.name));
    memcpy(slotData.name, accessPoint.SSID, nameLength);

    memcpy(slotData.primaryDns, accessPoint.PrimaryDns.data(), sizeof(slotData.primaryDns));
    memcpy(slotData.secondaryDns, accessPoint.SecondaryDns.data(), sizeof(slotData.secondaryDns));
}

bool wfcAccessPointsInBounds(const Firmware& firmware)
{
    if (!firmware.IsLayoutValid())
        return false;

    u32 userDataOffset = firmware.GetUserDataOffset();
    // The access points sit 0x400 bytes before the user settings and the DSi's extended ones
    // 0xA00 bytes before them, so anything closer to the start of the image is out of bounds.
    if (userDataOffset < 0xA00 || userDataOffset > firmware.Length())
        return false;

    u32 accessPointsEnd = firmware.GetWifiAccessPointOffset() + sizeof(firmware.GetAccessPoints());
    return accessPointsEnd <= firmware.Length();
}

// The extended (DSi) blocks are only meaningful in DSi firmware; in a real DS image that area
// holds unrelated firmware data and must not be touched. The generated firmware is the other
// case that owns it: its Wi-fi region is saved to wfcsettings.bin, extended blocks first
// (see generateFirmware() in EmulatorArgsBuilder.cpp).
static bool firmwareOwnsExtendedAccessPoints(const Firmware& firmware)
{
    return firmware.GetHeader().ConsoleType == Firmware::FirmwareConsoleType::DSi
        || firmware.IsGenerated();
}

bool MelonInstance::readWfcSlots(WfcSlotData slots[3])
{
    const Firmware& firmware = nds->SPI.GetFirmware();
    if (!wfcAccessPointsInBounds(firmware))
        return false;

    const auto& accessPoints = firmware.GetAccessPoints();
    for (int i = 0; i < 3; i++)
        extractWfcSlotData(accessPoints[i], slots[i]);

    return true;
}

bool MelonInstance::writeWfcSlot(int slot, const WfcSlotData& slotData)
{
    if (slot < 0 || slot > 2)
        return false;

    Firmware& firmware = nds->SPI.GetFirmware();
    if (!wfcAccessPointsInBounds(firmware))
        return false;

    Firmware::WifiAccessPoint& accessPoint = firmware.GetAccessPoints()[slot];
    applyWfcSlotData(accessPoint, slotData);

    if (firmwareOwnsExtendedAccessPoints(firmware))
    {
        // The extended block embeds its own copy of the base access point; keep them in sync so
        // the slot behaves the same in DS and DSi mode.
        u32 extendedOffset = firmware.GetExtendedAccessPointOffset();
        if (extendedOffset + sizeof(firmware.GetExtendedAccessPoints()) <= firmware.Length())
        {
            Firmware::ExtendedWifiAccessPoint& extendedAccessPoint = firmware.GetExtendedAccessPoints()[slot];
            extendedAccessPoint.Data.Base = accessPoint;
            extendedAccessPoint.UpdateChecksum();
        }
    }

    // Same request the console's own firmware writes end up making (SPI.cpp,
    // FirmwareMem::Release()): the platform layer decides whether that means the whole firmware
    // file or just the Wi-fi region of wfcsettings.bin.
    const auto range = wfcSaveRange(firmware.GetWifiAccessPointOffset(), sizeof(firmware.GetAccessPoints()),
                                   firmware.GetExtendedAccessPointOffset(), firmwareOwnsExtendedAccessPoints(firmware));
    Platform::WriteFirmware(firmware, range.offset, range.length, this);
    return true;
}

bool MelonInstance::saveState(Savestate* state)
{
    if (!retroAchievementsManager->DoSavestate(state))
        return false;

    return nds->DoSavestate(state);
}

bool MelonInstance::loadState(Savestate* state)
{
    const bool wasWildActive = wildEncounterToggle.active();
    wildEncounterToggle.reset(inputMask);
    notifyWildEncounterChange(wasWildActive);
    if (!retroAchievementsManager->DoSavestate(state))
        return false;

    if (nds->DoSavestate(state))
    {
        setBatteryLevels();
        // The savestate carries its own RTC, so don't stamp the host time over it. Only the
        // opt-in sync may touch it, and only forward (see syncRTC).
        syncRTC();
        return true;
    }
    else
    {
        return false;
    }
}

RewindWindow MelonInstance::getRewindWindow()
{
    return RewindWindow {
        .currentFrame = frame,
        .rewindStates = rewindManager.GetRewindWindow(),
    };
}

bool MelonInstance::loadRewindState(RewindSaveState rewindSaveState)
{
    Savestate* savestate = new Savestate(rewindSaveState.buffer, rewindSaveState.bufferContentSize, false);
    if (savestate->Error)
    {
        delete savestate;
        return false;
    }

    bool result = loadState(savestate);
    if (result)
    {
        frame = rewindSaveState.frame;
        rewindManager.OnRewindFromState(rewindSaveState);
    }

    delete savestate;

    return result;
}

void MelonInstance::setupAchievements(
    std::list<RetroAchievements::RAAchievement> achievements,
    std::list<RetroAchievements::RALeaderboard> leaderboards,
    std::optional<std::string> richPresenceScript
)
{
    if (instanceId == 0)
    {
        retroAchievementsManager->LoadAchievements(achievements);
        retroAchievementsManager->LoadLeaderboards(leaderboards);
        if (richPresenceScript)
            retroAchievementsManager->SetupRichPresence(*richPresenceScript);
    }
}

void MelonInstance::unloadRetroAchievementsData()
{
    retroAchievementsManager->UnloadEverything();
}

std::string MelonInstance::getRichPresenceStatus()
{
    if (instanceId == 0 && retroAchievementsManager)
        return retroAchievementsManager->GetRichPresenceStatus();
    else
        return "";
}

std::vector<RetroAchievements::RARuntimeAchievement> MelonInstance::getRuntimeAchievements()
{
    if (instanceId == 0 && retroAchievementsManager)
        return retroAchievementsManager->GetRuntimeAchievements();
    else
        return { };
}

void MelonInstance::updateRenderer()
{
    Renderer newRenderer = currentConfiguration->renderer;

    if (newRenderer != currentRenderer)
    {
        std::unique_ptr<Renderer3D> renderer;
        switch (newRenderer)
        {
            case Renderer::Software:
                renderer = std::make_unique<SoftRenderer>();
                break;
            case Renderer::OpenGl:
                renderer = GLRenderer::New();
                break;
            case Renderer::Compute:
                renderer = ComputeRenderer::New();
                break;
            default: __builtin_unreachable();
        }
        if (!renderer)
        {
            // The core installs SoftRenderer for a null factory result. Track that actual
            // type for frame dimensions/screenshots and leave the requested settings intact.
            nds->GPU.SetRenderer3D(nullptr);
            currentRenderer = Renderer::Software;
            Log(LogLevel::Error, "Failed to initialize renderer %d; using software rendering\n", static_cast<int>(newRenderer));
            return;
        }
        nds->GPU.SetRenderer3D(std::move(renderer));
        currentRenderer = newRenderer;
    }

    switch (newRenderer)
    {
        case Renderer::Software:
        {
            auto softwareRenderSettings = static_cast<SoftwareRenderSettings&>(*currentConfiguration->renderSettings);
            static_cast<SoftRenderer&>(nds->GPU.GetRenderer3D()).SetThreaded(softwareRenderSettings.threadedRendering, nds->GPU);
            break;
        }
        case Renderer::OpenGl:
        {
            auto glRenderSettings = static_cast<OpenGlRenderSettings&>(*currentConfiguration->renderSettings);
            static_cast<GLRenderer&>(nds->GPU.GetRenderer3D()).SetRenderSettings(glRenderSettings.betterPolygons, glRenderSettings.scale);
            break;
        }
        case Renderer::Compute:
        {
            auto computeRenderSettings = static_cast<ComputeRenderSettings&>(*currentConfiguration->renderSettings);
            static_cast<ComputeRenderer&>(nds->GPU.GetRenderer3D()).SetRenderSettings(computeRenderSettings.scale,computeRenderSettings.highResCoordinates);
            break;
        }
        default: __builtin_unreachable();
    }
}

void MelonInstance::setBatteryLevels()
{
    if (consoleType == 1)
    {
        auto dsi = static_cast<DSi*>(nds);
        dsi->I2C.GetBPTWL()->SetBatteryLevel(DSi_BPTWL::batteryLevel_Full);
        dsi->I2C.GetBPTWL()->SetBatteryCharging(false);
    }
    else
    {
        nds->SPI.GetPowerMan()->SetBatteryLevelOkay(true);
    }
}

void MelonInstance::setDateTime()
{
    std::time_t t = std::time(0);
    std::tm* now = std::localtime(&t);

    nds->RTC.SetDateTime(now->tm_year + 1900, now->tm_mon + 1, now->tm_mday, now->tm_hour, now->tm_min, now->tm_sec);
}

// Opt-in "Synchronize clock to device time" option. The DS RTC is driven by the emulator's
// own scheduler (RTC::ScheduleTimer), not by the host clock, so a DS console clock never
// resyncs on its own after the app spends real time paused or backgrounded. Left disabled,
// this is invisible: existing saves/behavior are unaffected. Enabled, it is called once when
// the emulator resumes (see onResumed(), called from MelonDSAndroid::resume() in MelonDS.cpp)
// -- construction already covers the first frame after load via its own unconditional
// setDateTime() call -- and forwards the decision to the pure function in RtcSync.h, which
// never asks to step the clock backward -- see there for why.
void MelonInstance::syncRTC()
{
    if (!currentConfiguration->rtcSyncToHost)
        return;

    int year, month, day, hour, minute, second;
    nds->RTC.GetDateTime(year, month, day, hour, minute, second);

    std::tm dsTm{};
    dsTm.tm_year = year - 1900;
    dsTm.tm_mon = month - 1;
    dsTm.tm_mday = day;
    dsTm.tm_hour = hour;
    dsTm.tm_min = minute;
    dsTm.tm_sec = second;
    dsTm.tm_isdst = -1;
    std::time_t dsEpochSeconds = std::mktime(&dsTm);

    std::time_t hostEpochSeconds = std::time(nullptr);

    if (dsEpochSeconds == (std::time_t) -1)
    {
        // Couldn't normalize the DS clock (e.g. it holds an out-of-range value). Log and
        // leave it alone rather than acting on garbage -- there is no safe direction to
        // stamp it in, and we never step the clock backward.
        Platform::Log(Platform::LogLevel::Warn, "syncRTC: mktime failed to normalize DS RTC value, skipping sync\n");
        return;
    }

    if (rtcSyncAction(dsEpochSeconds, hostEpochSeconds, kRtcSyncMinDriftSeconds) == RtcSyncAction::ADVANCE)
    {
        setDateTime();
    }
}

void MelonInstance::onResumed()
{
    syncRTC();
}

void MelonInstance::saveRewindState(RewindSaveState* rewindSaveState)
{
    Savestate* savestate = new Savestate(rewindSaveState->buffer, rewindSaveState->bufferSize, true);
    if (saveState(savestate))
    {
        rewindSaveState->bufferContentSize = savestate->Length();
        memcpy(rewindSaveState->screenshot, screenshotRenderer->getScreenshot(), rewindSaveState->screenshotSize);
    }

    delete savestate;
}

}
