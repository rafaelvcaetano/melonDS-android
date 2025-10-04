package me.magnum.melonds.impl.system

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AppForegroundStateTracker : AppForegroundStateObserver {

    override val onAppMovedToBackgroundEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var foregroundActivities = 0

    private val activityLifecycleCallback = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        }

        override fun onActivityStarted(activity: Activity) {
            foregroundActivities++
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
            foregroundActivities--
            if (foregroundActivities == 0) {
                onAppMovedToBackgroundEvent.tryEmit(Unit)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }
    }

    fun startTracking(applicationContext: Context) {
        val application = applicationContext as Application
        application.registerActivityLifecycleCallbacks(activityLifecycleCallback)
    }
}

interface AppForegroundStateObserver {
    val onAppMovedToBackgroundEvent: Flow<Unit>
}