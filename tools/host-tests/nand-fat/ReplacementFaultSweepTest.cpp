// One-shot physical NAND write failures against the production replacement path.
#define main nand_fat_fixture_main
#include "NandFatTest.cpp"
#undef main

int main(int argc, char** argv)
{
    const unsigned first = argc > 1 ? unsigned(std::strtoul(argv[1], nullptr, 10)) : 131;
    const unsigned last = argc > 2 ? unsigned(std::strtoul(argv[2], nullptr, 10)) : 135;
    require(first > 0 && last >= first && last <= 200, "bounded fault range");

    std::vector<u8> app(65536, 0xA7);
    NDSHeader header{};
    header.DSiTitleIDLow = 0x54455354;
    header.DSiTitleIDHigh = 0x30004;
    header.DSiPublicSavSize = 65536;
    std::memcpy(app.data(), &header, sizeof(header));
    const auto tmd = metadata(app);
    const std::vector<u8> original(65536, 0x51);
    const std::vector<u8> replacement(65536, 0x62);
    FILE* source = std::tmpfile();
    require(source != nullptr &&
            std::fwrite(replacement.data(), replacement.size(), 1, source) == 1 &&
            std::fflush(source) == 0, "anonymous authored source");
    char path[64];
#if defined(__ANDROID__) || defined(__linux__)
    std::snprintf(path, sizeof(path), "/proc/self/fd/%d", fileno(source));
#else
    std::snprintf(path, sizeof(path), "/dev/fd/%d", fileno(source));
#endif
    const char* savePath = "0:/title/00030004/54455354/data/public.sav";
    const char* backupPath = "0:/title/00030004/54455354/data/public.sav.melonds-old";
    const char* stagedPath = "0:/title/00030004/54455354/data/public.sav.melonds-new";

    for (unsigned ordinal = first; ordinal <= last; ++ordinal)
    {
        Fixture fixture;
        require(fixture.mount->ImportTitle(app.data(), app.size(), tmd, false), "install authored title");
        require(fixture.mount->ImportFile(savePath, original.data(), original.size()), "seed save");
        fixture.remount();
        NandFault::reset();
        NandFault::failWriteCall = ordinal;
        std::rewind(source);
        const bool imported = fixture.mount->ImportTitleData(
            0x30004, 0x54455354, TitleData_PublicSav, path);
        const unsigned calls = NandFault::writeCall;
        const unsigned delivered = NandFault::writeFailures;
        NandFault::reset();
        fixture.remount();
        FF_FILINFO current{}, backup{}, staged{};
        const FRESULT currentStatus = f_stat(savePath, &current);
        const FRESULT backupStatus = f_stat(backupPath, &backup);
        const FRESULT stagedStatus = f_stat(stagedPath, &staged);
        const auto generation = [&](const char* entry, FRESULT status, const FF_FILINFO& info) {
            if (status != FR_OK || info.fsize != original.size()) return '-';
            const auto bytes = fixture.read(entry);
            if (bytes == original) return 'O';
            if (bytes == replacement) return 'N';
            return '?';
        };
        const char currentGeneration = generation(savePath, currentStatus, current);
        const char backupGeneration = generation(backupPath, backupStatus, backup);
        const char stagedGeneration = generation(stagedPath, stagedStatus, staged);
        const bool exact = currentGeneration == (imported ? 'N' : 'O');
        const bool clean = backupStatus == FR_NO_FILE && stagedStatus == FR_NO_FILE;
        std::printf("fault=%u calls=%u delivered=%u imported=%d current=%d/%c backup=%d/%c staged=%d/%c exact=%d clean=%d\n",
                    ordinal, calls, delivered, imported, currentStatus, currentGeneration,
                    backupStatus, backupGeneration, stagedStatus, stagedGeneration, exact, clean);
        expect(exact, "committed save generation survives one-shot NAND write failure");
        if (ordinal == 134 && delivered && imported && !clean) {
            expect(!fixture.mount->ImportTitleData(0x30004, 0x54455354,
                TitleData_PublicSav, "/definitely/missing/melonds-public.sav"),
                "missing follow-up source returns failure");
            fixture.remount();
            FF_FILINFO after{}, oldAfter{};
            expect(f_stat(savePath, &after) == FR_OK &&
                after.fsize == replacement.size() &&
                fixture.read(savePath) == replacement,
                "backup cleanup retry preserves the committed replacement");
            expect(f_stat(backupPath, &oldAfter) == FR_NO_FILE,
                "backup cleanup succeeds on the next call");
        }
    }
    std::fclose(source);
    std::printf("ReplacementFaultSweepTest: failures=%d\n", failures);
    return failures ? 1 : 0;
}
