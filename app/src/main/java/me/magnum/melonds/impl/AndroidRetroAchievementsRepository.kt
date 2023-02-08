package me.magnum.melonds.impl

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.magnum.melonds.domain.model.retroachievements.RAUserAchievement
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository
import me.magnum.rcheevosapi.RAApi
import me.magnum.rcheevosapi.RAUserAuthStore
import me.magnum.rcheevosapi.model.RAAchievement
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
        val (gameAchievementsResult, userUnlocksResult) = coroutineScope {
            awaitAll(
                async {
                    raApi.getGameInfo(gameId).map { game ->
                        game.achievements
                            .filter {
                                it.type == RAAchievement.Type.CORE
                            }
                    }
                },
                async {
                    raApi.getUserUnlockedAchievements(gameId, false)
                },
            )
        }

        if (gameAchievementsResult.isFailure) {
            return Result.failure(gameAchievementsResult.exceptionOrNull()!!)
        }
        if (userUnlocksResult.isFailure) {
            return Result.failure(userUnlocksResult.exceptionOrNull()!!)
        }

        @Suppress("UNCHECKED_CAST")
        val gameAchievements = (gameAchievementsResult as Result<List<RAAchievement>>).getOrThrow()
        @Suppress("UNCHECKED_CAST")
        val userUnlocks = (userUnlocksResult as Result<List<Long>>).getOrThrow()

        val userAchievements = gameAchievements.map {
            RAUserAchievement(
                achievement = it,
                isUnlocked = userUnlocks.contains(it.id),
            )
        }
        return Result.success(userAchievements)
    }
}