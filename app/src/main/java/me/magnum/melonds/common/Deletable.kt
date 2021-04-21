package me.magnum.melonds.common

/**
 * Represents an entity that can be deleted but may be kept in memory. This can be useful, for example, to support soft-deleting of entities.
 */
class Deletable<T>(val data: T, var isDeleted: Boolean)