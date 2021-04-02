package me.magnum.melonds.common

class Crc32(bytes: ByteArray) {
    companion object {
        private val CRC32_TAB = uintArrayOf(
                0x00000000.toUInt(), 0x77073096.toUInt(), 0xee0e612c.toUInt(), 0x990951ba.toUInt(),
                0x076dc419.toUInt(), 0x706af48f.toUInt(), 0xe963a535.toUInt(), 0x9e6495a3.toUInt(),
                0x0edb8832.toUInt(), 0x79dcb8a4.toUInt(), 0xe0d5e91e.toUInt(), 0x97d2d988.toUInt(),
                0x09b64c2b.toUInt(), 0x7eb17cbd.toUInt(), 0xe7b82d07.toUInt(), 0x90bf1d91.toUInt(),
                0x1db71064.toUInt(), 0x6ab020f2.toUInt(), 0xf3b97148.toUInt(), 0x84be41de.toUInt(),
                0x1adad47d.toUInt(), 0x6ddde4eb.toUInt(), 0xf4d4b551.toUInt(), 0x83d385c7.toUInt(),
                0x136c9856.toUInt(), 0x646ba8c0.toUInt(), 0xfd62f97a.toUInt(), 0x8a65c9ec.toUInt(),
                0x14015c4f.toUInt(), 0x63066cd9.toUInt(), 0xfa0f3d63.toUInt(), 0x8d080df5.toUInt(),
                0x3b6e20c8.toUInt(), 0x4c69105e.toUInt(), 0xd56041e4.toUInt(), 0xa2677172.toUInt(),
                0x3c03e4d1.toUInt(), 0x4b04d447.toUInt(), 0xd20d85fd.toUInt(), 0xa50ab56b.toUInt(),
                0x35b5a8fa.toUInt(), 0x42b2986c.toUInt(), 0xdbbbc9d6.toUInt(), 0xacbcf940.toUInt(),
                0x32d86ce3.toUInt(), 0x45df5c75.toUInt(), 0xdcd60dcf.toUInt(), 0xabd13d59.toUInt(),
                0x26d930ac.toUInt(), 0x51de003a.toUInt(), 0xc8d75180.toUInt(), 0xbfd06116.toUInt(),
                0x21b4f4b5.toUInt(), 0x56b3c423.toUInt(), 0xcfba9599.toUInt(), 0xb8bda50f.toUInt(),
                0x2802b89e.toUInt(), 0x5f058808.toUInt(), 0xc60cd9b2.toUInt(), 0xb10be924.toUInt(),
                0x2f6f7c87.toUInt(), 0x58684c11.toUInt(), 0xc1611dab.toUInt(), 0xb6662d3d.toUInt(),
                0x76dc4190.toUInt(), 0x01db7106.toUInt(), 0x98d220bc.toUInt(), 0xefd5102a.toUInt(),
                0x71b18589.toUInt(), 0x06b6b51f.toUInt(), 0x9fbfe4a5.toUInt(), 0xe8b8d433.toUInt(),
                0x7807c9a2.toUInt(), 0x0f00f934.toUInt(), 0x9609a88e.toUInt(), 0xe10e9818.toUInt(),
                0x7f6a0dbb.toUInt(), 0x086d3d2d.toUInt(), 0x91646c97.toUInt(), 0xe6635c01.toUInt(),
                0x6b6b51f4.toUInt(), 0x1c6c6162.toUInt(), 0x856530d8.toUInt(), 0xf262004e.toUInt(),
                0x6c0695ed.toUInt(), 0x1b01a57b.toUInt(), 0x8208f4c1.toUInt(), 0xf50fc457.toUInt(),
                0x65b0d9c6.toUInt(), 0x12b7e950.toUInt(), 0x8bbeb8ea.toUInt(), 0xfcb9887c.toUInt(),
                0x62dd1ddf.toUInt(), 0x15da2d49.toUInt(), 0x8cd37cf3.toUInt(), 0xfbd44c65.toUInt(),
                0x4db26158.toUInt(), 0x3ab551ce.toUInt(), 0xa3bc0074.toUInt(), 0xd4bb30e2.toUInt(),
                0x4adfa541.toUInt(), 0x3dd895d7.toUInt(), 0xa4d1c46d.toUInt(), 0xd3d6f4fb.toUInt(),
                0x4369e96a.toUInt(), 0x346ed9fc.toUInt(), 0xad678846.toUInt(), 0xda60b8d0.toUInt(),
                0x44042d73.toUInt(), 0x33031de5.toUInt(), 0xaa0a4c5f.toUInt(), 0xdd0d7cc9.toUInt(),
                0x5005713c.toUInt(), 0x270241aa.toUInt(), 0xbe0b1010.toUInt(), 0xc90c2086.toUInt(),
                0x5768b525.toUInt(), 0x206f85b3.toUInt(), 0xb966d409.toUInt(), 0xce61e49f.toUInt(),
                0x5edef90e.toUInt(), 0x29d9c998.toUInt(), 0xb0d09822.toUInt(), 0xc7d7a8b4.toUInt(),
                0x59b33d17.toUInt(), 0x2eb40d81.toUInt(), 0xb7bd5c3b.toUInt(), 0xc0ba6cad.toUInt(),
                0xedb88320.toUInt(), 0x9abfb3b6.toUInt(), 0x03b6e20c.toUInt(), 0x74b1d29a.toUInt(),
                0xead54739.toUInt(), 0x9dd277af.toUInt(), 0x04db2615.toUInt(), 0x73dc1683.toUInt(),
                0xe3630b12.toUInt(), 0x94643b84.toUInt(), 0x0d6d6a3e.toUInt(), 0x7a6a5aa8.toUInt(),
                0xe40ecf0b.toUInt(), 0x9309ff9d.toUInt(), 0x0a00ae27.toUInt(), 0x7d079eb1.toUInt(),
                0xf00f9344.toUInt(), 0x8708a3d2.toUInt(), 0x1e01f268.toUInt(), 0x6906c2fe.toUInt(),
                0xf762575d.toUInt(), 0x806567cb.toUInt(), 0x196c3671.toUInt(), 0x6e6b06e7.toUInt(),
                0xfed41b76.toUInt(), 0x89d32be0.toUInt(), 0x10da7a5a.toUInt(), 0x67dd4acc.toUInt(),
                0xf9b9df6f.toUInt(), 0x8ebeeff9.toUInt(), 0x17b7be43.toUInt(), 0x60b08ed5.toUInt(),
                0xd6d6a3e8.toUInt(), 0xa1d1937e.toUInt(), 0x38d8c2c4.toUInt(), 0x4fdff252.toUInt(),
                0xd1bb67f1.toUInt(), 0xa6bc5767.toUInt(), 0x3fb506dd.toUInt(), 0x48b2364b.toUInt(),
                0xd80d2bda.toUInt(), 0xaf0a1b4c.toUInt(), 0x36034af6.toUInt(), 0x41047a60.toUInt(),
                0xdf60efc3.toUInt(), 0xa867df55.toUInt(), 0x316e8eef.toUInt(), 0x4669be79.toUInt(),
                0xcb61b38c.toUInt(), 0xbc66831a.toUInt(), 0x256fd2a0.toUInt(), 0x5268e236.toUInt(),
                0xcc0c7795.toUInt(), 0xbb0b4703.toUInt(), 0x220216b9.toUInt(), 0x5505262f.toUInt(),
                0xc5ba3bbe.toUInt(), 0xb2bd0b28.toUInt(), 0x2bb45a92.toUInt(), 0x5cb36a04.toUInt(),
                0xc2d7ffa7.toUInt(), 0xb5d0cf31.toUInt(), 0x2cd99e8b.toUInt(), 0x5bdeae1d.toUInt(),
                0x9b64c2b0.toUInt(), 0xec63f226.toUInt(), 0x756aa39c.toUInt(), 0x026d930a.toUInt(),
                0x9c0906a9.toUInt(), 0xeb0e363f.toUInt(), 0x72076785.toUInt(), 0x05005713.toUInt(),
                0x95bf4a82.toUInt(), 0xe2b87a14.toUInt(), 0x7bb12bae.toUInt(), 0x0cb61b38.toUInt(),
                0x92d28e9b.toUInt(), 0xe5d5be0d.toUInt(), 0x7cdcefb7.toUInt(), 0x0bdbdf21.toUInt(),
                0x86d3d2d4.toUInt(), 0xf1d4e242.toUInt(), 0x68ddb3f8.toUInt(), 0x1fda836e.toUInt(),
                0x81be16cd.toUInt(), 0xf6b9265b.toUInt(), 0x6fb077e1.toUInt(), 0x18b74777.toUInt(),
                0x88085ae6.toUInt(), 0xff0f6a70.toUInt(), 0x66063bca.toUInt(), 0x11010b5c.toUInt(),
                0x8f659eff.toUInt(), 0xf862ae69.toUInt(), 0x616bffd3.toUInt(), 0x166ccf45.toUInt(),
                0xa00ae278.toUInt(), 0xd70dd2ee.toUInt(), 0x4e048354.toUInt(), 0x3903b3c2.toUInt(),
                0xa7672661.toUInt(), 0xd06016f7.toUInt(), 0x4969474d.toUInt(), 0x3e6e77db.toUInt(),
                0xaed16a4a.toUInt(), 0xd9d65adc.toUInt(), 0x40df0b66.toUInt(), 0x37d83bf0.toUInt(),
                0xa9bcae53.toUInt(), 0xdebb9ec5.toUInt(), 0x47b2cf7f.toUInt(), 0x30b5ffe9.toUInt(),
                0xbdbdf21c.toUInt(), 0xcabac28a.toUInt(), 0x53b39330.toUInt(), 0x24b4a3a6.toUInt(),
                0xbad03605.toUInt(), 0xcdd70693.toUInt(), 0x54de5729.toUInt(), 0x23d967bf.toUInt(),
                0xb3667a2e.toUInt(), 0xc4614ab8.toUInt(), 0x5d681b02.toUInt(), 0x2a6f2b94.toUInt(),
                0xb40bbe37.toUInt(), 0xc30c8ea1.toUInt(), 0x5a05df1b.toUInt(), 0x2d02ef8d.toUInt(),
        )

        private fun updateCrc32(crc32: UInt, byte: Byte): UInt {
            val tabIndex = (crc32.xor(byte.toUInt())).and(0xFF.toUInt())
            return (crc32.shr(8)).xor(CRC32_TAB[tabIndex.toInt()])
        }
    }

    val value: UInt

    init {
        var crc32: UInt = 0.toUInt().inv()
        var length = 0
        var remaining = bytes.size
        var index = 0

        while (remaining-- > 0) {
            length += remaining
            val byte = bytes[index]
            crc32 = updateCrc32(crc32, byte)
            index++
        }

        value = crc32
    }
}