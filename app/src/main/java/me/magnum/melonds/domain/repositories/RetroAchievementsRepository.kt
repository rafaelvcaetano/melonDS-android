package me.magnum.melonds.domain.repositories

import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement

interface RetroAchievementsRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun getGameUserAchievements(gameHash: String): Result<List<RAUserAchievement>>
}