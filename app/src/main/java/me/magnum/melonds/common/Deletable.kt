package me.magnum.melonds.common

/**
 * Represents an entity that can be deleted but may be kept in memory. This can be useful, for example, to support soft-deleting of entities.
 */
data class Deletable<T>(val data: T, val isDeleted: Boolean)

fun <T> List<Deletable<T>>.filterNotDeleted(): List<T> {
    return mapNotNull {
        if (it.isDeleted) {
            null
        } else {
            it.data
        }
    }
}