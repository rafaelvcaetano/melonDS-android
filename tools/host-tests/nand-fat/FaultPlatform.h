#pragma once
#include <cstdint>
namespace NandFault {
inline uint64_t inputLength = 0;
inline uint64_t writeStart = UINT64_MAX;
inline unsigned writeCall = 0;
inline unsigned failWriteCall = 0;
inline unsigned readFailures = 0;
inline unsigned writeFailures = 0;
inline void reset() {
    inputLength = 0; writeStart = UINT64_MAX; writeCall = 0;
    failWriteCall = 0; readFailures = 0; writeFailures = 0;
}
}
