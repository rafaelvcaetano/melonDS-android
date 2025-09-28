#include "RomIconBuilder.h"

void MelonDSAndroid::BuildRomIcon(const melonDS::u8 (&data)[512], const melonDS::u16 (&palette)[16], melonDS::u32 (&iconRef)[32*32])
{
    melonDS::u32 paletteRGBA[16];
    for (int i = 0; i < 16; i++)
    {
        melonDS::u8 r = ((palette[i] >> 0)  & 0x1F) * 255 / 31;
        melonDS::u8 g = ((palette[i] >> 5)  & 0x1F) * 255 / 31;
        melonDS::u8 b = ((palette[i] >> 10) & 0x1F) * 255 / 31;
        melonDS::u8 a = i ? 255 : 0;
        paletteRGBA[i] = r | (g << 8) | (b << 16) | (a << 24);
    }

    int count = 0;
    for (int ytile = 0; ytile < 4; ytile++)
    {
        for (int xtile = 0; xtile < 4; xtile++)
        {
            for (int ypixel = 0; ypixel < 8; ypixel++)
            {
                for (int xpixel = 0; xpixel < 8; xpixel++)
                {
                    melonDS::u8 pal_index = count % 2 ? data[count/2] >> 4 : data[count/2] & 0x0F;
                    iconRef[ytile*256 + ypixel*32 + xtile*8 + xpixel] = paletteRGBA[pal_index];
                    count++;
                }
            }
        }
    }
}