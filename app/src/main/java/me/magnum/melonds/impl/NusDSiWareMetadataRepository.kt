package me.magnum.melonds.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.domain.repositories.DSiWareMetadataRepository
import java.io.DataInputStream
import java.net.URL
import java.net.URLConnection

class NusDSiWareMetadataRepository(
    private val metadataBaseUrl: URL? = null,
    private val openConnection: (URL) -> URLConnection = { it.openConnection() },
) : DSiWareMetadataRepository {

    /** Keeps the existing injected-transport test seam without enabling a production endpoint. */
    internal constructor(openConnection: (URL) -> URLConnection) : this(
        metadataBaseUrl = URL("https://metadata.invalid/"),
        openConnection = openConnection,
    )

    override suspend fun getDSiWareTitleMetadata(categoryId: UInt, titleId: UInt): ByteArray = withContext(Dispatchers.IO) {
        val baseUrl = metadataBaseUrl
            ?: throw SecurityException("No authenticated DSiWare metadata source is configured")
        if (baseUrl.protocol != "https") {
            throw SecurityException("DSiWare metadata requires an authenticated HTTPS source")
        }

        val categoryIdHex = categoryId.toString(16).padStart(8, '0')
        val titleIdHex = titleId.toString(16).padStart(8, '0')
        val url = baseUrl.toURI().resolve("$categoryIdHex$titleIdHex/tmd").toURL()
        val connection = openConnection(url).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        // Only 520 bytes are needed
        val tmdMetadata = ByteArray(520)
        connection.getInputStream().use {
            DataInputStream(it).readFully(tmdMetadata)
        }

        tmdMetadata
    }
}
