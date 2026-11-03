/*
 * Exercises the JIT's block invalidation with a synthetic cartridge: a handful of ARM
 * instructions assembled by this file, wrapped in an NDS header, direct-booted into main RAM.
 * No commercial ROM and no BIOS dump are involved, so this test always runs.
 *
 * The regression properties include:
 *
 *  1. a literal held in main RAM is folded into the compiled block, and rewriting it drops
 *     the block so the next execution sees the new value;
 *  2. rewriting one 16-byte chunk drops only the blocks that actually cover that chunk, not
 *     every block sharing the surrounding 512-byte range;
 *  3. a literal the JIT cannot localise to a code region (here: one living in the DTCM) is
 *     left alone -- neither registered for invalidation nor folded into the code -- so the
 *     value read stays correct even though nothing is watching that address.
 *  4. fast memory keeps DTCM separate from neighboring main RAM, uses the correct physical
 *     slice/mirror, and restores ordinary RAM access when DTCM is moved.
 *
 * The number of live blocks is read straight out of ARMJIT::JitBlocks9. That is a public
 * member today but it is marked "TODO: Encapsulate" upstream; if it ever becomes private,
 * property 2 needs another way to count blocks (the correctness of the emulation alone does
 * not reveal over-invalidation -- discarding a still-valid block is always safe, just slow).
 */

#include <cstdio>
#include <cstring>
#include <memory>
#include <optional>
#include <vector>

#include <ARMJIT.h>
#include <Args.h>
#include <MemConstants.h>
#include <NDS.h>
#include <NDSCart.h>
#include <NDS_Header.h>

#include "JitTestSupport.h"

using namespace melonDS;

// Where direct boot drops our ARM9 binary. Cases 1 and 2 stay in the first 1 KiB so their
// code and literals share one 512-byte AddressRange. The Thumb/DTCM case reaches the end of
// the 4-KiB image to place its PC-relative literal at the adjacent DTCM window.
static constexpr u32 CodeBase = 0x02000000;
static constexpr u32 Arm7Base = 0x037F8000;
static constexpr u32 Arm9BinarySize = 0x1000;

// The ARM9 binary is padded out to Arm9BinarySize with NOPs; direct boot copies it verbatim.
static constexpr u32 Nop = 0xE1A00000;

/// LDR Rd, [PC, #imm]. R15 reads as the instruction's address plus 8.
static u32 LdrPcRelative(u32 rd, u32 at, u32 target) { return 0xE59F0000 | (rd << 12) | (target - (at + 8)); }
/// STR Rd, [PC, #imm].
static u32 StrPcRelative(u32 rd, u32 at, u32 target) { return 0xE58F0000 | (rd << 12) | (target - (at + 8)); }
/// B <target>.
static u32 BranchTo(u32 at, u32 target) { return 0xEA000000 | (((static_cast<s32>(target) - static_cast<s32>(at + 8)) >> 2) & 0xFFFFFF); }
/// BX Rm. An indirect branch, which ends a JIT block instead of being followed into it.
static u32 BranchExchange(u32 rm) { return 0xE12FFF10 | rm; }
/// Thumb LDR Rd, [PC, #imm]. PC reads as (instruction address + 4) & ~3.
static u16 ThumbLdrPcRelative(u32 rd, u32 at, u32 target)
{
    return 0x4800 | (rd << 8) | ((target - ((at + 4) & ~3)) >> 2);
}
/// Thumb STR Rd, [Rb, #imm].
static u16 ThumbStrImm(u32 rd, u32 rb, u32 offset)
{
    return 0x6000 | ((offset >> 2) << 6) | (rb << 3) | rd;
}
/// Thumb unconditional B <target>.
static u16 ThumbBranch(u32 at, u32 target)
{
    const s32 offset = (static_cast<s32>(target) - static_cast<s32>(at + 4)) >> 1;
    return 0xE000 | (offset & 0x7FF);
}

class SyntheticRom
{
public:
    SyntheticRom() : Words(Arm9BinarySize / 4, Nop) {}

    void Put(u32 address, u32 word) { Words[(address - CodeBase) / 4] = word; }
    void Put16(u32 address, u16 halfword)
    {
        memcpy(reinterpret_cast<u8*>(Words.data()) + address - CodeBase, &halfword, sizeof(halfword));
    }

