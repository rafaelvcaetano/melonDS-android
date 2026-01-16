package me.magnum.melonds.impl.retroachievements

import me.magnum.melonds.database.daos.RetroAchievementsDao
import me.magnum.melonds.database.entities.retroachievements.RAAchievementEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameHashEntity
import me.magnum.melonds.database.entities.retroachievements.RAGameSetMetadata
import me.magnum.melonds.database.entities.retroachievements.RALeaderboardEntity
import me.magnum.melonds.database.entities.retroachievements.RAPendingAchievementSubmissionEntity
import me.magnum.melonds.database.entities.retroachievements.RAUserAchievementEntity

/**
 * An [RetroAchievementsDao] implementation that disables most functionality related to data caching that should not be stored to guarantee the best integration with the
 * RetroAchievements platform. This implementation allows the caching implementation to be maintained but not used. If in the future that implementation proves useful, this
 * DAO usage can be replaced with the actual DAO that interacts with the database.
 * The only data that is actually not allowed to be cached is game set metadata and pending achievement submissions. All other data is maintained so that it can be used in an
 * ongoing session.
 *
 * @param actualAchievementsDao The DAO that should be used for operations that are actually supported and actually stores and fetches the data
 */
class NoCacheRetroAchievementsDao(private val actualAchievementsDao: RetroAchievementsDao) : RetroAchievementsDao() {

    override suspend fun getGameSetMetadata(gameId: Long): RAGameSetMetadata? {
        return null
    }

    override suspend fun updateGameSetMetadata(gameSetMetadata: RAGameSetMetadata) {
    }

    override suspend fun clearAllGameSetMetadataLastUserDataUpdate() {
    }

    override suspend fun getGameAchievements(gameId: Long): List<RAAchievementEntity> {
        return actualAchievementsDao.getGameAchievements(gameId)
    }

    override suspend fun getAchievement(achievementId: Long): RAAchievementEntity? {
        return actualAchievementsDao.getAchievement(achievementId)
    }

    override suspend fun deleteGameAchievements(gameId: Long) {
    }

    override suspend fun insertGameAchievements(achievements: List<RAAchievementEntity>) {
    }

    override suspend fun getLeaderboard(leaderboardId: Long): RALeaderboardEntity? {
        return actualAchievementsDao.getLeaderboard(leaderboardId)
    }

    override suspend fun getGameLeaderboards(gameId: Long): List<RALeaderboardEntity> {
        return actualAchievementsDao.getGameLeaderboards(gameId)
    }

    override suspend fun deleteGameLeaderboards(gameId: Long) {
    }

    override suspend fun insertGameLeaderboards(leaderboards: List<RALeaderboardEntity>) {
    }

    override suspend fun updateGameData(gameData: RAGameEntity) {
    }

    override suspend fun updateGameData(gameEntity: RAGameEntity, achievements: List<RAAchievementEntity>, leaderboards: List<RALeaderboardEntity>) {
        actualAchievementsDao.updateGameData(gameEntity, achievements, leaderboards)
    }

    override suspend fun getGame(gameId: Long): RAGameEntity? {
        return actualAchievementsDao.getGame(gameId)
    }

    override suspend fun getGameUserUnlockedAchievements(gameId: Long, forHardcoreMode: Boolean): List<RAUserAchievementEntity> {
        return actualAchievementsDao.getGameUserUnlockedAchievements(gameId, forHardcoreMode)
    }

    override suspend fun updateGameUserUnlockedAchievements(gameId: Long, userAchievements: List<RAUserAchievementEntity>) {
        actualAchievementsDao.updateGameUserUnlockedAchievements(gameId, userAchievements)
    }

    override suspend fun addUserAchievement(userAchievement: RAUserAchievementEntity) {
        actualAchievementsDao.addUserAchievement(userAchievement)
    }

    override suspend fun insertGameUserUnlockedAchievements(userAchievements: List<RAUserAchievementEntity>) {
    }

    override suspend fun deleteGameUserUnlockedAchievements(gameId: Long) {
    }

    override suspend fun deleteAllUserUnlockedAchievements() {
    }

    override suspend fun deleteAllAchievementUserData() {
        actualAchievementsDao.deleteAllAchievementUserData()
    }

    override suspend fun addPendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity) {
    }

    override suspend fun getPendingAchievementSubmissions(): List<RAPendingAchievementSubmissionEntity> {
        return emptyList()
    }

    override suspend fun removePendingAchievementSubmission(pendingAchievementSubmission: RAPendingAchievementSubmissionEntity) {
    }

    override suspend fun deleteAllPendingAchievementSubmissions() {
    }

    override suspend fun deleteGameHashLibrary() {
        actualAchievementsDao.deleteGameHashLibrary()
    }

    override suspend fun insertGameHashLibrary(hashLibrary: List<RAGameHashEntity>) {
    }

    override suspend fun getGameHashEntity(gameHash: String): RAGameHashEntity? {
        return actualAchievementsDao.getGameHashEntity(gameHash)
    }

    override suspend fun updateGameHashLibrary(hashLibrary: List<RAGameHashEntity>) {
        actualAchievementsDao.updateGameHashLibrary(hashLibrary)
    }
}