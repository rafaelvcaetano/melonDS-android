#include <array>
#include <cstdio>
#include <cstring>
#include <vector>

#include "DSiContentValidation.h"
#include "sha1/sha1.hpp"

namespace {
int Failures = 0;
void Expect(bool condition, const char* message)
{
    if (condition) std::printf("PASS: %s\n", message);
    else { std::fprintf(stderr, "FAIL: %s\n", message); ++Failures; }
}

std::array<melonDS::u8, 20> Hash(const std::vector<melonDS::u8>& bytes)
{
    std::array<melonDS::u8, 20> result{};
    SHA1_CTX context;
    SHA1Init(&context);
    SHA1Update(&context, bytes.data(), static_cast<uint32_t>(bytes.size()));
    SHA1Final(result.data(), &context);
    return result;
}

void SetBigEndian64(melonDS::u8* destination, uint64_t value)
{
    for (int index = 7; index >= 0; --index)
    {
        destination[index] = static_cast<melonDS::u8>(value);
        value >>= 8;
    }
}
}

int main()
{
    using namespace melonDS;
    std::vector<u8> content(0x8000, 0xA5);
    NDSHeader header{};
    header.DSiTitleIDHigh = 0x00030004;
    header.DSiTitleIDLow = 0x54455354;
    header.ROMSize = static_cast<u32>(content.size());
    std::memcpy(content.data(), &header, sizeof(header));

    DSi_TMD::TitleMetadata tmd{};
    const u8 titleId[] = {0x00, 0x03, 0x00, 0x04, 'T', 'E', 'S', 'T'};
    std::memcpy(tmd.TitleId, titleId, sizeof(titleId));
    SetBigEndian64(tmd.Contents.ContentSize, content.size());
    const auto digest = Hash(content);
    std::memcpy(tmd.Contents.ContentSha1Hash, digest.data(), digest.size());

    Expect(DSiContentValidation::MetadataMatches(tmd, header, content.size(), digest.data()),
        "matching DSi content size, identity, ranges, and SHA-1 are accepted");
    Expect(!DSiContentValidation::MetadataMatches(tmd, header, sizeof(NDSHeader), digest.data()),
        "header-only DSi content is rejected against the TMD size");
    auto wrongDigest = digest;
    wrongDigest[0] ^= 0x80;
    Expect(!DSiContentValidation::MetadataMatches(tmd, header, content.size(), wrongDigest.data()),
        "content changed after metadata selection is rejected by SHA-1");
    header.DSiTitleIDLow++;
    Expect(!DSiContentValidation::MetadataMatches(tmd, header, content.size(), digest.data()),
        "header/TMD title identity mismatch is rejected");
    SetBigEndian64(tmd.Contents.ContentSize, 0x100000000ULL);
    Expect(!DSiContentValidation::LengthSupported(tmd, 0x100000000ULL),
        "4 GiB content is rejected before any 32-bit narrowing");
    SetBigEndian64(tmd.Contents.ContentSize, 128ULL * 1024 * 1024);
    Expect(DSiContentValidation::LengthSupported(tmd, 128ULL * 1024 * 1024),
        "maximum bounded DSiWare snapshot is accepted");
    SetBigEndian64(tmd.Contents.ContentSize, 128ULL * 1024 * 1024 + 1);
    Expect(!DSiContentValidation::LengthSupported(tmd, 128ULL * 1024 * 1024 + 1),
        "DSiWare snapshot above 128 MiB is rejected before allocation");

    return Failures == 0 ? 0 : 1;
}
