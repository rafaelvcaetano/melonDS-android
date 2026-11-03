// Exercises the production NANDMount on an encrypted, synthetic FatFs image.
#define main nand_fat_fixture_main
#include "NandFatTest.cpp"
#undef main
#include <utility>

int main()
{
    std::setvbuf(stdout, nullptr, _IOLBF, 0);
    Fixture fixture;
    std::vector<u8> app(65536, 0xA7);
    NDSHeader header{};
    header.DSiTitleIDLow = 0x54455354;
    header.DSiTitleIDHigh = 0x30004;
    header.DSiPublicSavSize = 65536;
    std::memcpy(app.data(), &header, sizeof(header));
    const auto tmd = metadata(app);
    require(fixture.mount->ImportTitle(app.data(), app.size(), tmd, false), "install authored title");

    const char* const savePath = "0:/title/00030004/54455354/data/public.sav";
    std::vector<u8> original(65536, 0x51);
    std::vector<u8> replacement(65536, 0x62);
    require(fixture.mount->ImportFile(savePath, original.data(), original.size()), "seed save");
    fixture.remount();
    require(fixture.read(savePath) == original, "seed survives remount");

    expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354, TitleData_PublicSav,
                                          "/definitely/missing/melonds-public.sav"),
           "missing source returns failure");
    auto checkOldAndReset = [&](const char* entryMessage, const char* bytesMessage) {
        fixture.remount();
        FF_FILINFO info{};
        const FRESULT status = f_stat(savePath, &info);
        expect(status == FR_OK, entryMessage);
        if (status == FR_OK)
            expect(fixture.read(savePath) == original, bytesMessage);
        require(fixture.mount->ImportFile(savePath, original.data(), original.size()),
                "restore independent seed for next fault");
        fixture.remount();
    };
    checkOldAndReset("missing source preserves prior save entry",
                     "missing source preserves exact bytes");

    FILE* source = std::tmpfile();
    require(source != nullptr, "anonymous replacement source");
    require(std::fwrite(replacement.data(), replacement.size(), 1, source) == 1 &&
            std::fflush(source) == 0, "write replacement source");
    std::rewind(source);
    char path[64];
#if defined(__ANDROID__) || defined(__linux__)
    std::snprintf(path, sizeof(path), "/proc/self/fd/%d", fileno(source));
#else
    std::snprintf(path, sizeof(path), "/dev/fd/%d", fileno(source));
#endif

    NandFault::inputLength = replacement.size();
    std::rewind(source);
    expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354,
                                           TitleData_PublicSav, path),
           "source read failure returns failure");
    expect(NandFault::readFailures > 0, "source read fault was delivered");
    NandFault::reset();
    checkOldAndReset("source read failure preserves old entry",
                     "source read failure preserves old bytes");

    NandFault::writeStart = fixture.dataStart();
    std::rewind(source);
    expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354,
                                           TitleData_PublicSav, path),
           "NAND write failure returns failure");
    expect(NandFault::writeFailures > 0, "NAND write fault was delivered");
    NandFault::reset();
    checkOldAndReset("NAND write failure preserves old entry",
                     "NAND write failure preserves old bytes");

    NandFault::reset();
    std::rewind(source);
    require(fixture.mount->ImportTitleData(0x30004, 0x54455354,
                                           TitleData_PublicSav, path), "retry replacement");
    std::printf("replacement physical write calls=%u\n", NandFault::writeCall);
    fixture.remount();
    expect(fixture.read(savePath) == replacement, "successful retry replaces exact bytes");

    const char* const backupPath =
        "0:/title/00030004/54455354/data/public.sav.melonds-old";
    require(f_rename(savePath, backupPath) == FR_OK,
            "simulate interruption after moving current save aside");
    fixture.remount();
    expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354, TitleData_PublicSav,
                                          "/definitely/missing/melonds-public.sav"),
           "failed import following interruption returns failure");
    fixture.remount();
    expect(fixture.read(savePath) == replacement,
           "interrupted replacement recovers the latest committed save");
    FF_FILINFO backupInfo{};
    expect(f_stat(backupPath, &backupInfo) == FR_NO_FILE,
           "recovery removes the stranded backup entry");

    fixture.leaveFree(8192);
    std::rewind(source);
    expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354,
                                           TitleData_PublicSav, path),
           "insufficient NAND space returns failure");
    fixture.remount();
    expect(fixture.read(savePath) == replacement,
           "insufficient NAND space preserves the latest save");
    require(f_unlink("0:/filler.bin") == FR_OK, "remove synthetic space filler");

    for (const auto& save : {
             std::pair<int, const char*>{TitleData_PrivateSav,
                 "0:/title/00030004/54455354/data/private.sav"},
             std::pair<int, const char*>{TitleData_BannerSav,
                 "0:/title/00030004/54455354/data/banner.sav"}}) {
        require(fixture.mount->ImportFile(save.second, original.data(), original.size()),
                "seed equivalent save type");
        expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354, save.first,
                                              "/definitely/missing/melonds-public.sav"),
               "equivalent save type reports missing source");
        fixture.remount();
        expect(fixture.read(save.second) == original,
               "equivalent save type retains exact bytes");
    }
    std::fclose(source);
    std::printf("ExistingSaveReplacementTest: failures=%d\n", failures);
    return failures ? 1 : 0;
}
