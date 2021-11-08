package me.magnum.melonds.domain.model

sealed class SizeUnit(private val sizeInBytes: Long) : Comparable<SizeUnit> {
    class Bytes(bytesSize: Long) : SizeUnit(bytesSize)
    class KB(kbSize: Long) : SizeUnit(kbSize * 1024.toLong())
    class MB(mbSize: Long) : SizeUnit(mbSize * 1024 * 1024.toLong())
    class GB(gbSize: Long) : SizeUnit(gbSize * 1024 * 1024 * 1024.toLong())

    fun toBytes(): Long = sizeInBytes
    fun toKB(): Double = sizeInBytes.toDouble() / 1024
    fun toMB(): Double = sizeInBytes.toDouble() / 1024 / 1024
    fun toGB(): Double = sizeInBytes.toDouble() / 1024 / 1024 / 1024

    fun toBestRepresentation(): SizeUnit {
        if (sizeInBytes < 1024) return Bytes(sizeInBytes)
        if (sizeInBytes / 1024.0 < 1024.0) return KB(sizeInBytes)
        if (sizeInBytes / 1024.0 / 1024.0 < 1024.0) return MB(sizeInBytes)
        return GB(sizeInBytes)
    }

    override fun compareTo(other: SizeUnit): Int {
        return sizeInBytes.compareTo(other.sizeInBytes)
    }

    operator fun plus(other: SizeUnit) = Bytes(sizeInBytes + other.toBytes())
    operator fun plus(bytes: Long) = Bytes(sizeInBytes + bytes)
    operator fun minus(other: SizeUnit) = Bytes(sizeInBytes - other.sizeInBytes)
    operator fun times(multiplier: Int) = Bytes(sizeInBytes * multiplier.toLong())
    operator fun times(multiplier: Long) = Bytes(sizeInBytes * multiplier)
    operator fun times(multiplier: Float) = Bytes((sizeInBytes * multiplier).toLong())
}