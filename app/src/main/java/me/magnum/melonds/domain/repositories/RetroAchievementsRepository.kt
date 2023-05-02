package me.magnum.melonds.domain.repositories

import me.magnum.melonds.domain.model.retroachievements.RAGameSummary
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAUserAuth

interface RetroAchievementsRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun getUserAuthentication(): RAUserAuth?
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun getGameUserAchievements(gameHash: String, forHardcoreMode: Boolean): Result<List<RAUserAchievement>>
    suspend fun getGameSummary(gameHash: String): RAGameSummary?
    suspend fun getAchievement(achievementId: Long): Result<RAAchievement?>
    suspend fun awardAchievement(achievement: RAAchievement, forHardcoreMode: Boolean): Result<Unit>
    suspend fun submitPendingAchievements(): Result<Unit>
    suspend fun startSession(gameHash: String): Result<Unit>
    suspend fun sendSessionHeartbeat(gameHash: String, richPresenceDescription: String?)
}