    std::vector<u8> Build(u32 entryPoint) const
    {
        const u32 arm9Size = static_cast<u32>(Words.size()) * 4;
        const u32 arm7Word = 0xEAFFFFFE; // B . -- the ARM7 has nothing to do here.

        std::vector<u8> rom(0x8000 + arm9Size + 4, 0);

        NDSHeader header {};
        memcpy(header.GameTitle, "MELONJIT", 8);
        memcpy(header.GameCode, "MJIT", 4);
        // 0x8000 keeps the ROM out of both the encrypted-secure-area path (which applies
        // between 0x4000 and 0x8000) and the homebrew path (below 0x4000, which would pull in
        // DLDI patching and an SD card).
        header.ARM9ROMOffset = 0x8000;
        header.ARM9EntryAddress = entryPoint;
        header.ARM9RAMAddress = CodeBase;
        header.ARM9Size = arm9Size;
        header.ARM7ROMOffset = 0x8000 + arm9Size;
        header.ARM7EntryAddress = Arm7Base;
        header.ARM7RAMAddress = Arm7Base;
        header.ARM7Size = 4;

        memcpy(rom.data(), &header, sizeof(header));
        memcpy(rom.data() + 0x8000, Words.data(), arm9Size);
        memcpy(rom.data() + 0x8000 + arm9Size, &arm7Word, 4);
        return rom;
    }

private:
    std::vector<u32> Words;
};

static std::unique_ptr<NDS> Boot(const std::vector<u8>& rom, u32 entryPoint,
                                std::optional<JITArgs> jitArgs = JITArgs {})
{
    NDSArgs args {};
    args.JIT = jitArgs;

    auto nds = std::make_unique<NDS>(std::move(args), nullptr);
#if defined(__ANDROID__) || defined(__linux__)
    if (jitArgs)
        JitTest::Expect(nds->JIT.FastMemoryEnabled() == jitArgs->FastMemory,
                        "Android/Linux exercises the requested fast-memory mode");
#endif
    nds->SetNDSCart(NDSCart::ParseROM(rom.data(), static_cast<u32>(rom.size()), nullptr));
    nds->Reset();
    nds->SetupDirectBoot("melonds-jit-test.nds");
    nds->Start();
    (void)entryPoint;
    return nds;
}

static void RunFrames(NDS& nds, int count)
{
    for (int i = 0; i < count; i++)
        nds.RunFrame();
}

// -- 1: a literal in main RAM is invalidated when it is rewritten ---------------------------

static void test_rewriting_a_literal_recompiles_the_block()
{
    printf("== a literal in main RAM is dropped when it is rewritten ==\n");

    constexpr u32 Literal = CodeBase + 0x100;
    constexpr u32 Result = CodeBase + 0x300;

    SyntheticRom rom;
    rom.Put(CodeBase + 0, LdrPcRelative(0, CodeBase + 0, Literal));
    rom.Put(CodeBase + 4, StrPcRelative(0, CodeBase + 4, Result));
    rom.Put(CodeBase + 8, BranchTo(CodeBase + 8, CodeBase));
    rom.Put(Literal, 0x11111111);
    rom.Put(Result, 0);

    auto nds = Boot(rom.Build(CodeBase), CodeBase);
    RunFrames(*nds, 4);

    const u32 before = nds->ARM9Read32(Result);
    printf("  after boot:  result=%08x, %zu block(s) compiled\n", before, nds->JIT.JitBlocks9.size());
    JitTest::Expect(before == 0x11111111, "the loop published the original literal");
    JitTest::Expect(nds->JIT.JitBlocks9.size() == 1, "exactly one ARM9 block was compiled");

    nds->ARM9Write32(Literal, 0x22222222);
    printf("  after write: %zu block(s) left\n", nds->JIT.JitBlocks9.size());
    JitTest::Expect(nds->JIT.JitBlocks9.empty(), "rewriting the literal dropped the block");

    RunFrames(*nds, 4);
    const u32 after = nds->ARM9Read32(Result);
    printf("  after rerun: result=%08x, %zu block(s) compiled\n", after, nds->JIT.JitBlocks9.size());
    JitTest::Expect(after == 0x22222222, "the recompiled block published the new literal");
}

// -- 2: invalidation is precise to 16 bytes, not to the whole 512-byte range -----------------

