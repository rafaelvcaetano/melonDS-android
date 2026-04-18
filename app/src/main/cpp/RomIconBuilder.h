#ifndef ROMICONBUILDER_H
#define ROMICONBUILDER_H

#include "types.h"

namespace MelonDSAndroid
{

void BuildRomIcon(const melonDS::u8 (&data)[512], const melonDS::u16 (&palette)[16], melonDS::u32 (&iconRef)[32*32]);

}

#endif