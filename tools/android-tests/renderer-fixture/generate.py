#!/usr/bin/env python3
"""Generate original DS homebrew, with no ROM, BIOS or Nintendo logo input.

The ARM9 program enables both normal 2D displays and swaps red/blue backdrop
palette entries every sixteen VBlanks. ARM7 executes a self-branch. This is an
emulator direct-boot fixture, not a signed retail cartridge image.
"""
from pathlib import Path
import hashlib
import json
import struct
import sys

OUT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[3] / "app/src/androidTest/assets"
OUT.mkdir(parents=True, exist_ok=True)
words, listing, labels, literals, patches = [], [], {}, {}, []

def emit(word, asm):
    words.append(word)
    listing.append(asm)

def mark(label):
    labels[label] = len(words) * 4

def load(register, name, value):
    literals[name] = value
    patches.append((len(words), 'literal', name, register))
    emit(0, f'ldr r{register}, ={value:#010x} ; {name}')

def branch(label, condition=14):
    patches.append((len(words), 'branch', label, condition))
    emit(0, f'b{ {14:"", 2:"hs", 3:"lo"}[condition]} {label}')

load(0, 'power', 0x04000304)
load(1, 'power_value', 0x820F)
emit(0xE1C010B0, 'strh r1, [r0]')
load(0, 'main_display', 0x04000000)
load(1, 'normal_display', 0x00010000)
emit(0xE5801000, 'str r1, [r0]')
load(0, 'sub_display', 0x04001000)
emit(0xE5801000, 'str r1, [r0]')
load(2, 'main_palette', 0x05000000)
load(3, 'sub_palette', 0x05000400)
load(4, 'vcount', 0x04000006)
load(7, 'red', 0x001F)
load(8, 'blue', 0x7C00)
emit(0xE3A05000, 'mov r5, #0')
mark('wait_visible')
emit(0xE1D460B0, 'ldrh r6, [r4]')
emit(0xE35600C0, 'cmp r6, #192')
branch('wait_visible', 2)
mark('wait_vblank')
emit(0xE1D460B0, 'ldrh r6, [r4]')
emit(0xE35600C0, 'cmp r6, #192')
branch('wait_vblank', 3)
emit(0xE2855001, 'add r5, r5, #1')
emit(0xE3150010, 'tst r5, #16')
emit(0x01C270B0, 'strheq r7, [r2]')
emit(0x11C280B0, 'strhne r8, [r2]')
emit(0x01C380B0, 'strheq r8, [r3]')
emit(0x11C370B0, 'strhne r7, [r3]')
branch('wait_visible')
code_word_count = len(words)
for name, value in literals.items():
    labels[name] = len(words) * 4
    emit(value, f'.word {value:#010x} ; {name}')
for index, kind, name, operand in patches:
    displacement = labels[name] - (index * 4 + 8)
    if kind == 'literal':
        assert 0 <= displacement < 4096
        words[index] = 0xE59F0000 | (operand << 12) | displacement
        assert index * 4 + 8 + (words[index] & 4095) == labels[name]
    else:
        assert displacement % 4 == 0
        words[index] = (operand << 28) | 0x0A000000 | ((displacement // 4) & 0xFFFFFF)

arm9 = struct.pack('<' + 'I' * len(words), *words)
arm7 = struct.pack('<I', 0xEAFFFFFE)
rom = bytearray(0x20000)
rom[0:12] = b'GLES PROBE  '
rom[0x0C:0x10] = b'####'  # core's explicit homebrew identifier
rom[0x10:0x12] = b'00'
struct.pack_into('<IIII', rom, 0x20, 0x200, 0x02000000, 0x02000000, len(arm9))
struct.pack_into('<IIII', rom, 0x30, 0x800, 0x03800000, 0x03800000, len(arm7))
struct.pack_into('<I', rom, 0x68, 0x1000)
struct.pack_into('<II', rom, 0x80, len(rom), 0x200)
rom[0x200:0x200 + len(arm9)] = arm9
rom[0x800:0x804] = arm7
struct.pack_into('<H', rom, 0x1000, 1)
title = 'GLES fallback probe\nGenerated homebrew\nOriginal test code'.encode('utf-16le')
for language in range(6):
    offset = 0x1000 + 0x240 + language * 0x100
    rom[offset:offset + len(title)] = title

def crc16(data):
    value = 0xFFFF
    for byte in data:
        value ^= byte
        for _ in range(8):
            value = (value >> 1) ^ (0xA001 if value & 1 else 0)
    return value

# Logo remains zero; its checksum is internally consistent, with no logo copied.
struct.pack_into('<H', rom, 0x15C, crc16(rom[0xC0:0x15C]))
struct.pack_into('<H', rom, 0x15E, crc16(rom[:0x15E]))
struct.pack_into('<H', rom, 0x1002, crc16(rom[0x1020:0x1840]))
assert not any(rom[0xC0:0x15C])
assert len(arm9) < 0x600
assert struct.unpack_from('<I', rom, 0x20)[0] < 0x4000
assert struct.unpack_from('<H', rom, 0x15E)[0] == crc16(rom[:0x15E])

(OUT/'fallback-homebrew.nds').write_bytes(rom)
(OUT/'arm9-listing.txt').write_text('\n'.join(
    f'{0x02000000 + i*4:08x}: {word:08x}  {listing[i]}' for i, word in enumerate(words)
) + '\n')
metadata = {
    'status': 'generated; header/layout assertions passed; NOT emulator-executed',
    'sha256': hashlib.sha256(rom).hexdigest(), 'bytes': len(rom),
    'arm9_bytes': len(arm9), 'arm9_instructions': code_word_count,
    'arm7_bytes': len(arm7), 'logo': 'all zero; no external input',
    'expected_scene': 'complementary red/blue backdrops on both screens; swaps every 16 VBlanks',
}
(OUT/'fixture.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps(metadata, indent=2))
