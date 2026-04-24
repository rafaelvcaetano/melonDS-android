package me.magnum.melonds.ui.emulator

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.magnum.melonds.MelonDSApplication

// Simple service to keep the app in foreground after going to sleep
// Sound is really crunchy if the app is sent to the background due to Android's handling
class LidCloseService : Service() {
    companion object {
        private const val NOTIFICATION_ID_LID_CLOSE = 300
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, MelonDSApplication.NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS)
            .setContentTitle("DS Sleep")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode)
            .setSilent(true)
            .build()

        ServiceCompat.startForeground(this, NOTIFICATION_ID_LID_CLOSE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }
}
