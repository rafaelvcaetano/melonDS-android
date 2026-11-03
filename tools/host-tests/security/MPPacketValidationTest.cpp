#include <cstdio>
#include "net/MPPacketValidation.h"

namespace { int failures = 0; void expect(bool ok, const char* text) { if (ok) std::printf("PASS: %s\n", text); else { std::fprintf(stderr, "FAIL: %s\n", text); failures++; } } }

int main()
{
    melonDS::MPPacketHeader h {0x4946494E, 1, 0, 16, 0};
    expect(melonDS::MPPacketValidation::HeaderValid(h, sizeof(h) + 16), "bounded LAN payload is accepted");
    expect(!melonDS::MPPacketValidation::HeaderValid(h, sizeof(h)), "truncated LAN payload is rejected");
    h.Type = 2;
    expect(!melonDS::MPPacketValidation::HeaderValid(h, sizeof(h) + 16), "LAN reply AID zero is rejected");
    h.Type = 2 | (16U << 16);
    expect(!melonDS::MPPacketValidation::HeaderValid(h, sizeof(h) + 16), "LAN reply AID sixteen is rejected");
    h.Type = 2 | (15U << 16);
    expect(melonDS::MPPacketValidation::HeaderValid(h, sizeof(h) + 16), "highest valid LAN reply AID is accepted");
    h.SenderID = 16;
    expect(!melonDS::MPPacketValidation::HeaderValid(h, sizeof(h) + 16), "LAN sender outside table is rejected");
    return failures == 0 ? 0 : 1;
}
