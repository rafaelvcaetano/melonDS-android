package me.magnum.melonds.impl

import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.rcheevosapi.RAApi
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAGameId

class AndroidRetroAchievementsRepository(
    private val raApi: RAApi,
    private val raUserAuthStore: RAUserAuthStore,
) : RetroAchievementsRepository {

    override suspend fun isUserAuthenticated(): Boolean {
        return raUserAuthStore.getUserAuth() != null
    }

    override suspend fun login(username: String, password: String): Result<Unit> {
        return raApi.login(username, password)
    }

    override suspend fun getGameUserAchievements(gameId: RAGameId): Result<List<RAUserAchievement>> {
        return raApi.getGameInfo(gameId).map { game ->
            game.achievements.map {
                RAUserAchievement(
                    it,
                    true,
                )
            }
        }
    }
}