package me.magnum.melonds.common.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

class MelonOkHttpInterceptor(private val context: Context) : Interceptor {
    private companion object {
        const val USER_AGENT = "User-Agent"
        const val MELON_USER_AGENT_PREFIX = "melonDS-android"
    }

    private val userAgentVersion by lazy {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName
        val userAgentSuffix = appVersion.lowercase().replace(' ', '-').replace("(", "").replace(")", "")
        "$MELON_USER_AGENT_PREFIX/$userAgentSuffix"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val newRequest = chain.request()
            .newBuilder()
            .addHeader(USER_AGENT, userAgentVersion)
            .build()

        return chain.proceed(newRequest)
    }
}