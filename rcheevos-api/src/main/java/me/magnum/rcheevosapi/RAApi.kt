package me.magnum.rcheevosapi

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.magnum.melonds.common.suspendMapCatching
import me.magnum.melonds.common.suspendRunCatching
import me.magnum.rcheevosapi.dto.*
import me.magnum.rcheevosapi.dto.mapper.mapToModel
import me.magnum.rcheevosapi.exception.UnsuccessfulRequestException
import me.magnum.rcheevosapi.exception.UserNotAuthenticatedException
import me.magnum.rcheevosapi.model.RAGame
import me.magnum.rcheevosapi.model.RAGameId
import me.magnum.rcheevosapi.model.RAUserAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class RAApi(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val userAuthStore: RAUserAuthStore,
    private val achievementSignatureProvider: RAAchievementSignatureProvider,
) {

    companion object {
        private const val BASE_URL = "https://retroachievements.org/dorequest.php"

        private const val PARAMETER_USER = "u"
        private const val PARAMETER_PASSWORD = "p"
        private const val PARAMETER_TOKEN = "t"
        private const val PARAMETER_REQUEST = "r"
        private const val PARAMETER_GAME_ID = "g"
        private const val PARAMETER_SESSION_GAME_ID = "m"
        private const val PARAMETER_ACHIEVEMENT_ID = "a"
        private const val PARAMETER_IS_HARDMODE = "h"
        private const val PARAMETER_ACTIVITY_TYPE = "a"
        private const val PARAMETER_RICH_PRESENCE = "m"
        private const val PARAMETER_SIGNATURE = "v"

        private const val VALUE_HARDMODE_DISABLED = "0"
        private const val VALUE_HARDMODE_ENABLED = "1"

        private const val REQUEST_LOGIN = "login2"
        private const val REQUEST_HASH_LIBRARY = "hashlibrary"
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

    suspend fun getGameHashList(): Result<Map<String, RAGameId>> {
        return get<HashLibraryDto>(
            mapOf(PARAMETER_REQUEST to REQUEST_HASH_LIBRARY)
        ).map { library ->
            library.md5List.mapValues {
                RAGameId(it.value)
            }
        }
    }

    suspend fun getUserUnlockedAchievements(gameId: RAGameId, forHardcoreMode: Boolean): Result<List<Long>> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return get<UserUnlocksDto>(
            mapOf(
                PARAMETER_REQUEST to REQUEST_USER_UNLOCKED_ACHIEVEMENTS,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_GAME_ID to gameId.id.toString(),
                PARAMETER_IS_HARDMODE to if (forHardcoreMode) VALUE_HARDMODE_ENABLED else VALUE_HARDMODE_DISABLED,
            )
        ).map {
            it.userUnlocks
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
        ).suspendMapCatching {
            it.game.mapToModel()
        }
    }

    suspend fun startSession(gameId: RAGameId): Result<Unit> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        return post(
            mapOf(
                PARAMETER_REQUEST to REQUEST_POST_ACTIVITY,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_ACTIVITY_TYPE to ACTIVITY_TYPE_START_SESSION,
                PARAMETER_SESSION_GAME_ID to gameId.id.toString(),
            )
        )
    }

    suspend fun awardAchievement(achievementId: Long, forHardcoreMode: Boolean): Result<Unit> {
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        val signature = achievementSignatureProvider.provideAchievementSignature(achievementId, userAuth, forHardcoreMode)

        return get(
            mapOf(
                PARAMETER_REQUEST to REQUEST_AWARD_ACHIEVEMENT,
                PARAMETER_USER to userAuth.username,
                PARAMETER_TOKEN to userAuth.token,
                PARAMETER_ACHIEVEMENT_ID to achievementId.toString(),
                // TODO: Maybe send game hash?
                PARAMETER_IS_HARDMODE to if (forHardcoreMode) VALUE_HARDMODE_ENABLED else VALUE_HARDMODE_DISABLED,
                PARAMETER_SIGNATURE to signature,
            ),
            errorHandler = {
                // Ignore errors if the achievement has already been awarded to the user
                if (it != "User already has") {
                    throw UnsuccessfulRequestException(it ?: "Unknown reason")
                }
            }
        )
    }

    suspend fun sendPing(gameId: RAGameId, richPresenceDescription: String?): Result<Unit> {
        // NOTE: Call this every 2 minutes if rich presence is enabled or every 4 minutes if not
        val userAuth = userAuthStore.getUserAuth() ?: return Result.failure(UserNotAuthenticatedException())

        val parameters = mutableMapOf(
            PARAMETER_REQUEST to REQUEST_PING,
            PARAMETER_USER to userAuth.username,
            PARAMETER_TOKEN to userAuth.token,
            PARAMETER_GAME_ID to gameId.id.toString(),
        )

        if (richPresenceDescription != null) {
            parameters[PARAMETER_RICH_PRESENCE] = richPresenceDescription
        }

        return post(parameters)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend inline fun <reified T> get(
        parameters: Map<String, String>,
        errorHandler: (String?) -> Unit = { throw UnsuccessfulRequestException(it ?: "Unknown reason") }
    ): Result<T> {
        val request = buildGetRequest(parameters)
        return suspendRunCatching {
            executeRequest(request)
        }.suspendMapCatching { response ->
            if (response.isSuccessful) {
                val json = JsonParser.parseReader(response.body?.charStream())
                val isSuccessful = json.asJsonObject["Success"].asBoolean
                if (!isSuccessful) {
                    val reason = json.asJsonObject["Error"]?.asString
                    // The error handler may choose to ignore the error
                    errorHandler.invoke(reason)
                }

                if (T::class == Unit::class) {
                    // Ignore response. Don't parse anything
                    Unit as T
                } else {
                    gson.fromJson(json, typeOf<T>().javaType)
                }
            } else {
                throw Exception(response.message)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private suspend inline fun <reified T> post(
        parameters: Map<String, String>,
        errorHandler: (String?) -> Unit = { throw UnsuccessfulRequestException(it ?: "Unknown reason") }
    ): Result<T> {
        val request = buildPostRequest(parameters)
        return suspendRunCatching {
            executeRequest(request)
        }.suspendMapCatching { response ->
            if (response.isSuccessful) {
                val json = JsonParser.parseReader(response.body?.charStream())
                val isSuccessful = json.asJsonObject["Success"].asBoolean
                if (!isSuccessful) {
                    val reason = json.asJsonObject["Error"]?.asString
                    // The error handler may choose to ignore the error
                    errorHandler.invoke(reason)
                }

                if (T::class == Unit::class) {
                    // Ignore response. Don't parse anything
                    Unit as T
                } else {
                    gson.fromJson(json, typeOf<T>().javaType)
                }
            } else {
                throw Exception(response.message)
            }
        }
    }

    private fun buildGetRequest(parameters: Map<String, String>): Request {
        val query = parameters.map {
            "${URLEncoder.encode(it.key, "utf-8")}=${URLEncoder.encode(it.value, "utf-8")}"
        }.joinToString(separator = "&")

        val url = "$BASE_URL?$query"

        return Request.Builder()
            .get()
            .url(url)
            .build()
    }

    private fun buildPostRequest(parameters: Map<String, String>): Request {
        val data = parameters.map {
            "${URLEncoder.encode(it.key, "utf-8")}=${URLEncoder.encode(it.value, "utf-8")}"
        }.joinToString(separator = "&")

        return Request.Builder()
            .post(data.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .url(BASE_URL)
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