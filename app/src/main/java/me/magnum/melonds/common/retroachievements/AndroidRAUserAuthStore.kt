package me.magnum.melonds.common.retroachievements

import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAUserAuth

class AndroidRAUserAuthStore(private val retroAchievementsIniStore: RetroAchievementsIniStore) : RAUserAuthStore {

    override suspend fun storeUserAuth(userAuth: RAUserAuth.Authenticated) {
        retroAchievementsIniStore.storeUserAuth(userAuth.username, userAuth.token)
    }

    override suspend fun getUserAuth(): RAUserAuth? {
        val username = retroAchievementsIniStore.getUsername() ?: return null
        val token = retroAchievementsIniStore.getToken() ?: return RAUserAuth.AuthenticationExpired(username)

        return RAUserAuth.Authenticated(username, token)
    }

    override suspend fun clearUserAuth() {
        retroAchievementsIniStore.clearUserAuth()
    }

    override suspend fun clearUserToken() {
        retroAchievementsIniStore.clearUserToken()
    }
}
