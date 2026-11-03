package me.magnum.melonds.impl

import java.net.URL
import java.net.URLConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NusDSiWareMetadataRepositorySecurityTest {
    @Test
    fun `metadata import fails closed before contacting the unauthenticated legacy endpoint`() = runTest {
        var opened = false
        val repository = NusDSiWareMetadataRepository(
            metadataBaseUrl = URL("http://nus.cdn.t.shop.nintendowifi.net/ccs/download/"),
            openConnection = { url: URL ->
                opened = true
                object : URLConnection(url) {
                    override fun connect() = Unit
                }
            },
        )

        val failure = runCatching {
            repository.getDSiWareTitleMetadata(0x00030004u, 0x4B4D4A45u)
        }.exceptionOrNull()

        assertTrue(failure is SecurityException)
        assertFalse("the cleartext endpoint must not be contacted", opened)
    }
}