static void test_only_the_written_chunk_is_invalidated()
{
    printf("== rewriting one 16-byte chunk spares the other blocks in the same range ==\n");

    // Both blocks sit in the 512-byte range at 0x02000000: block A in chunk 0, block B in
    // chunk 4. They jump to each other indirectly (BX), because a plain B would let the JIT
    // follow the branch and merge the two into a single block.
    constexpr u32 BlockA = CodeBase + 0x000;
    constexpr u32 BlockB = CodeBase + 0x040;
    constexpr u32 LiteralA = CodeBase + 0x100;
    constexpr u32 LiteralB = CodeBase + 0x104;
    constexpr u32 PointerToA = CodeBase + 0x108;
    constexpr u32 PointerToB = CodeBase + 0x10C;
    constexpr u32 ResultA = CodeBase + 0x300;
    constexpr u32 ResultB = CodeBase + 0x304;

    SyntheticRom rom;
    rom.Put(BlockA + 0, LdrPcRelative(0, BlockA + 0, LiteralA));
    rom.Put(BlockA + 4, StrPcRelative(0, BlockA + 4, ResultA));
    rom.Put(BlockA + 8, LdrPcRelative(2, BlockA + 8, PointerToB));
    rom.Put(BlockA + 12, BranchExchange(2));

    rom.Put(BlockB + 0, LdrPcRelative(1, BlockB + 0, LiteralB));
    rom.Put(BlockB + 4, StrPcRelative(1, BlockB + 4, ResultB));
    rom.Put(BlockB + 8, LdrPcRelative(3, BlockB + 8, PointerToA));
    rom.Put(BlockB + 12, BranchExchange(3));

    rom.Put(LiteralA, 0x11111111);
    rom.Put(LiteralB, 0x22222222);
    rom.Put(PointerToA, BlockA);
    rom.Put(PointerToB, BlockB);

    auto nds = Boot(rom.Build(BlockA), BlockA);
    RunFrames(*nds, 4);

    const size_t compiled = nds->JIT.JitBlocks9.size();
    printf("  after boot:  resultA=%08x resultB=%08x, %zu block(s) compiled\n",
           nds->ARM9Read32(ResultA), nds->ARM9Read32(ResultB), compiled);
    JitTest::Expect(compiled == 2, "both blocks were compiled separately");

    // Rewrite the first instruction of block A so that it now loads block B's literal. Only
    // chunk 0 is touched, so only block A should go.
    nds->ARM9Write32(BlockA + 0, LdrPcRelative(0, BlockA + 0, LiteralB));
    const size_t remaining = nds->JIT.JitBlocks9.size();
    printf("  after write: %zu block(s) left (precise invalidation leaves 1; "
           "over-invalidation leaves 0)\n", remaining);
    JitTest::Expect(remaining == 1, "only the block covering the written chunk was dropped");

    RunFrames(*nds, 4);
    printf("  after rerun: resultA=%08x resultB=%08x, %zu block(s) compiled\n",
           nds->ARM9Read32(ResultA), nds->ARM9Read32(ResultB), nds->JIT.JitBlocks9.size());
    JitTest::Expect(nds->ARM9Read32(ResultA) == 0x22222222, "the rewritten block A ran its new code");
}

// -- 3: a literal outside any code region is neither registered nor folded -------------------

