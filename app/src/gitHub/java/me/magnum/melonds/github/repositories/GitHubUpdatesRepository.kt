package me.magnum.melonds.github.repositories

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.core.net.toUri
import io.reactivex.Maybe
import io.reactivex.Single
import me.magnum.melonds.domain.model.AppUpdate
import me.magnum.melonds.domain.model.Version
import me.magnum.melonds.domain.repositories.UpdatesRepository
import me.magnum.melonds.github.GitHubApi
import me.magnum.melonds.github.dtos.ReleaseDto
import me.magnum.melonds.utils.PackageManagerCompat
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import java.util.*
import java.util.concurrent.TimeUnit

class GitHubUpdatesRepository(private val context: Context, private val api: GitHubApi, private val preferences: SharedPreferences) : UpdatesRepository {
    companion object {
        private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        private const val KEY_SKIP_VERSION = "github_updates_skip_version"
        private const val KEY_LAST_UPDATE_CHECK = "github_updates_last_check"

        private const val UPDATE_CHECK_DELAY_HOURS = 22
    }

    override fun checkNewUpdate(): Maybe<AppUpdate> {
        return shouldCheckUpdates()
            .flatMapMaybe { checkUpdates ->
                if (checkUpdates) {
                    api.getLatestRelease().flatMapMaybe { release ->
                        updateLastUpdateCheckTime()
                        if (isReleaseNewUpdate(release) && !shouldSkipUpdate(release)) {
                            val apkBinary = release.assets.firstOrNull { it.contentType == APK_CONTENT_TYPE }
                            if (apkBinary != null) {
                                val update = AppUpdate(
                                    apkBinary.id,
                                    apkBinary.url.toUri(),
                                    Version.fromString(release.tagName),
                                    release.body,
                                    apkBinary.size
                                )
                                Maybe.just(update)
                            } else {
                                Maybe.empty()
                            }
                        } else {
                            Maybe.empty()
                        }
                    }
                } else {
                    Maybe.empty()
                }
            }
    }

    override fun skipUpdate(update: AppUpdate) {
        preferences.edit {
            putString(KEY_SKIP_VERSION, update.newVersion.toString())
        }
    }

    private fun shouldCheckUpdates(): Single<Boolean> {
        return Single.create { emitter ->
            val lastCheckUpdateTimestamp = preferences.getLong(KEY_LAST_UPDATE_CHECK, -1)
            if (lastCheckUpdateTimestamp == (-1).toLong()) {
                emitter.onSuccess(true)
                return@create
            }

            val currentDate = Calendar.getInstance().time

            val difference = currentDate.time - lastCheckUpdateTimestamp
            val hoursDifference = TimeUnit.HOURS.convert(difference, TimeUnit.MILLISECONDS)

            val shouldCheckUpdates = hoursDifference >= UPDATE_CHECK_DELAY_HOURS
            emitter.onSuccess(shouldCheckUpdates)
        }
    }

    private fun updateLastUpdateCheckTime() {
        val currentDate = Calendar.getInstance().time
        preferences.edit {
            putLong(KEY_LAST_UPDATE_CHECK, currentDate.time)
        }
    }

    private fun shouldSkipUpdate(releaseDto: ReleaseDto): Boolean {
        val skipVersionString = preferences.getString(KEY_SKIP_VERSION, null) ?: return false

        val releaseVersion = Version.fromString(releaseDto.tagName)
        val skipVersion = Version.fromString(skipVersionString)

        return skipVersion >= releaseVersion
    }

    private fun isReleaseNewUpdate(releaseDto: ReleaseDto): Boolean {
        val packageInfo = PackageManagerCompat.getPackageInfo(context.packageManager, context.packageName, 0)

        val currentVersion = getCurrentAppVersion(packageInfo.versionName)
        val releaseVersion = Version.fromString(releaseDto.tagName)

        return releaseVersion > currentVersion
    }

    private fun getCurrentAppVersion(versionString: String): Version {
        val parts = versionString.split(' ')
        return if (parts.size == 1) {
            val intParts = parts[0].split('.').map { it.toInt() }.ensureMinimumSize(3, 0)
            Version(Version.ReleaseType.FINAL, intParts[0], intParts[1], intParts[2])
        } else {
            val versionType = enumValueOfIgnoreCase<Version.ReleaseType>(parts[0])
            val intParts = parts[1].split('.').map { it.toInt() }.ensureMinimumSize(3, 0)
            Version(versionType, intParts[0], intParts[1], intParts[2])
        }
    }

    private fun <T> List<T>.ensureMinimumSize(size: Int, filler: T): List<T> {
        return if (this.size >= size) {
            this
        } else {
            val remaining = size - this.size
            val newList = mutableListOf<T>()
            newList.addAll(this)
            for (i in 0..remaining) {
                newList.add(filler)
            }

            newList
        }
    }
}