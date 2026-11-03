package me.magnum.melonds.common.retroachievements

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.magnum.rcheevosapi.model.RAUserAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AndroidRAUserAuthStoreTest {
    @Test
    fun `legacy credentials migrate losslessly into isolated preferences`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = PreferenceManager.getDefaultSharedPreferences(context)
        val isolated = context.getSharedPreferences(AndroidRAUserAuthStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        legacy.edit().putString("ra_username", "player").putString("ra_token", "secret").commit()

        val store = AndroidRAUserAuthStore(isolated, legacy)

        assertEquals(RAUserAuth.Authenticated("player", "secret"), store.getUserAuth())
        assertEquals("player", isolated.getString("ra_username", null))
        assertEquals("secret", isolated.getString("ra_token", null))
        assertFalse(legacy.contains("ra_username"))
        assertFalse(legacy.contains("ra_token"))
    }

    @Test
    fun `conflicting legacy credentials are scrubbed without replacing isolated account`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = PreferenceManager.getDefaultSharedPreferences(context)
        val isolated = context.getSharedPreferences(AndroidRAUserAuthStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        legacy.edit().clear().putString("ra_username", "old").putString("ra_token", "old-token").commit()
        isolated.edit().clear().putString("ra_username", "current").putString("ra_token", "current-token").commit()

        val store = AndroidRAUserAuthStore(isolated, legacy)

        assertEquals(RAUserAuth.Authenticated("current", "current-token"), store.getUserAuth())
        assertFalse(legacy.contains("ra_username"))
        assertFalse(legacy.contains("ra_token"))
    }
}
