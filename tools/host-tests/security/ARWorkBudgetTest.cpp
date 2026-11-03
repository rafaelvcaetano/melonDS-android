#include <cstdio>
#include <limits>

#include "ARWorkBudget.h"

namespace {
int Failures = 0;
void Expect(bool condition, const char* message)
{
    if (condition) std::printf("PASS: %s\n", message);
    else { std::fprintf(stderr, "FAIL: %s\n", message); ++Failures; }
}
}

int main()
{
    melonDS::ARWorkBudget budget(4);
    Expect(budget.Consume(3), "ordinary Action Replay work within budget is accepted");
    Expect(!budget.Consume(2), "Action Replay work crossing the budget is rejected atomically");
    Expect(budget.Remaining() == 1, "a rejected Action Replay operation consumes no partial budget");
    Expect(budget.Consume(1), "the exact remaining Action Replay budget is usable");
    Expect(!budget.Consume(1), "Action Replay work stops when the budget is exhausted");
    Expect(melonDS::ARWorkBudget::OperationsForBytes(0) == 0,
        "an empty Action Replay copy costs no memory operation");
    Expect(melonDS::ARWorkBudget::OperationsForBytes(5) == 2,
        "Action Replay copy work counts rounded 32-bit memory operations");
    Expect(melonDS::ARWorkBudget::OperationsForBytes(std::numeric_limits<melonDS::u32>::max()) ==
            0x40000000ULL,
        "maximal Action Replay copy work is counted without overflow");

    melonDS::ARWorkBudget freshCheat(4), nearlySpentFrame(1);
    Expect(melonDS::ConsumeARWork(freshCheat, nearlySpentFrame, 2) ==
            melonDS::ARBudgetResult::FrameExhausted &&
            freshCheat.Remaining() == 4 && nearlySpentFrame.Remaining() == 1,
        "a shared frame limit defers an ordinary cheat without disabling or partially charging it");
    melonDS::ARWorkBudget exhaustedCheat(1), freshFrame(4);
    Expect(melonDS::ConsumeARWork(exhaustedCheat, freshFrame, 2) ==
            melonDS::ARBudgetResult::CheatExhausted &&
            exhaustedCheat.Remaining() == 1 && freshFrame.Remaining() == 4,
        "a single cheat crossing its own budget is distinguished and rejected atomically");
    return Failures == 0 ? 0 : 1;
}