static void test_a_literal_in_dtcm_is_not_folded(bool fastMemory, bool literalOptimizations)
{
    printf("== DTCM literal: fast memory %s, literal optimization %s ==\n",
           fastMemory ? "on" : "off", literalOptimizations ? "on" : "off");

    // ARMJIT::LocaliseCodeAddress returns 0 for the DTCM, because CodeMemRegions has no entry
    // for it. Two things must then happen, and this case covers both:
    //   * CompileBlock must not register the literal. Without the `translatedAddr &&` guard it
    //     records an address range of 0 and later indexes CodeMemRegions[0], which is NULL --
    //     a straight null dereference, so dropping the guard turns this test into a crash.
    //   * the backend must not fold the value, which is what FetchedInstr::LiteralRegistered
    //     tells it. Folding an address nothing is watching yields a stale read forever, so
    //     dropping that check makes this test report the old value below.
    // The DTCM is moved next to the code because a PC-relative load only reaches +/-4095
    // bytes; its default home at 0x03000000 is far out of range.
    constexpr u32 DtcmBase = 0x02001000;
    constexpr u32 DtcmSizeSetting = 3 << 1;          // 0x200 << 3 == 4 KiB, the smallest allowed
    constexpr u32 Literal = DtcmBase;                 // 0xFF8 bytes past the load's R15: in range
    constexpr u32 Result = CodeBase + 0x300;

    SyntheticRom rom;
    rom.Put(CodeBase + 0, LdrPcRelative(0, CodeBase + 0, Literal));
    rom.Put(CodeBase + 4, StrPcRelative(0, CodeBase + 4, Result));
    rom.Put(CodeBase + 8, BranchTo(CodeBase + 8, CodeBase));
    rom.Put(Result, 0);

    JITArgs jitArgs {};
    jitArgs.FastMemory = fastMemory;
    jitArgs.LiteralOptimizations = literalOptimizations;
    auto nds = Boot(rom.Build(CodeBase), CodeBase, jitArgs);

    nds->ARM9.CP15Write(0x910, DtcmBase | DtcmSizeSetting);
    const bool mapped = nds->ARM9.DTCMBase == DtcmBase;
    printf("  DTCM mapped at %08x (mask %08x)\n", nds->ARM9.DTCMBase, nds->ARM9.DTCMMask);
    JitTest::Expect(mapped, "the DTCM was remapped next to the code");

    auto writeDtcm = [&nds](u32 address, u32 value) {
        memcpy(&nds->ARM9.DTCM[address & (DTCMPhysicalSize - 1)], &value, sizeof(value));
    };

    // A main-RAM store must never alias this physical DTCM location. In the broken
    // fast-memory mapping, Result was overlaid by a 16-KiB DTCM mirror.
    constexpr u32 Sentinel = 0xC0DEC0DE;
    writeDtcm(Result, Sentinel);
    writeDtcm(Literal, 0xAAAA1111);
    RunFrames(*nds, 4);
    const u32 before = nds->ARM9Read32(Result);
    printf("  after boot:  result=%08x, %zu block(s) compiled\n", before, nds->JIT.JitBlocks9.size());
    JitTest::Expect(before == 0xAAAA1111, "the loop published the DTCM value");

    // Nothing invalidates the block: the DTCM is not a watched code region. The new value can
    // only surface if the load was compiled as a real memory access.
    writeDtcm(Literal, 0xBBBB2222);
    RunFrames(*nds, 4);
    const u32 after = nds->ARM9Read32(Result);
    u32 dtcmSentinel;
    memcpy(&dtcmSentinel, &nds->ARM9.DTCM[Result & (DTCMPhysicalSize - 1)], sizeof(dtcmSentinel));
    printf("  after DTCM rewrite: result=%08x, %zu block(s) compiled\n",
           after, nds->JIT.JitBlocks9.size());
    printf("  loaded R0=%08x, physical DTCM sentinel=%08x\n", nds->ARM9.R[0], dtcmSentinel);
    JitTest::Expect(after == 0xBBBB2222, "the untracked literal was re-read instead of folded");
    JitTest::Expect(dtcmSentinel == Sentinel, "main-RAM stores do not overwrite physical DTCM");
}

// -- 4: the Thumb literal path must also reject an untracked DTCM address --------------------

static void test_thumb_literal_in_dtcm_is_not_folded(bool jit, bool literalOptimizations)
{
    printf("== Thumb DTCM literal: %s, literal optimization %s, fast memory off ==\n",
           jit ? "JIT" : "interpreter", literalOptimizations ? "on" : "off");

    // The ARM entry stub performs a normal BX into Thumb code. At ThumbEntry, architectural
    // PC is (ThumbEntry + 4) & ~3 == 0x02000F04, so imm8 0x3F reaches DTCM at 0x02001000.
    constexpr u32 ThumbEntry = 0x02000F02;
    constexpr u32 DtcmBase = 0x02001000;
    constexpr u32 DtcmSizeSetting = 3 << 1;
    constexpr u32 Literal = DtcmBase;
    constexpr u32 Result = CodeBase + 0x300;
    constexpr u32 ResultPointer = ThumbEntry + 0x0A;

    SyntheticRom rom;
    rom.Put(CodeBase + 0, LdrPcRelative(0, CodeBase + 0, CodeBase + 8));
    rom.Put(CodeBase + 4, BranchExchange(0));
    rom.Put(CodeBase + 8, ThumbEntry | 1);
    rom.Put16(ThumbEntry + 0, ThumbLdrPcRelative(0, ThumbEntry + 0, Literal));
    rom.Put16(ThumbEntry + 2, ThumbLdrPcRelative(1, ThumbEntry + 2, ResultPointer));
    rom.Put16(ThumbEntry + 4, ThumbStrImm(0, 1, 0));
    rom.Put16(ThumbEntry + 6, ThumbBranch(ThumbEntry + 6, ThumbEntry));
    rom.Put(ResultPointer, Result);

    std::optional<JITArgs> jitArgs = std::nullopt;
    if (jit)
    {
        JITArgs configured {};
        configured.FastMemory = false;
        configured.LiteralOptimizations = literalOptimizations;
        jitArgs = configured;
    }
    auto nds = Boot(rom.Build(CodeBase), CodeBase, jitArgs);
    nds->ARM9.CP15Write(0x910, DtcmBase | DtcmSizeSetting);
    JitTest::Expect(nds->ARM9.DTCMBase == DtcmBase, "the Thumb literal DTCM window is active");

    auto writeDtcm = [&nds](u32 value) {
        memcpy(&nds->ARM9.DTCM[Literal & (DTCMPhysicalSize - 1)], &value, sizeof(value));
    };
    auto runAndRead = [&nds](u32 expected) {
        nds->ARM9.R[0] = 0;
        nds->ARM9Write32(Result, 0);
        RunFrames(*nds, 4);
        const u32 observed = nds->ARM9Read32(Result);
        printf("  expected=%08x result=%08x R0=%08x\n", expected, observed, nds->ARM9.R[0]);
        JitTest::Expect(observed == expected, "the Thumb load reads the current DTCM literal");
    };

    writeDtcm(0xAAAA1111);
    runAndRead(0xAAAA1111);
    writeDtcm(0xBBBB2222);
    runAndRead(0xBBBB2222);
    writeDtcm(0xCCCC3333);
    runAndRead(0xCCCC3333);
}

