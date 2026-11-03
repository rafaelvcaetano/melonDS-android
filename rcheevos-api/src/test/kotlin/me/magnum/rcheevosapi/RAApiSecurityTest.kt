package me.magnum.rcheevosapi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.magnum.rcheevosapi.model.RAUserAuth
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class RAApiSecurityTest {
    @Test
    fun `login sends password in form body and never in url`() = runBlocking {
        var captured: okhttp3.Request? = null
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            captured = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"Success":true,"User":"player","Token":"token","Score":0,"SoftcoreScore":0}""".toResponseBody("application/json".toMediaType()))
                .build()
        }).build()
        val api = RAApi(client, Json { ignoreUnknownKeys = true }, authStore(), signatureProvider())

        assertTrue(api.login("player", "top secret").isSuccess)

        val request = requireNotNull(captured)
        assertEquals("POST", request.method)
        assertFalse(request.url.toString().contains("top+secret"))
        assertFalse(request.url.toString().contains("top%20secret"))
        val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("p=top+secret"))
    }

    @Test
    fun `declared oversized response is rejected before reading and closed`() = runBlocking {
        val body = TrackingBody("{}", RAApi.MAX_RESPONSE_BYTES.toLong() + 1)
        val api = apiReturning(200, body)

        assertTrue(api.login("player", "password").isFailure)
        assertEquals(0, body.readBytes)
        assertTrue(body.closed.get())
    }

    @Test
    fun `chunked response above the byte budget is stopped and closed`() = runBlocking {
        val body = TrackingBody("x".repeat(RAApi.MAX_RESPONSE_BYTES + 1), -1)
        val api = apiReturning(200, body)

        assertTrue(api.login("player", "password").isFailure)
        assertTrue(body.readBytes <= RAApi.MAX_RESPONSE_BYTES + 1)
        assertTrue(body.closed.get())
    }

    @Test
    fun `http failures close their response body`() = runBlocking {
        val body = TrackingBody("failure")
        val api = apiReturning(500, body)

        assertTrue(api.login("player", "password").isFailure)
        assertTrue(body.closed.get())
    }

    private fun apiReturning(code: Int, body: ResponseBody): RAApi {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Failure")
                .body(body)
                .build()
        }).build()
        return RAApi(client, Json { ignoreUnknownKeys = true }, authStore(), signatureProvider())
    }

    private class TrackingBody(
        text: String,
        private val declaredLength: Long = text.encodeToByteArray().size.toLong(),
    ) : ResponseBody() {
        private val bytes = text.encodeToByteArray()
        val closed = AtomicBoolean(false)
        var readBytes = 0
            private set

        override fun contentType() = "application/json".toMediaType()
        override fun contentLength() = declaredLength
        override fun source(): BufferedSource {
            val buffer = Buffer().write(bytes)
            return object : ForwardingSource(buffer) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return super.read(sink, byteCount).also { if (it > 0) readBytes += it.toInt() }
                }

                override fun close() {
                    closed.set(true)
                    super.close()
                }
            }.buffer()
        }
    }

    private fun authStore() = object : RAUserAuthStore {
        private var auth: RAUserAuth? = null
        override suspend fun storeUserAuth(userAuth: RAUserAuth.Authenticated) { auth = userAuth }
        override suspend fun getUserAuth() = auth
        override suspend fun clearUserAuth() { auth = null }
        override suspend fun clearUserToken() { auth = (auth as? RAUserAuth.Authenticated)?.let { RAUserAuth.AuthenticationExpired(it.username) } }
    }

    private fun signatureProvider() = object : RASignatureProvider {
        override fun provideAchievementSignature(achievementId: Long, userAuth: RAUserAuth.Authenticated, forHardcoreMode: Boolean) = "signature"
        override fun provideLeaderboardSignature(leaderboardId: Long, value: Int, userAuth: RAUserAuth.Authenticated) = "signature"
    }
}
