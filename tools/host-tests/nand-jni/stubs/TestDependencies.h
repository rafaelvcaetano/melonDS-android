#pragma once
#include <algorithm>
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <memory>
#include <string>
#include <vector>
#include "DSi_TMD.h"
using melonDS::u8;
using melonDS::u32;
struct NDSHeader { u32 DSiPublicSavSize = 1024; u32 DSiPrivateSavSize = 2048; int AppFlags = 0; };
struct NDSBanner { std::array<u8,512> Icon{}; std::array<u32,16> Palette{}; char16_t EnglishTitle[128] = u"Test title\nTest producer"; };
namespace Audit {
inline std::string biosPath;
inline std::string nandPath;
inline std::filesystem::path titleRoot;
inline bool failExecutableImport = false;
inline bool titleInfoValid = true;
inline bool unterminatedTitle = false;
inline bool malformedTitle = false;
inline std::filesystem::path titlePath(u32 category, u32 id) {
    char relative[100]; std::snprintf(relative, sizeof(relative), "%08x/%08x", category, id);
    return titleRoot / relative;
}
}
namespace melonDS {
namespace Platform {
enum class FileMode { Read, ReadWriteExisting };
enum class FileSeekOrigin { Start };
inline FILE* OpenFile(const std::string& path, FileMode mode) { return std::fopen(path.c_str(), mode == FileMode::Read ? "rb" : "r+b"); }
inline FILE* OpenLocalFile(const std::string& path, FileMode mode) { return OpenFile(path, mode); }
inline bool FileSeek(FILE* file, int offset, FileSeekOrigin) { return std::fseek(file, offset, SEEK_SET) == 0; }
inline size_t FileRead(void* data, int size, int count, FILE* file) { return std::fread(data, size, count, file); }
inline bool CloseFile(FILE* file) { return std::fclose(file) == 0; }
}
namespace DSi_NAND {
// NAND encryption is outside this test. Use host files as the mounted storage boundary.
// Constructor owns the FILE as the production NANDImage does.
struct NANDImage {
    FILE* file;
    NANDImage(FILE* value, const u8*) : file(value) {}
    ~NANDImage() { if (file) std::fclose(file); }
    explicit operator bool() const { return file != nullptr; }
};
struct NANDMount {
    std::vector<u32> titles;
    explicit NANDMount(NANDImage&) {}
    void ListTitles(u32, std::vector<u32>& output) { output = titles; }
    bool GetTitleInfo(u32, u32, u32& version, NDSHeader* header, NDSBanner* banner) {
        if (!Audit::titleInfoValid) return false;
        version = 0;
        if (header) *header = NDSHeader{};
        if (banner) {
            *banner = NDSBanner{};
            if (Audit::unterminatedTitle)
                std::fill(std::begin(banner->EnglishTitle), std::end(banner->EnglishTitle), u'A');
            if (Audit::malformedTitle) {
                banner->EnglishTitle[0] = static_cast<char16_t>(0xD800);
                banner->EnglishTitle[1] = u'X';
                banner->EnglishTitle[2] = u'\0';
            }
        }
        return true;
    }
    bool TitleExists(u32 category, u32 id) { return std::filesystem::exists(Audit::titlePath(category, id) / "complete.app"); }
    void DeleteTitle(u32 category, u32 id) { std::filesystem::remove_all(Audit::titlePath(category, id)); }
    bool ImportTitle(const char*, const DSi_TMD::TitleMetadata& tmd, bool) {
        auto path = Audit::titlePath(tmd.GetCategory(), tmd.GetID());
        std::filesystem::create_directories(path);
        auto file = std::fopen((path / "title.tmd").c_str(), "wb");
        std::fwrite(&tmd, sizeof(tmd), 1, file); std::fclose(file);
        if (Audit::failExecutableImport) return false;
        file = std::fopen((path / "complete.app").c_str(), "wb"); std::fclose(file);
        return true;
    }
    bool ImportTitleData(u32,u32,jint,const char*) { return true; }
    bool ExportTitleData(u32,u32,jint,const char*) { return true; }
};
}
}
namespace Platform = melonDS::Platform;
namespace MelonDSAndroid {
// Production Configuration.h uses char*, not std::string: moving configuration preserves these.
struct EmulatorConfiguration { const char* dsiBios7Path; const char* dsiNandPath; };
inline void setConfiguration(EmulatorConfiguration) {}
inline void BuildRomIcon(const std::array<u8,512>&, const std::array<u32,16>&, u32* data) {
    for (int i=0; i<1024; ++i) data[i] = 0x12345678;
}
}
namespace MelonDSAndroidConfiguration {
inline MelonDSAndroid::EmulatorConfiguration buildEmulatorConfiguration(JNIEnv*, jobject) {
    return {Audit::biosPath.c_str(), Audit::nandPath.c_str()};
}
}
