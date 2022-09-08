package me.magnum.melonds.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.repositories.DSiWareMetadataRepository
import java.net.URL

class NusDSiWareMetadataRepository : DSiWareMetadataRepository {

    override suspend fun getDSiWareTitleMetadata(categoryId: UInt, titleId: UInt): ByteArray = withContext(Dispatchers.IO) {
        val categoryIdHex = categoryId.toString(16).padStart(8, '0')
        val titleIdHex = titleId.toString(16).padStart(8, '0')
        val url = "http://nus.cdn.t.shop.nintendowifi.net/ccs/download/$categoryIdHex$titleIdHex/tmd"
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        // Only 520 bytes are needed
        val tmdMetadata = ByteArray(520)
        connection.getInputStream().use {
            it.read(tmdMetadata)
        }

        tmdMetadata
    }
}