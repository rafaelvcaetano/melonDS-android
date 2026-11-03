package me.magnum.rcheevosapi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.magnum.rcheevosapi.model.RAUserAuth
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
