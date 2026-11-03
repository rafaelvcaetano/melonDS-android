// Only host-file failure delivery is adapted; NAND, encryption and FatFs stay real.
#define FileRead BoundaryFileRead
#define FileWrite BoundaryFileWrite
#include "../jit/HostPlatform.cpp"
#undef FileRead
#undef FileWrite
#include "FaultPlatform.h"

namespace melonDS::Platform {
u64 FileRead(void* data, u64 size, u64 count, FileHandle* file)
{
    if (NandFault::inputLength && FileLength(file) == NandFault::inputLength) {
        ++NandFault::readFailures;
        return 0;
    }
    return BoundaryFileRead(data, size, count, file);
}
u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file)
{
    const unsigned call = ++NandFault::writeCall;
    if (FilePosition(file) >= NandFault::writeStart ||
        (NandFault::failWriteCall && call == NandFault::failWriteCall)) {
        ++NandFault::writeFailures;
        return 0;
    }
    return BoundaryFileWrite(data, size, count, file);
}
}
