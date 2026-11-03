// Authored FAT and encrypted NAND fixtures. No BIOS, firmware or commercial ROM.
#include <DSi_NAND.h>
#include <FATIO.h>
#include <Platform.h>
#include <fatfs/diskio.h>
#include <sha1/sha1.hpp>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <vector>
#include <unistd.h>
#include "FaultPlatform.h"

using namespace melonDS;
using namespace melonDS::DSi_NAND;

static int failures = 0;
static void expect(bool ok, const char* what)
{
    if (!ok) { std::fprintf(stderr, "FAIL: %s\n", what); ++failures; }
}
static void require(bool ok, const char* what)
{
    if (!ok) { std::fprintf(stderr, "FIXTURE ERROR: %s\n", what); std::exit(2); }
}

class Fixture
{
    // 4 MiB plain FAT, then written through NANDMount's real encryption callbacks.
    static constexpr size_t Size = 4 * 1024 * 1024;
    static constexpr size_t Base = 0x10EE00;
    std::unique_ptr<NANDImage> image;
public:
    std::unique_ptr<NANDMount> mount;
    Fixture()
    {
        std::vector<u8> volume(Size);
        ff_disk_open([&](BYTE* out, LBA_t sector, UINT count) {
            if ((size_t(sector) + count) * 512 > volume.size()) return UINT(0);
            std::memcpy(out, volume.data() + size_t(sector) * 512, count * 512);
            return count;
        }, [&](const BYTE* in, LBA_t sector, UINT count) {
            if ((size_t(sector) + count) * 512 > volume.size()) return UINT(0);
            std::memcpy(volume.data() + size_t(sector) * 512, in, count * 512);
            return count;
        }, Size / 512);
        FF_MKFS_PARM format{};
        format.fmt = FM_FAT | FM_SFD;
        format.au_size = 512;
        u8 work[4096];
        require(f_mkfs("0:", &format, work, sizeof(work)) == FR_OK, "format authored FAT");
        ff_disk_close();

        FILE* file = std::tmpfile();
        require(file != nullptr, "anonymous backing file");
        require(std::fseek(file, Base + Size, SEEK_SET) == 0, "size backing file");
        u8 footer[64]{};
        std::memcpy(footer, "DSi eMMC CID/CPU", 16);
        require(std::fwrite(footer, sizeof(footer), 1, file) == 1, "synthetic footer");
        DSiKey esKey{};
        image = std::make_unique<NANDImage>(reinterpret_cast<Platform::FileHandle*>(file), esKey);
        require(bool(*image), "synthetic NAND identity");
        mount = std::make_unique<NANDMount>(*image);
        require((disk_initialize(0) & STA_NOINIT) == 0, "initialize encrypted disk");
        for (size_t i = 0; i < Size; i += sizeof(work))
            require(disk_write(0, volume.data() + i, i / 512, sizeof(work) / 512) == RES_OK,
                    "write encrypted FAT fixture");
        remount();
        require(f_mkdir("0:/title") == FR_OK && f_mkdir("0:/ticket") == FR_OK, "root directories");
    }
    void remount()
    {
        mount.reset();
        mount = std::make_unique<NANDMount>(*image);
        DWORD freeClusters;
        FATFS* fs;
        require(f_getfree("0:", &freeClusters, &fs) == FR_OK, "mount encrypted FAT");
    }
    size_t freeBytes()
    {
        DWORD clusters; FATFS* fs;
        require(f_getfree("0:", &clusters, &fs) == FR_OK, "read free space");
        return size_t(clusters) * fs->csize * 512;
    }
    uint64_t dataStart()
    {
        DWORD clusters; FATFS* fs;
        require(f_getfree("0:", &clusters, &fs) == FR_OK, "find FAT data region");
        return Base + uint64_t(fs->database) * 512;
    }
    void leaveFree(size_t bytes)
    {
        const auto free = freeBytes();
        require(free > bytes, "filler fits");
        std::vector<u8> filler(free - bytes, 0xB6);
        FF_FIL file; UINT written;
        require(f_open(&file, "0:/filler.bin", FA_CREATE_ALWAYS | FA_WRITE) == FR_OK, "open filler");
        require(f_write(&file, filler.data(), filler.size(), &written) == FR_OK && written == filler.size(), "fill volume");
        require(f_close(&file) == FR_OK, "close filler");
    }
    std::vector<u8> read(const char* path)
    {
        FF_FIL file;
        require(f_open(&file, path, FA_READ) == FR_OK, "open readback");
        std::vector<u8> bytes(f_size(&file)); UINT read;
        require(f_read(&file, bytes.data(), bytes.size(), &read) == FR_OK && read == bytes.size(), "read actual bytes");
        require(f_close(&file) == FR_OK, "close readback");
        return bytes;
    }
};

