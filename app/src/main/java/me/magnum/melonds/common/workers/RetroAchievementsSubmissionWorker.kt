package me.magnum.melonds.common.workers

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.R
import me.magnum.melonds.domain.repositories.RetroAchievementsRepository

@HiltWorker
class RetroAchievementsSubmissionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val retroAchievementsRepository: RetroAchievementsRepository,
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val NOTIFICATION_ID_ACHIEVEMENT_SUBMISSION = 200
    }

    override suspend fun doWork(): Result {
        val submissionResult = withContext(Dispatchers.IO) {
            retroAchievementsRepository.submitPendingAchievements()
        }
        if (submissionResult.isSuccess) {
            return Result.success()
        }

        return Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MelonDSApplication.NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle(applicationContext.getString(R.string.submitting_achievements))
            .setColor(ContextCompat.getColor(applicationContext, R.color.melonMain))
            .setSmallIcon(R.drawable.ic_melon_small)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID_ACHIEVEMENT_SUBMISSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_ACHIEVEMENT_SUBMISSION, notification)
        }
    }
}