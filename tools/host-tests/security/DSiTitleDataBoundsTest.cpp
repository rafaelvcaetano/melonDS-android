#include <cassert>

#include "DSiTitleDataBounds.h"

int main()
{
    melonDS::u64 length = 0;
    assert(melonDS::DSiTitleDataBounds::ExpectedLength(0, 0x20000, 0x40000, 0, length));
    assert(length == 0x20000);
    assert(melonDS::DSiTitleDataBounds::ExpectedLength(1, 0x20000, 0x40000, 0, length));
    assert(length == 0x40000);
    assert(melonDS::DSiTitleDataBounds::ExpectedLength(2, 0, 0, 0x04, length));
    assert(length == 0x4000);
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(0, 0, 1, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(0, 1, 512, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(0, 511, 512, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(0, 513, 512, 0, length));
    assert(melonDS::DSiTitleDataBounds::ExpectedLength(0, 0xFFFF * 512, 512, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(0, 0x10000 * 512, 512, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(2, 1, 1, 0, length));
    assert(!melonDS::DSiTitleDataBounds::ExpectedLength(3, 1, 1, 0x04, length));
}
