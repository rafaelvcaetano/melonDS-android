package me.magnum.melonds.impl

import me.magnum.melonds.impl.dtos.rom.normalizeStorageNameVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomStorageNameTest {
    @Test
    fun `unsafe provider labels become a single digest-bound leaf`() {
        listOf("../victim.nds", "a/b.nds", "a\\b.nds", ".", "..", "\u0000.nds").forEach { label ->
            val stem = RomStorageName.stem(label, "content://host/doc/1", collisionSafe = true)
            assertFalse('/' in stem)
            assertFalse('\\' in stem)
            assertFalse(stem == "." || stem == "..")
            assertTrue(stem.startsWith("rom-"))
        }
    }

    @Test
    fun `ad hoc documents with the same display name remain distinct`() {
        val first = RomStorageName.stem("game.multi.nds", "content://host/doc/1", collisionSafe = true)
        val second = RomStorageName.stem("game.multi.nds", "content://host/doc/2", collisionSafe = true)

        assertNotEquals(first, second)
        assertTrue(first.startsWith("game.multi-"))
    }

    @Test
    fun `safe library leaf preserves the historical basename`() {
        assertEquals("Pokémon – Test", RomStorageName.stem("Pokémon – Test.nds", "stable", collisionSafe = false))
    }

    @Test
    fun `only a pre-upgrade exact identity receives a legacy lookup`() {
        val migrated = RomStorageName.compatibleStems(
            "game.multi.nds", "content://host/doc/1", collisionSafe = true, allowLegacyFallback = true
        )
        val fresh = RomStorageName.compatibleStems(
            "game.multi.nds", "content://host/doc/2", collisionSafe = true, allowLegacyFallback = false
        )

        assertEquals(2, migrated.size)
        assertEquals("game.multi", migrated.last())
        assertEquals(1, fresh.size)
    }

    @Test
    fun `unsafe pre-upgrade labels never receive a legacy lookup`() {
        val stems = RomStorageName.compatibleStems(
            "../victim.nds", "content://host/doc/1", collisionSafe = true, allowLegacyFallback = true
        )
        assertEquals(1, stems.size)
        assertTrue(stems.single().startsWith("rom-"))
    }

    @Test
    fun `only a missing pre-upgrade cache marker enables migration`() {
        assertEquals(0, normalizeStorageNameVersion(null))
        assertEquals(0, normalizeStorageNameVersion(0))
        assertEquals(1, normalizeStorageNameVersion(1))
        assertEquals(1, normalizeStorageNameVersion(-1))
        assertEquals(1, normalizeStorageNameVersion(99))
    }

}
