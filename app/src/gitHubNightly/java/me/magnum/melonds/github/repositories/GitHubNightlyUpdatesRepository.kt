package me.magnum.melonds.github.repositories

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import me.magnum.melonds.common.suspendMapCatching
import me.magnum.melonds.common.suspendRunCatching
import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.repositories.UpdatesRepository
import me.magnum.melonds.github.APK_CONTENT_TYPE
import me.magnum.melonds.github.GitHubApi
import me.magnum.melonds.github.PREF_KEY_GITHUB_CHECK_FOR_UPDATES
import me.magnum.melonds.github.dtos.ReleaseDto
import java.time.Duration
import java.time.Instant

class GitHubNightlyUpdatesRepository(private val api: GitHubApi, private val preferences: SharedPreferences) : UpdatesRepository {
    companion object {
        private const val KEY_NEXT_CHECK_DATE = "github_updates_nightly_next_check_date"
        private const val KEY_LAST_RELEASE_DATE = "github_updates_nightly_last_release_date"
    }

    override suspend fun checkNewUpdate(): Result<AppUpdate?> {
        if (!shouldCheckUpdates()) {
            return Result.success(null)
        }

        return suspendRunCatching {
            api.getLatestNightlyRelease()
        }.suspendMapCatching { release ->
            if (shouldUpdate(release)) {
                val apkBinary = release.assets.firstOrNull { it.contentType == APK_CONTENT_TYPE }
                if (apkBinary != null) {
                    AppUpdate(
                        AppUpdate.Type.NIGHTLY,
                        apkBinary.id,
                        apkBinary.url.toUri(),
                        Version.fromString(release.tagName),
                        release.body,
                        apkBinary.size,
                        Instant.parse(release.createdAt),
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    override fun skipUpdate(update: AppUpdate) {
        scheduleNextUpdate()
    }

    override fun notifyUpdateDownloaded(update: AppUpdate) {
        // This doesn't mean that the user has actually installed the update, but we have no way to determine that. As such, just assume that the update will be installed and
        // store the date of the update
        preferences.edit {
            putLong(KEY_LAST_RELEASE_DATE, update.updateDate.toEpochMilli())
        }
    }

    private fun shouldCheckUpdates(): Boolean {
        val updateCheckEnabled = preferences.getBoolean(PREF_KEY_GITHUB_CHECK_FOR_UPDATES, true)
        if (!updateCheckEnabled) {
            return false
        }

        val nextUpdateCheckTime = preferences.getLong(KEY_NEXT_CHECK_DATE, -1)
        if (nextUpdateCheckTime == (-1).toLong()) {
            return true
        }

        val now = Instant.now()
        return now.toEpochMilli() > nextUpdateCheckTime
    }

    private fun scheduleNextUpdate() {
        val now = Instant.now()
        val nextUpdateDate = now + Duration.ofDays(1)

        preferences.edit {
            putLong(KEY_NEXT_CHECK_DATE, nextUpdateDate.toEpochMilli())
        }
    }

    private fun shouldUpdate(releaseDto: ReleaseDto): Boolean {
        val lastReleaseDate = preferences.getLong(KEY_LAST_RELEASE_DATE, -1)
        if (lastReleaseDate == -1L) {
            // If there is no last release date, then it's the first time the user is running the app and checking for updates. Ignore this release since we can't check if
            // it's actually different from the one the user has installed, and save the release date in the preferences so that we can have a future reference

            val releaseDate = Instant.parse(releaseDto.createdAt)
            preferences.edit {
                putLong(KEY_LAST_RELEASE_DATE, releaseDate.toEpochMilli())
            }
            scheduleNextUpdate()
            return false
        }

        val thisReleaseDate = Instant.parse(releaseDto.createdAt)

        return thisReleaseDate.toEpochMilli() > lastReleaseDate
    }
}