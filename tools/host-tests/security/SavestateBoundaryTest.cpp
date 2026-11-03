#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <vector>

#include "Platform.h"
#include "Savestate.h"

namespace melonDS::Platform {
void Log(LogLevel, const char*, ...) {}
}

namespace {
int Failures = 0;

void Expect(bool condition, const char* message)
{
    if (condition) std::printf("PASS: %s\n", message);
    else
    {
        std::fprintf(stderr, "FAIL: %s\n", message);
        ++Failures;
    }
}

void Write16(std::vector<melonDS::u8>& data, std::size_t offset, melonDS::u16 value)
{
    std::memcpy(data.data() + offset, &value, sizeof(value));
}

void Write32(std::vector<melonDS::u8>& data, std::size_t offset, melonDS::u32 value)
{
    std::memcpy(data.data() + offset, &value, sizeof(value));
}

std::vector<melonDS::u8> State(std::size_t size)
{
    std::vector<melonDS::u8> data(size, 0);
    if (size >= 4) std::memcpy(data.data(), "MELN", 4);
    if (size >= 6) Write16(data, 4, SAVESTATE_MAJOR);
    if (size >= 8) Write16(data, 6, SAVESTATE_MINOR);
    if (size >= 12) Write32(data, 8, static_cast<melonDS::u32>(size));
    return data;
}

void ExpectRejected(std::vector<melonDS::u8> data, const char* message)
{
    melonDS::Savestate state(data.data(), static_cast<melonDS::u32>(data.size()), false);
    state.Section("RCHV");
    Expect(state.Error, message);
}
}

int main()
{
    {
        auto data = State(32);
        std::memcpy(data.data() + 16, "NOPE", 4);
        Write32(data, 20, 0);
        ExpectRejected(std::move(data), "zero-length section is rejected without nonprogress");
    }

    for (melonDS::u32 sectionLength = 1; sectionLength < 16; ++sectionLength)
    {
        auto data = State(32);
        std::memcpy(data.data() + 16, "NOPE", 4);
        Write32(data, 20, sectionLength);
        ExpectRejected(std::move(data), "section shorter than its header is rejected");
    }

    {
        auto data = State(32);
        std::memcpy(data.data() + 16, "NOPE", 4);
        Write32(data, 20, 17);
        ExpectRejected(std::move(data), "section extending past the buffer is rejected");
    }

    for (std::size_t size = 17; size < 32; ++size)
        ExpectRejected(State(size), "trailing partial section header is rejected");

    {
        auto data = State(32);
        std::memcpy(data.data() + 16, "RCHV", 4);
        Write32(data, 20, 16);
        melonDS::Savestate state(data.data(), static_cast<melonDS::u32>(data.size()), false);
        state.Section("RCHV");
        Expect(!state.Error, "an exact 16-byte section remains valid");
    }

    {
        auto data = State(52);
        std::memcpy(data.data() + 16, "RCHV", 4);
        Write32(data, 20, 20);
        std::memcpy(data.data() + 36, "NEXT", 4);
        Write32(data, 40, 16);
        melonDS::Savestate state(data.data(), static_cast<melonDS::u32>(data.size()), false);
        state.Section("RCHV");
        melonDS::u8 payload[5] = {};
        state.VarArray(payload, sizeof(payload));
        Expect(state.Error, "a section payload cannot consume bytes from the next section");
    }

    return Failures == 0 ? 0 : 1;
}
