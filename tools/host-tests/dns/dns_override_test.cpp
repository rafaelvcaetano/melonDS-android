// Host-side test for the Net_Slirp DNS host-override table (Net_Slirp.h/.cpp).
//
// Private WFC-style servers give the ROM synthetic DNS names such as
// "<service>.<server>.sgp"; the frontend feeds Net_Slirp a name -> IPv4 table so a
// server IP change doesn't require shipping a new ROM. This builds a real Ethernet +
// IPv4 + UDP DNS query aimed at Net_Slirp's virtual nameserver, feeds it to the real
// Net_Slirp::SendPacket, and checks the synthesized DNS response.
//
// Only Platform::Log needs stubbing here: Net_Slirp answers overridden names
// synchronously inside SendPacket (it calls the send-packet callback directly, no
// polling loop), and pulls in nothing else from Platform:: on that path.

#include "Net_Slirp.h"
#include "Platform.h"

#include <arpa/inet.h>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <netinet/in.h>
#include <string>
#include <vector>

using namespace melonDS;

namespace melonDS::Platform
{
void Log(LogLevel level, const char* fmt, ...)
{
    (void)level;
    va_list args;
    va_start(args, fmt);
    vprintf(fmt, args);
    va_end(args);
}
}

namespace
{

int failures = 0;

void Expect(bool condition, const std::string& description)
{
    if (!condition)
    {
        std::fprintf(stderr, "FAIL: %s\n", description.c_str());
        failures++;
    }
    else
    {
        std::printf("PASS: %s\n", description.c_str());
    }
}

// Mirrors the private constants in Net_Slirp.cpp (kSubnet / kDNSIP / kClientIP):
// the virtual DNS server Net_Slirp answers for, and the address it hands the guest.
constexpr u32 kSubnetAddr = 0x0A400000;
constexpr u32 kDNSAddr    = kSubnetAddr | 0x02;
constexpr u32 kClientAddr = kSubnetAddr | 0x10;

void StoreBE16(u8* destination, u16 value)
{
    value = htons(value);
    std::memcpy(destination, &value, sizeof(value));
}

void StoreBE32(u8* destination, u32 value)
{
    value = htonl(value);
    std::memcpy(destination, &value, sizeof(value));
}

u16 ReadBE16(const u8* source)
{
    u16 value;
    std::memcpy(&value, source, sizeof(value));
    return ntohs(value);
}

u32 ReadBE32(const u8* source)
{
    u32 value;
    std::memcpy(&value, source, sizeof(value));
    return ntohl(value);
}

u32 ParseIPv4(const std::string& dotted)
{
    struct in_addr addr {};
    if (inet_pton(AF_INET, dotted.c_str(), &addr) != 1)
    {
        std::fprintf(stderr, "bad IPv4 literal: %s\n", dotted.c_str());
        std::abort();
    }
    return ntohl(addr.s_addr);
}

void AppendQName(std::vector<u8>& out, const std::string& nameIn)
{
    // A trailing dot denotes a fully-qualified name and carries no extra label.
    std::string name = nameIn;
    if (!name.empty() && name.back() == '.')
        name.pop_back();

    size_t start = 0;
    while (start < name.size())
    {
        size_t dot = name.find('.', start);
        if (dot == std::string::npos)
            dot = name.size();

        size_t labelLen = dot - start;
        out.push_back((u8)labelLen);
        for (size_t i = start; i < dot; i++)
            out.push_back((u8)name[i]);

        start = dot + 1;
    }
    out.push_back(0);
}

// Builds a minimal Ethernet + IPv4 + UDP frame carrying a single-question DNS query
// for `name`/`qtype`, addressed to Net_Slirp's virtual nameserver (kDNSAddr:53), the
// same shape Net_Slirp::SendPacket / HandleDNSFrame expect.
std::vector<u8> BuildDNSQuery(const std::string& name, u16 qtype, u16 id)
{
    std::vector<u8> qname;
    AppendQName(qname, name);

    std::vector<u8> dns;
    auto put16 = [&](u16 v) { dns.push_back((u8)(v >> 8)); dns.push_back((u8)v); };
    put16(id);
    put16(0x0100); // flags: standard query, recursion desired, QR=0
    put16(1);      // QDCOUNT
    put16(0);      // ANCOUNT
    put16(0);      // NSCOUNT
    put16(0);      // ARCOUNT
    dns.insert(dns.end(), qname.begin(), qname.end());
    put16(qtype);
    put16(1); // QCLASS = IN

    std::vector<u8> pkt(0x2A + dns.size(), 0);

    // Ethernet
    pkt[0] = 0x00; pkt[1] = 0xAB; pkt[2] = 0x33; pkt[3] = 0x28; pkt[4] = 0x99; pkt[5] = 0x01; // dest (server)
    pkt[6] = 0x02; pkt[7] = 0x00; pkt[8] = 0x00; pkt[9] = 0x00; pkt[10] = 0x00; pkt[11] = 0x01; // src (guest)
    StoreBE16(&pkt[0xC], 0x0800);

    // IPv4
    u8* ip = &pkt[0xE];
    ip[0] = 0x45;
    ip[1] = 0x00;
    StoreBE16(&ip[2], static_cast<u16>(20 + 8 + dns.size())); // total length
    StoreBE16(&ip[4], 1); // identification
    StoreBE16(&ip[6], 0); // flags/fragment
    ip[8] = 64;               // TTL
    ip[9] = 0x11;             // UDP
    StoreBE16(&ip[10], 0); // checksum, unchecked by Net_Slirp
    StoreBE32(&ip[12], kClientAddr); // source IP
    StoreBE32(&ip[16], kDNSAddr); // destination IP

    // UDP
    u8* udp = &pkt[0x22];
    StoreBE16(&udp[0], 33445); // source port
    StoreBE16(&udp[2], 53); // destination port
    StoreBE16(&udp[4], static_cast<u16>(8 + dns.size()));
    StoreBE16(&udp[6], 0); // checksum, unchecked by Net_Slirp

    std::memcpy(&pkt[0x2A], dns.data(), dns.size());
    return pkt;
}

// Finds the address (host byte order) of the first answer record in a DNS response
// built by Net_Slirp::HandleDNSFrame. `questionLen` is the raw length of the question
// section, which HandleDNSFrame echoes back unchanged right before the answers.
// Returns false if the response has no answers (ANCOUNT == 0).
bool FirstAnswerAddress(const std::vector<u8>& resp, size_t questionLen, u32& addr)
{
    if (resp.size() < 0x2A + 12)
        return false;

    const u8* dns = &resp[0x2A];
    u16 ancount = ReadBE16(&dns[6]);
    if (ancount == 0)
        return false;

    size_t answerOffset = 0x2A + 12 + questionLen;
    if (resp.size() < answerOffset + 16)
        return false;

    const u8* rdata = &resp[answerOffset + 12];
    addr = ReadBE32(rdata);
    return true;
}

} // namespace

