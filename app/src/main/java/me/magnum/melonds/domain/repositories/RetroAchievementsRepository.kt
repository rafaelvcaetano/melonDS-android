package me.magnum.melonds.domain.repositories

import me.magnum.melonds.domain.model.retroachievements.RAGameSummary
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.model.retroachievements.RAUserGameData
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAAwardAchievementResponse
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RALeaderboard
import me.magnum.rcheevosapi.model.RASubmitLeaderboardEntryResponse
import me.magnum.rcheevosapi.model.RAUserAuth

interface RetroAchievementsRepository {
    suspend fun isUserAuthenticated(): Boolean
    suspend fun getUserAuthentication(): RAUserAuth?
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun getUserGameData(gameHash: String, forHardcoreMode: Boolean): Result<RAUserGameData?>
    suspend fun getGameSummary(gameHash: String): RAGameSummary?
    suspend fun getGameSummary(gameId: RAGameId): RAGameSummary?
    suspend fun getAchievement(achievementId: Long): Result<RAAchievement?>
    suspend fun awardAchievement(achievement: RAAchievement, forHardcoreMode: Boolean): Result<RAAwardAchievementResponse>
    suspend fun submitPendingAchievements(): Result<Unit>
    suspend fun getLeaderboard(leaderboardId: Long): RALeaderboard?
    suspend fun submitLeaderboardEntry(leaderboardId: Long, value: Int): Result<RASubmitLeaderboardEntryResponse>
    suspend fun startSession(gameHash: String): Result<Unit>
    suspend fun sendSessionHeartbeat(gameHash: String, richPresenceDescription: String?)
}