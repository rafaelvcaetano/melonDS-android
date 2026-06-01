package me.magnum.melonds.common.retroachievements

import android.content.SharedPreferences
import androidx.core.content.edit
import me.magnum.rcheevosapi.RAHostUrlProvider
import java.net.URI
import java.util.Locale

class SharedPreferencesRAHostUrlProvider(
    private val sharedPreferences: SharedPreferences,
) : RAHostUrlProvider {

    companion object {
        const val HOST_URL_KEY = "ra_host_url"
        const val DEFAULT_BASE_URL = "https://retroachievements.org/dorequest.php"
        private const val HARDCORE_ENABLED_KEY = "ra_hardcore_enabled"
        private const val HARDCORE_RESTORE_KEY = "ra_hardcore_enabled_restore"
    }

    override fun getBaseUrl(): String {
        val configuredUrl = sharedPreferences.getString(HOST_URL_KEY, null)
        val normalizedUrl = normalize(configuredUrl)

        return normalizedUrl ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(host: String): Boolean {
        val normalizedHost = normalize(host) ?: return false
        val hasExistingOverride = !sharedPreferences.getString(HOST_URL_KEY, null).isNullOrBlank()
        val currentHardcoreEnabled = sharedPreferences.getBoolean(HARDCORE_ENABLED_KEY, false)

        sharedPreferences.edit(commit = true) {
            putString(HOST_URL_KEY, normalizedHost)
            if (!hasExistingOverride) {
                putBoolean(HARDCORE_RESTORE_KEY, currentHardcoreEnabled)
            }
            putBoolean(HARDCORE_ENABLED_KEY, false)
        }

        return true
    }

    fun clearBaseUrl() {
        val hasRestoreValue = sharedPreferences.contains(HARDCORE_RESTORE_KEY)
        val restoreHardcore = sharedPreferences.getBoolean(HARDCORE_RESTORE_KEY, false)

        sharedPreferences.edit(commit = true) {
            remove(HOST_URL_KEY)
            if (hasRestoreValue) {
                putBoolean(HARDCORE_ENABLED_KEY, restoreHardcore)
            }
            remove(HARDCORE_RESTORE_KEY)
        }
    }

    private fun normalize(value: String?): String? {
        val trimmedValue = value?.trim().orEmpty()

        if (trimmedValue.isEmpty()) {
            return null
        }

        val candidate = if ("://" in trimmedValue) {
            trimmedValue
        } else {
            "http://$trimmedValue"
        }

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null

        if (scheme != "http") {
            return null
        }

        if (host != "127.0.0.1" && host != "localhost") {
            return null
        }

        if (uri.port !in 1..65535) {
            return null
        }

        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/" && uri.rawPath != "/dorequest.php") {
            return null
        }

        if (uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null) {
            return null
        }

        return URI(scheme, null, host, uri.port, "/dorequest.php", null, null).toASCIIString()
    }
}