int main()
{
    // {name -> IPv4} override table the frontend would push down for a private WFC
    // server, deliberately mixing case to double as fixture data for the
    // case-insensitive lookup test below.
    const std::string kasumiHost = "nas.kaeru.sgp";
    const std::string wiiLinkHost = "gts.wiilink.sgp";
    const u32 kasumiAddr = ParseIPv4("178.62.43.212");
    const u32 wiiLinkAddr = ParseIPv4("167.235.229.36");

    Net_Slirp::SetHostOverrides({
        {kasumiHost, kasumiAddr},
        {wiiLinkHost, wiiLinkAddr},
    });

    std::vector<u8> lastResponse;
    Platform::SendPacketCallback callback = [&](const u8* data, int len)
    {
        lastResponse.assign(data, data + len);
    };

    Net_Slirp slirp(callback);

    // 1) exact-case override hit
    {
        lastResponse.clear();
        auto query = BuildDNSQuery(kasumiHost, 1 /* A */, 0x1234);
        size_t qlen = query.size() - 0x2A - 12; // question section length, as echoed after the 12-byte DNS header
        slirp.SendPacket(query.data(), (int)query.size());

        Expect(!lastResponse.empty(), "override hit (exact case): response received");
        u32 addr = 0;
        bool hasAnswer = !lastResponse.empty() && FirstAnswerAddress(lastResponse, qlen, addr);
        Expect(hasAnswer && addr == kasumiAddr,
            "override hit (exact case): answer is the overridden IPv4 address");
    }

    // 2) case-insensitive override hit, with a trailing dot in the query name
    {
        lastResponse.clear();
        auto query = BuildDNSQuery("NAS.Kaeru.SGP.", 1 /* A */, 0x5678);
        size_t qlen = query.size() - 0x2A - 12;
        slirp.SendPacket(query.data(), (int)query.size());

        Expect(!lastResponse.empty(), "override hit (mixed case + trailing dot): response received");
        u32 addr = 0;
        bool hasAnswer = !lastResponse.empty() && FirstAnswerAddress(lastResponse, qlen, addr);
        Expect(hasAnswer && addr == kasumiAddr,
            "override hit (mixed case + trailing dot): answer is the overridden IPv4 address");
    }

    // Sanity check that the table holds more than one entry correctly.
    {
        lastResponse.clear();
        auto query = BuildDNSQuery(wiiLinkHost, 1 /* A */, 0x9ABC);
        size_t qlen = query.size() - 0x2A - 12;
        slirp.SendPacket(query.data(), (int)query.size());

        u32 addr = 0;
        bool hasAnswer = !lastResponse.empty() && FirstAnswerAddress(lastResponse, qlen, addr);
        Expect(hasAnswer && addr == wiiLinkAddr,
            "second table entry resolves to its own overridden IPv4 address");
    }

    // 3) name not in the table: must never return an override address. It may fail
    // to resolve at all (no network in the test sandbox) -- that's fine, we only
    // reject getting an override's IP back for an unrelated name.
    {
        lastResponse.clear();
        auto query = BuildDNSQuery("not-overridden.example.invalid", 1 /* A */, 0xDEAD);
        size_t qlen = query.size() - 0x2A - 12;
        slirp.SendPacket(query.data(), (int)query.size());

        u32 addr = 0;
        bool hasAnswer = !lastResponse.empty() && FirstAnswerAddress(lastResponse, qlen, addr);
        bool gotWrongOverride = hasAnswer && (addr == kasumiAddr || addr == wiiLinkAddr);
        Expect(!gotWrongOverride,
            "name absent from the table never resolves to an override address");
    }

    // 4) The declared frame length is the trust boundary. Bytes that happen to exist
    // after it in a reused LAN buffer must never be parsed or reflected.
    {
        lastResponse.clear();
        auto query = BuildDNSQuery(kasumiHost, 1 /* A */, 0xBEEF);
        query.resize(2048, 0xA5); // model the reusable Android LAN buffer's stale suffix
        slirp.SendPacket(query.data(), 14); // Ethernet header only
        Expect(lastResponse.empty(), "truncated frame cannot consume stale LAN-buffer bytes");
    }

    // 5) A label must fit entirely inside the UDP/DNS payload, including QTYPE/QCLASS.
    {
        lastResponse.clear();
        auto query = BuildDNSQuery(kasumiHost, 1 /* A */, 0xCAFE);
        query[0x2A + 12] = 63; // declared label extends beyond the supplied frame
        const int truncatedLength = 0x2A + 12 + 5;
        query.resize(2048, 0x5A);
        slirp.SendPacket(query.data(), truncatedLength);
        Expect(lastResponse.empty(), "truncated DNS label is rejected before reading its suffix");
    }

    // 6) The first IPv4 fragment contains a UDP header whose declared datagram is
    // intentionally larger than this fragment. It must bypass DNS parsing and
    // remain a valid packet for the IP reassembly layer.
    {
        lastResponse.clear();
        auto fragment = BuildDNSQuery(kasumiHost, 1 /* A */, 0xFACE);
        const u16 fullUdpLength = ReadBE16(&fragment[0x22 + 4]);
        fragment.resize(14 + 20 + 8 + 8);
        u8* ip = &fragment[14];
        StoreBE16(&ip[2], static_cast<u16>(fragment.size() - 14));
        StoreBE16(&ip[6], 0x2000); // more fragments, offset zero
        StoreBE16(&fragment[0x22 + 4], fullUdpLength);
        Expect(slirp.SendPacket(fragment.data(), static_cast<int>(fragment.size())) ==
                static_cast<int>(fragment.size()),
            "fragmented UDP is passed to the IP layer instead of rejected as a short datagram");
        Expect(lastResponse.empty(), "fragmented DNS is not parsed before IP reassembly");
    }

    // 7) Four maximum labels fill the 255-byte presentation buffer exactly. A
    // fifth label must be rejected without unsigned-capacity underflow.
    {
        lastResponse.clear();
        const std::string label(63, 'a');
        auto query = BuildDNSQuery(
            label + "." + label + "." + label + "." + label + ".z", 1, 0xD00D);
        slirp.SendPacket(query.data(), static_cast<int>(query.size()));
        Expect(lastResponse.empty(),
            "DNS name beyond 255 presentation bytes is rejected without overflow");
    }

    if (failures == 0)
        std::printf("\nAll dns_override_test checks passed.\n");
    else
        std::fprintf(stderr, "\n%d dns_override_test check(s) failed.\n", failures);

    return failures == 0 ? 0 : 1;
}
