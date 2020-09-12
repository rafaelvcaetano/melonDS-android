package me.magnum.melonds.model

import java.util.*

data class Rom(val name: String, val path: String, var config: RomConfig, var lastPlayed: Date? = null) {
    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        if (other == null || javaClass != other.javaClass)
            return false

        val rom = other as Rom
        return path == rom.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}