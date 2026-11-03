package me.magnum.melonds.github.repositories

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.magnum.melonds.github.GitHubApi
import me.magnum.melonds.github.PREF_KEY_GITHUB_CHECK_FOR_UPDATES
import me.magnum.melonds.github.dtos.ReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GitHubProdUpdatesRepositoryTest {
    @Test
    fun `disabled update checks never call GitHub`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("prod-update-test", Context.MODE_PRIVATE)
        preferences.edit().clear().putBoolean(PREF_KEY_GITHUB_CHECK_FOR_UPDATES, false).commit()
        val api = RecordingApi()

        val result = GitHubProdUpdatesRepository(context, api, preferences).checkNewUpdate()

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
        assertEquals(0, api.latestReleaseCalls)
    }

    private class RecordingApi : GitHubApi {
        var latestReleaseCalls = 0

        override suspend fun getLatestRelease(): ReleaseDto {
            latestReleaseCalls++
            error("network must remain unreachable while update checks are disabled")
        }

        override suspend fun getLatestNightlyRelease(): ReleaseDto = error("not used")
    }
}
