package me.magnum.melonds.common.retroachievements

import android.content.SharedPreferences
import androidx.core.content.edit
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAUserAuth

class AndroidRAUserAuthStore(
    private val sharedPreferences: SharedPreferences,
    private val legacySharedPreferences: SharedPreferences? = null,
) : RAUserAuthStore {

    companion object {
        const val PREFERENCES_NAME = "ra_auth"
        const val USERNAME_KEY = "ra_username"
        const val TOKEN_KEY = "ra_token"
    }

    init {
        migrateLegacyCredentials()
    }

    private fun migrateLegacyCredentials() {
        val legacy = legacySharedPreferences ?: return
        val legacyUsername = legacy.getString(USERNAME_KEY, null)
        val legacyToken = legacy.getString(TOKEN_KEY, null)
        if (legacyUsername == null && legacyToken == null) return

        val currentUsername = sharedPreferences.getString(USERNAME_KEY, null)
        val currentToken = sharedPreferences.getString(TOKEN_KEY, null)
        val canMigrate = currentUsername == null && currentToken == null
        val isDuplicate = currentUsername == legacyUsername && currentToken == legacyToken

        if (canMigrate) {
            val migrated = sharedPreferences.edit()
                .putString(USERNAME_KEY, legacyUsername)
                .putString(TOKEN_KEY, legacyToken)
                .commit()
            if (!migrated) return
        } else if (!isDuplicate) {
            // The isolated store is already authoritative. Keeping a conflicting
            // legacy copy only prolongs credential exposure and could resurrect it.
            legacy.edit().remove(USERNAME_KEY).remove(TOKEN_KEY).commit()
            return
        }

        legacy.edit().remove(USERNAME_KEY).remove(TOKEN_KEY).commit()
    }

    override suspend fun storeUserAuth(userAuth: RAUserAuth.Authenticated) {
        sharedPreferences.edit {
            putString(USERNAME_KEY, userAuth.username)
            putString(TOKEN_KEY, userAuth.token)
        }
    }

    override suspend fun getUserAuth(): RAUserAuth? {
        val username = sharedPreferences.getString(USERNAME_KEY, null) ?: return null
        val token = sharedPreferences.getString(TOKEN_KEY, null) ?: return RAUserAuth.AuthenticationExpired(username)

        return RAUserAuth.Authenticated(username, token)
    }

    override suspend fun clearUserAuth() {
        sharedPreferences.edit {
            remove(USERNAME_KEY)
            remove(TOKEN_KEY)
        }
    }

    override suspend fun clearUserToken() {
        sharedPreferences.edit {
            remove(TOKEN_KEY)
        }
    }
}