static DSi_TMD::TitleMetadata metadata(const std::vector<u8>& app)
{
    DSi_TMD::TitleMetadata tmd{};
    // Big-endian TMD title 00030004/54455354; content version zero.
    auto* bytes = reinterpret_cast<u8*>(&tmd);
    bytes[0x18C] = 0; bytes[0x18D] = 3; bytes[0x18E] = 0; bytes[0x18F] = 4;
    bytes[0x190] = 'T'; bytes[0x191] = 'E'; bytes[0x192] = 'S'; bytes[0x193] = 'T';
    u64 length = app.size();
    for (int index = 7; index >= 0; --index) {
        tmd.Contents.ContentSize[index] = static_cast<u8>(length);
        length >>= 8;
    }
    SHA1_CTX sha;
    SHA1Init(&sha);
    SHA1Update(&sha, app.data(), static_cast<u32>(app.size()));
    SHA1Final(tmd.Contents.ContentSha1Hash, &sha);
    require(tmd.GetCategory() == 0x30004 && tmd.GetID() == 0x54455354, "TMD identity");
    return tmd;
}

int main(int argc, char** argv)
{
    std::setvbuf(stdout, nullptr, _IOLBF, 0);
    const bool ioOnly = argc == 2 && std::strcmp(argv[1], "--io-only") == 0;
    if (!ioOnly)
    for (bool fromFile : {false, true})
    for (int saveKind : {0, 1, 2, 3})
    for (bool full : {false, true})
    {
        Fixture f;
        std::vector<u8> sentinel(1024, 0x57);
        require(f.mount->ImportFile("0:/sentinel.bin", sentinel.data(), sentinel.size()), "sentinel import");
        if (full) f.leaveFree(16384);
        std::vector<u8> app(65536, 0xA7);
        NDSHeader header{};
        header.DSiTitleIDLow = 0x54455354;
        header.DSiTitleIDHigh = 0x30004;
        if (saveKind == 1) header.DSiPublicSavSize = 65536;
        if (saveKind == 2) header.DSiPrivateSavSize = 65536;
        if (saveKind == 3) header.AppFlags |= 4;
        std::memcpy(app.data(), &header, sizeof(header));
        const auto tmd = metadata(app);
        char path[128];
#if defined(__ANDROID__)
        std::snprintf(path, sizeof(path), "/data/local/tmp/melonds-nand-input.XXXXXX");
#else
        std::snprintf(path, sizeof(path), "/tmp/melonds-nand-input.XXXXXX");
#endif
        int fd = mkstemp(path);
        require(fd >= 0, "temporary synthetic input");
        FILE* input = fdopen(fd, "wb");
        require(input && std::fwrite(app.data(), app.size(), 1, input) == 1, "write input");
        require(std::fclose(input) == 0, "close input");
        const bool imported = fromFile ? f.mount->ImportTitle(path, tmd, false)
            : f.mount->ImportTitle(app.data(), app.size(), tmd, false);
        require(unlink(path) == 0, "remove authored input");
        std::printf("title import file=%d save=%d full=%d returned=%d free=%zu\n", fromFile, saveKind, full, imported, f.freeBytes());
        expect(imported == !full, "title import reports physical exhaustion");
        f.remount();
        if (imported)
        {
            auto actual = f.read("0:/title/00030004/54455354/content/00000000.app");
            std::printf("persisted executable: %zu / %zu bytes\n", actual.size(), app.size());
            expect(actual == app, "successful import persists every executable byte");
        }
        expect(f.read("0:/sentinel.bin") == sentinel, "unrelated data survives remount");
        f.mount->DeleteTitle(tmd.GetCategory(), tmd.GetID());
        FF_FILINFO info;
        expect(f_stat("0:/title/00030004/54455354", &info) == FR_NO_FILE,
               "explicit cleanup removes failed title directory");
        expect(f_stat("0:/ticket/00030004/54455354.tik", &info) == FR_NO_FILE,
               "explicit cleanup removes ticket");
        if (full) require(f_unlink("0:/filler.bin") == FR_OK, "remove synthetic filler");
        require(f.mount->ImportTitle(app.data(), app.size(), tmd, false), "retry succeeds after cleanup");
        f.remount();
        expect(f.read("0:/title/00030004/54455354/content/00000000.app") == app, "retry exact bytes after remount");
        if (saveKind == 1 || saveKind == 2)
        {
            auto save = f.read(saveKind == 1 ? "0:/title/00030004/54455354/data/public.sav"
                : "0:/title/00030004/54455354/data/private.sav");
            expect(save.size() == 65536, "save length survives remount");
            expect(save[11] == 0 && save[12] == 2 && save[17] == 32 && save[18] == 0,
                   "FAT bytes per sector and root entry count match independent constants");
            expect(save[39] == 0x78 && save[40] == 0x56 && save[41] == 0x34 && save[42] == 0x12
                && save[510] == 0x55 && save[511] == 0xAA, "save serial and boot signature use little endian bytes");
        }
    }
    for (size_t length : {size_t(1), size_t(8192)})
    {
        Fixture f;
        std::vector<u8> sentinel(1024, 0x57);
        require(f.mount->ImportFile("0:/sentinel.bin", sentinel.data(), sentinel.size()), "error control sentinel");
        std::vector<u8> input(length, 0x39);
        NandFault::writeStart = f.dataStart();
        const bool imported = f.mount->ImportFile("0:/error.bin", input.data(), input.size());
        expect(NandFault::writeFailures > 0, "real encrypted disk write delivers I/O error");
        expect(!imported, length == 1 ? "final buffered sector failure is not success"
                                      : "direct sector write failure is not success");
        NandFault::reset();
        f.remount();
        expect(f.read("0:/sentinel.bin") == sentinel, "write failure preserves unrelated data");
        require(f.mount->ImportFile("0:/error.bin", input.data(), input.size()), "I/O error retry");
        f.remount();
        expect(f.read("0:/error.bin") == input, "I/O retry persists exact bytes");
    }
    {
        Fixture f;
        FILE* file = std::tmpfile();
        require(file != nullptr, "read error fixture");
        std::vector<u8> input(65536, 0x39);
        require(std::fwrite(input.data(), input.size(), 1, file) == 1 && std::fflush(file) == 0, "read error input bytes");
        std::rewind(file);
        // Use the already open original via a proc/dev-fd path; ImportFile owns its own handle.
        char path[64];
#if defined(__ANDROID__) || defined(__linux__)
        std::snprintf(path, sizeof(path), "/proc/self/fd/%d", fileno(file));
#else
        std::snprintf(path, sizeof(path), "/dev/fd/%d", fileno(file));
#endif
        NandFault::inputLength = input.size();
        const bool imported = f.mount->ImportFile("0:/read-error.bin", path, input.size());
        expect(NandFault::readFailures > 0, "source read delivers I/O error");
        expect(!imported, "source read failure is not success");
        NandFault::reset();
        std::rewind(file);
        std::vector<u8> original(input.size());
        require(std::fread(original.data(), original.size(), 1, file) == 1, "read original fixture");
        expect(original == input, "failed import preserves source bytes");
        std::rewind(file);
        require(f.mount->ImportFile("0:/read-error.bin", path, input.size()), "source read retry");
        f.remount();
        expect(f.read("0:/read-error.bin") == input, "source retry persists exact bytes");
        std::fclose(file);
    }
    std::printf("NandFatTest: %d failure(s)\n", failures);
    return failures ? 1 : 0;
}
