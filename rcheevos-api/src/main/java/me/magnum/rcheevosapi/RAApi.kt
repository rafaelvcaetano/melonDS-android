package me.magnum.rcheevosapi

import com.google.gson.Gson
import me.magnum.rcheevosapi.dto.GameIdDto
import me.magnum.rcheevosapi.dto.GamePatchDto
import me.magnum.rcheevosapi.dto.UserLoginDto
import me.magnum.rcheevosapi.dto.UserUnlocksDto
import me.magnum.rcheevosapi.dto.mapper.mapToModel
import me.magnum.rcheevosapi.exception.UserNotAuthenticatedException
import me.magnum.rcheevosapi.model.RAAchievement
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RAUserAuth
import okhttp3.*
import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class RAApi(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val userAuthStore: UserAuthStore,
    private val achievementSignatureProvider: RAAchievementSignatureProvider,
) {

    companion object {
        private const val PARAMETER_USER = "u"
        private const val PARAMETER_PASSWORD = "p"
        private const val PARAMETER_TOKEN = "t"
        private const val PARAMETER_REQUEST = "r"
        private const val PARAMETER_GAME_ID = "g"
        private const val PARAMETER_ACHIEVEMENT_ID = "a"
        private const val PARAMETER_IS_HARDMODE = "h"
        private const val PARAMETER_ACTIVITY_TYPE = "a"
        private const val PARAMETER_GAME_HASH = "m"
        private const val PARAMETER_SIGNATURE = "v"

        private const val VALUE_HARDMODE_DISABLED = "0"
        private const val VALUE_HARDMODE_ENABLED = "1"

        private const val REQUEST_LOGIN = "login"
        private const val REQUEST_GAME_ID = "gameid"
        private const val REQUEST_GAME_DATA = "patch"
        private const val REQUEST_USER_UNLOCKED_ACHIEVEMENTS = "unlocks"
        private const val REQUEST_POST_ACTIVITY = "postactivity"
        private const val REQUEST_AWARD_ACHIEVEMENT = "awardachievement"
        private const val REQUEST_PING = "ping"

        private const val ACTIVITY_TYPE_START_SESSION = "3"
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        return get<UserLoginDto>(
            mapOf(
                PARAMETER_REQUEST to REQUEST_LOGIN,
                PARAMETER_USER to username,
                PARAMETER_PASSWORD to password,
            )
        ).onSuccess {
            userAuthStore.storeUserAuth(RAUserAuth(username, it.token))
        }.map { }
    }

    suspend fun getUserUnlockedAchievements(gameId: RAGameId, forHardcoreMode: Boolean): Result<List<Long>> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get<UserUnlocksDto>(
            mapOf(
                PARAMETER_REQUEST to REQUEST_USER_UNLOCKED_ACHIEVEMENTS,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_GAME_ID to gameId.toString(),
                PARAMETER_IS_HARDMODE to if (forHardcoreMode) VALUE_HARDMODE_ENABLED else VALUE_HARDMODE_DISABLED,
            )
        ).map {
            it.userUnlocks
        }
    }

    suspend fun getGameId(gameHash: String): Result<RAGameId> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get<GameIdDto>(
            mapOf(
                PARAMETER_REQUEST to REQUEST_GAME_ID,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_GAME_HASH to gameHash,
            )
        ).mapCatching {
            RAGameId(it.gameId)
        }
    }

    suspend fun getGameInfo(gameId: RAGameId): Result<RAGame> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get<GamePatchDto>(
            mapOf(
                PARAMETER_REQUEST to REQUEST_GAME_DATA,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_GAME_ID to gameId.id.toString(),
            )
        ).mapCatching {
            it.game.mapToModel()
        }
    }

    suspend fun startSession(gameId: RAGameId): Result<Unit> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get(
            mapOf(
                PARAMETER_REQUEST to REQUEST_POST_ACTIVITY,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_ACTIVITY_TYPE to ACTIVITY_TYPE_START_SESSION,
                PARAMETER_GAME_ID to gameId.id.toString(),
            )
        )
    }

    suspend fun awardAchievement(achievement: RAAchievement, gameId: RAGameId, forHardcoreMode: Boolean): Result<Unit> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        val signature = achievementSignatureProvider.provideAchievementSignature(achievement, userAuth, forHardcoreMode)

        return get(
            mapOf(
                PARAMETER_REQUEST to REQUEST_AWARD_ACHIEVEMENT,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_ACHIEVEMENT_ID to achievement.id.toString(),
                // TODO: Maybe send game hash?
                PARAMETER_IS_HARDMODE to if (forHardcoreMode) VALUE_HARDMODE_ENABLED else VALUE_HARDMODE_DISABLED,
                PARAMETER_SIGNATURE to signature,
            )
        )
    }

    suspend fun sendPing(gameId: RAGameId): Result<Unit> {
        // NOTE: Call this every 2 minutes if rich presence is enabled or every 4 minutes if not
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get(
            mapOf(
                PARAMETER_REQUEST to REQUEST_PING,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_GAME_ID to gameId.id.toString(),
                // TODO: Support rich presence
            )
        )
    }

    private suspend inline fun <reified T> get(parameters: Map<String, String>): Result<T> {
        val request = buildGetRequest(parameters)
        val response = executeRequest(request)
        return if (response.isSuccessful) {
            val result = if (T::class == Unit::class) {
                // Ignore response. Don't parse anything
                Unit as T
            } else {
                gson.fromJson(response.body?.charStream(), T::class.java)
            }
            Result.success(result)
        } else {
            Result.failure(Exception(response.message))
        }
    }

    private fun buildGetRequest(parameters: Map<String, String>): Request {
        val query = parameters.map {
            "${it.key}=${it.value}"
        }.joinToString(separator = "&")

        val uri = URI("https", "retroachievements.org", "/dorequest.php", query, null)

        return Request.Builder()
            .get()
            .url(uri.toURL())
            .build()
    }

    private suspend fun executeRequest(request: Request): Response = suspendCoroutine {
        val call = okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                it.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                it.resume(response)
            }
        })
    }
}