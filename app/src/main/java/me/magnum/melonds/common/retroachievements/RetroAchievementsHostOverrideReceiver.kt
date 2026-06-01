package me.magnum.melonds.common.retroachievements

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.magnum.melonds.common.appSharedPreferences

class RetroAchievementsHostOverrideReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_SET_HOST_OVERRIDE_SUFFIX = ".action.SET_RETROACHIEVEMENTS_HOST_OVERRIDE"
        private const val ACTION_CLEAR_HOST_OVERRIDE_SUFFIX = ".action.CLEAR_RETROACHIEVEMENTS_HOST_OVERRIDE"
        const val EXTRA_HOST = "host"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sharedPreferences = appSharedPreferences(context)
        val hostUrlProvider = SharedPreferencesRAHostUrlProvider(sharedPreferences)
        val packageName = context.packageName
        val setAction = packageName + ACTION_SET_HOST_OVERRIDE_SUFFIX
        val clearAction = packageName + ACTION_CLEAR_HOST_OVERRIDE_SUFFIX

        when (intent.action) {
            setAction -> {
                val host = intent.getStringExtra(EXTRA_HOST)
                if (!hostUrlProvider.setBaseUrl(host.orEmpty())) {
                    resultCode = Activity.RESULT_CANCELED
                }
            }

            clearAction -> hostUrlProvider.clearBaseUrl()
        }
    }
}
