package me.magnum.melonds.common.retroachievements

import android.content.SharedPreferences
import androidx.core.content.edit
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAUserAuth

class AndroidRAUserAuthStore(private val sharedPreferences: SharedPreferences) : RAUserAuthStore {

    private companion object {
        const val USERNAME_KEY = "ra_username"
        const val USERNAME_TOKEN = "ra_token"
    }

    override suspend fun storeUserAuth(userAuth: RAUserAuth) {
        sharedPreferences.edit {
            putString(USERNAME_KEY, userAuth.username)
            putString(USERNAME_TOKEN, userAuth.token)
        }
    }

    override suspend fun getUserAuth(): RAUserAuth? {
        val username = sharedPreferences.getString(USERNAME_KEY, null) ?: return null
        val token = sharedPreferences.getString(USERNAME_TOKEN, null) ?: return null

        return RAUserAuth(username, token)
    }
}