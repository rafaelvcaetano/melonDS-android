package me.magnum.melonds.common.retroachievements

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import me.magnum.rcheevosapi.RAHostUrlProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetroAchievementsIniStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
) : RAHostUrlProvider {

    private data class Config(
        val hostUrl: String,
        val username: String?,
        val token: String?,
        val hardcoreEnabled: Boolean,
        val richPresenceEnabled: Boolean,
        val activeChallengeIndicatorsEnabled: Boolean,
        val progressIndicatorsEnabled: Boolean,
        val leaderboardIndicatorsEnabled: Boolean,
    )

    private companion object {
        const val FILE_NAME = "retroachievements.ini"
        private const val SECTION_NAME = "[RetroAchievements]"

        private const val HOST_URL_KEY = "HostUrl"
        private const val USERNAME_KEY = "Username"
        private const val TOKEN_KEY = "Token"
        private const val HARDCORE_ENABLED_KEY = "HardcoreEnabled"
        private const val RICH_PRESENCE_ENABLED_KEY = "RichPresence"
        private const val ACTIVE_CHALLENGE_INDICATORS_ENABLED_KEY = "ActiveChallengeIndicators"
        private const val PROGRESS_INDICATORS_ENABLED_KEY = "ProgressIndicators"
        private const val LEADERBOARD_INDICATORS_ENABLED_KEY = "LeaderboardIndicators"

        private const val LEGACY_USERNAME_KEY = "ra_username"
        private const val LEGACY_TOKEN_KEY = "ra_token"
        private const val LEGACY_HARDCORE_ENABLED_KEY = "ra_hardcore_enabled"
        private const val LEGACY_RICH_PRESENCE_ENABLED_KEY = "ra_rich_presence"
        private const val LEGACY_ACTIVE_CHALLENGE_INDICATORS_ENABLED_KEY = "ra_active_challenge_indicators"
        private const val LEGACY_PROGRESS_INDICATORS_ENABLED_KEY = "ra_progress_indicators"
        private const val LEGACY_LEADERBOARD_INDICATORS_ENABLED_KEY = "ra_leaderboard_indicators"
    }

    fun getIniFile(): File {
        return File(context.getExternalFilesDir(null) ?: context.filesDir, FILE_NAME)
    }

    override fun getHostUrl(): String? {
        return readConfig().hostUrl.ifBlank { null }
    }

    fun getUsername(): String? {
        return readConfig().username
    }

    fun getToken(): String? {
        return readConfig().token
    }

    fun storeUserAuth(username: String, token: String) {
        updateConfig {
            it.copy(
                username = username.trim(),
                token = token,
            )
        }
    }

    fun clearUserAuth() {
        updateConfig {
            it.copy(
                username = null,
                token = null,
            )
        }
    }

    fun clearUserToken() {
        updateConfig {
            it.copy(token = null)
        }
    }

    fun isHardcoreEnabled(): Boolean {
        return readConfig().hardcoreEnabled
    }

    fun setHardcoreEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(hardcoreEnabled = enabled)
        }
    }

    fun isRichPresenceEnabled(): Boolean {
        return readConfig().richPresenceEnabled
    }

    fun setRichPresenceEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(richPresenceEnabled = enabled)
        }
    }

    fun areActiveChallengeIndicatorsEnabled(): Boolean {
        return readConfig().activeChallengeIndicatorsEnabled
    }

    fun setActiveChallengeIndicatorsEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(activeChallengeIndicatorsEnabled = enabled)
        }
    }

    fun areProgressIndicatorsEnabled(): Boolean {
        return readConfig().progressIndicatorsEnabled
    }

    fun setProgressIndicatorsEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(progressIndicatorsEnabled = enabled)
        }
    }

    fun areLeaderboardIndicatorsEnabled(): Boolean {
        return readConfig().leaderboardIndicatorsEnabled
    }

    fun setLeaderboardIndicatorsEnabled(enabled: Boolean) {
        updateConfig {
            it.copy(leaderboardIndicatorsEnabled = enabled)
        }
    }

    @Synchronized
    private fun readConfig(): Config {
        ensureFileExists()
        return parseConfig(getIniFile())
    }

    @Synchronized
    private fun updateConfig(transform: (Config) -> Config) {
        val updatedConfig = transform(readConfig())
        writeConfig(getIniFile(), updatedConfig)
    }

    private fun ensureFileExists() {
        val iniFile = getIniFile()
        if (iniFile.exists()) {
            return
        }

        writeConfig(iniFile, buildInitialConfig())
    }

    private fun buildInitialConfig(): Config {
        return Config(
            hostUrl = "",
            username = sharedPreferences.getString(LEGACY_USERNAME_KEY, null)?.trim()?.ifEmpty { null },
            token = sharedPreferences.getString(LEGACY_TOKEN_KEY, null),
            hardcoreEnabled = sharedPreferences.getBoolean(LEGACY_HARDCORE_ENABLED_KEY, false),
            richPresenceEnabled = sharedPreferences.getBoolean(LEGACY_RICH_PRESENCE_ENABLED_KEY, true),
            activeChallengeIndicatorsEnabled = sharedPreferences.getBoolean(LEGACY_ACTIVE_CHALLENGE_INDICATORS_ENABLED_KEY, true),
            progressIndicatorsEnabled = sharedPreferences.getBoolean(LEGACY_PROGRESS_INDICATORS_ENABLED_KEY, true),
            leaderboardIndicatorsEnabled = sharedPreferences.getBoolean(LEGACY_LEADERBOARD_INDICATORS_ENABLED_KEY, true),
        )
    }

    private fun parseConfig(file: File): Config {
        var hostUrl = ""
        var username: String? = null
        var token: String? = null
        var hardcoreEnabled = false
        var richPresenceEnabled = true
        var activeChallengeIndicatorsEnabled = true
        var progressIndicatorsEnabled = true
        var leaderboardIndicatorsEnabled = true
        var inRetroAchievementsSection = false

        file.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                return@forEachLine
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                inRetroAchievementsSection = line == SECTION_NAME
                return@forEachLine
            }

            if (!inRetroAchievementsSection) {
                return@forEachLine
            }

            val separatorIndex = line.indexOf('=')
            if (separatorIndex < 0) {
                return@forEachLine
            }

            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1)

            when (key) {
                HOST_URL_KEY -> hostUrl = value.trim()
                USERNAME_KEY -> username = value.trim().ifEmpty { null }
                TOKEN_KEY -> token = value.ifEmpty { null }
                HARDCORE_ENABLED_KEY -> hardcoreEnabled = value.toBooleanStrictOrNull() ?: false
                RICH_PRESENCE_ENABLED_KEY -> richPresenceEnabled = value.toBooleanStrictOrNull() ?: true
                ACTIVE_CHALLENGE_INDICATORS_ENABLED_KEY -> activeChallengeIndicatorsEnabled = value.toBooleanStrictOrNull() ?: true
                PROGRESS_INDICATORS_ENABLED_KEY -> progressIndicatorsEnabled = value.toBooleanStrictOrNull() ?: true
                LEADERBOARD_INDICATORS_ENABLED_KEY -> leaderboardIndicatorsEnabled = value.toBooleanStrictOrNull() ?: true
            }
        }

        return Config(
            hostUrl = hostUrl,
            username = username,
            token = token,
            hardcoreEnabled = hardcoreEnabled,
            richPresenceEnabled = richPresenceEnabled,
            activeChallengeIndicatorsEnabled = activeChallengeIndicatorsEnabled,
            progressIndicatorsEnabled = progressIndicatorsEnabled,
            leaderboardIndicatorsEnabled = leaderboardIndicatorsEnabled,
        )
    }

    private fun writeConfig(file: File, config: Config) {
        file.parentFile?.mkdirs()

        file.writeText(
            buildString {
                appendLine(SECTION_NAME)
                appendLine("$HOST_URL_KEY=${config.hostUrl}")
                appendLine("$USERNAME_KEY=${config.username.orEmpty()}")
                appendLine("$TOKEN_KEY=${config.token.orEmpty()}")
                appendLine("$HARDCORE_ENABLED_KEY=${config.hardcoreEnabled}")
                appendLine("$RICH_PRESENCE_ENABLED_KEY=${config.richPresenceEnabled}")
                appendLine("$ACTIVE_CHALLENGE_INDICATORS_ENABLED_KEY=${config.activeChallengeIndicatorsEnabled}")
                appendLine("$PROGRESS_INDICATORS_ENABLED_KEY=${config.progressIndicatorsEnabled}")
                appendLine("$LEADERBOARD_INDICATORS_ENABLED_KEY=${config.leaderboardIndicatorsEnabled}")
            }
        )
    }
}
