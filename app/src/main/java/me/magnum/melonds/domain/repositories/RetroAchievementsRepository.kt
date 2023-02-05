package me.magnum.melonds.domain.repositories

import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.rcheevosapi.model.RAGameId

interface RetroAchievementsRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun getGameUserAchievements(gameId: RAGameId): Result<List<RAUserAchievement>>
}