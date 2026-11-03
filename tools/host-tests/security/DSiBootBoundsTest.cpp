#include <cassert>

#include "DSiBootBounds.h"

int main()
{
    assert(melonDS::DSiBootBounds::SpanValid(0x1000, 0x20, 0x02000000, 0x2000));
    assert(!melonDS::DSiBootBounds::SpanValid(0x1000, 0, 0x02000000, 0x2000));
    assert(!melonDS::DSiBootBounds::SpanValid(0x1000, 0x11, 0x02000000, 0x2000));
    assert(!melonDS::DSiBootBounds::SpanValid(0x1FF0, 0x20, 0x02000000, 0x2000));
    assert(!melonDS::DSiBootBounds::SpanValid(0, 0x01000010, 0x02000000, 0x02000000));
    assert(!melonDS::DSiBootBounds::SpanValid(0, 0x20, 0xFFFFFFF0, 0x2000));
}