static void test_dtcm_mapping_and_remap(u32 base, u32 sizeSetting)
{
    printf("== DTCM mapping and remap: base %08x, setting %u ==\n", base, sizeSetting);
    constexpr u32 Result = CodeBase + 0x300;
    SyntheticRom rom;
    rom.Put(CodeBase, 0xE5910000); // LDR r0, [r1], no PC-relative literal folding
    rom.Put(CodeBase + 4, StrPcRelative(0, CodeBase + 4, Result));
    rom.Put(CodeBase + 8, BranchTo(CodeBase + 8, CodeBase));

    auto nds = Boot(rom.Build(CodeBase), CodeBase);
    nds->ARM9.CP15Write(0x910, base | (sizeSetting << 1));
    nds->ARM9.R[1] = base;
    const u32 first = 0x12345678;
    memcpy(&nds->ARM9.DTCM[base & (DTCMPhysicalSize - 1)], &first, sizeof(first));
    RunFrames(*nds, 2);
    JitTest::Expect(nds->ARM9Read32(Result) == first, "DTCM load and neighboring main-RAM store remain distinct");

    if (sizeSetting == 6)
    {
        nds->ARM9.R[1] = base + DTCMPhysicalSize;
        RunFrames(*nds, 2);
        JitTest::Expect(nds->ARM9Read32(Result) == first, "the second 16-KiB mirror reads the same physical DTCM");
        const u32 updated = 0x13579BDF;
        memcpy(nds->ARM9.DTCM, &updated, sizeof(updated));
        RunFrames(*nds, 2);
        JitTest::Expect(nds->ARM9Read32(Result) == updated, "physical DTCM updates are visible through the second mirror");
        nds->ARM9.R[1] = base;
    }

    // Move DTCM outside the old main-RAM mirror. Its old hole must become ordinary RAM,
    // even when both the DTCM and main-RAM fast mappings already exist.
    nds->ARM9.CP15Write(0x910, (base + 0x01000000) | (sizeSetting << 1));
    nds->ARM9Write32(base, 0x87654321);
    RunFrames(*nds, 2);
    JitTest::Expect(nds->ARM9Read32(Result) == 0x87654321, "moving DTCM restores reads from the old main-RAM address");
}

int main()
{
    // stdout is block-buffered when redirected, the core logs to stderr: without this the
    // core's warnings land in the middle of an unrelated section of the report.
    setvbuf(stdout, nullptr, _IOLBF, 0);

    test_rewriting_a_literal_recompiles_the_block();
    test_only_the_written_chunk_is_invalidated();
    test_a_literal_in_dtcm_is_not_folded(false, true);
    test_a_literal_in_dtcm_is_not_folded(true, false);
    test_a_literal_in_dtcm_is_not_folded(true, true);
    test_thumb_literal_in_dtcm_is_not_folded(true, true);
    test_thumb_literal_in_dtcm_is_not_folded(true, false);
    test_thumb_literal_in_dtcm_is_not_folded(false, false);
    test_dtcm_mapping_and_remap(0x02001000, 3); // 4 KiB
    test_dtcm_mapping_and_remap(0x02002000, 4); // 8 KiB
    test_dtcm_mapping_and_remap(0x02004000, 5); // 16 KiB
    test_dtcm_mapping_and_remap(0x02008000, 6); // 32 KiB, mirrored physical DTCM

    printf("%s\n", JitTest::Failures == 0 ? "PASS" : "FAIL");
    return JitTest::Failures == 0 ? 0 : 1;
}
