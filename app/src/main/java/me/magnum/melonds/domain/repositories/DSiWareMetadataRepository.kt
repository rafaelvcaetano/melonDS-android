package me.magnum.melonds.domain.repositories

interface DSiWareMetadataRepository {
    suspend fun getDSiWareTitleMetadata(categoryId: UInt, titleId: UInt): ByteArray
}