package me.magnum.melonds.common.workers

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.entities.CheatDatabaseEntity
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.impl.RoomCheatsRepository
import me.magnum.melonds.ui.settings.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Run alone in a fresh instrumentation process. No WorkManager testing adapters are used. */
@RunWith(AndroidJUnit4::class)
class CheatImportWorkManagerTest {
    private val application = ApplicationProvider.getApplicationContext<MelonDSApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixtures = mutableListOf<Uri>()
    private val authority by lazy {
        @Suppress("DEPRECATION")
        val info = instrumentation.context.packageManager.getPackageInfo(
            instrumentation.context.packageName, PackageManager.GET_PROVIDERS)
        requireNotNull(info.providers).single { it.name == CheatImportTestProvider::class.java.name }.authority
    }
    private val controlUri get() = Uri.parse("content://$authority")
    private val xml = """<codelist><name>Replacement</name><game><name>New game</name><gameid>NEW1 87654321</gameid><folder><name>New folder</name><cheat><name>New code</name><codes>02000000 00000003</codes></cheat></folder></game></codelist>""".toByteArray()

    @Test(timeout = 60000)
    fun cancelUniqueImportRemovesNotificationAndAllowsNextKeepImport() = runBlocking {
        org.junit.Assume.assumeTrue("Dedicated fresh-process run requires -e realWorkManager true",
            InstrumentationRegistry.getArguments().getString("realWorkManager") == "true")
        assertFalse("Run this class alone before any WorkManager.getInstance call", WorkManager.isInitialized())
        val database = Room.inMemoryDatabaseBuilder(application, MelonDatabase::class.java).build()
        val repository = RoomCheatsRepository(application, database)
        val uniqueName = "cheat-import-regression-${UUID.randomUUID()}"
        var manager: WorkManager? = null
        var activity: ActivityScenario<SettingsActivity>? = null
        try {
            seed(database, repository)
            val originalRows = snapshot(database)
            val originalConfiguration = application.workManagerConfiguration
            val fixtureFactory = object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String,
                    workerParameters: WorkerParameters): ListenableWorker? {
                    return if (workerClassName == CheatImportWorker::class.java.name &&
                        uniqueName in workerParameters.tags) {
                        CheatImportWorker(appContext, workerParameters, repository)
                    } else {
                        originalConfiguration.workerFactory.createWorker(appContext, workerClassName, workerParameters)
                    }
                }
            }
            WorkManager.initialize(application, Configuration.Builder(originalConfiguration)
                .setWorkerFactory(fixtureFactory).build())
            val workManager = WorkManager.getInstance(application)
            manager = workManager
            assertTrue("Existing non-test cheat work must finish before this dedicated test run",
                workManager.getWorkInfosByTag(CheatImportWorker::class.java.name).awaitBounded().all { it.state.isFinished })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Only the dedicated target fixture package is granted; no global notification settings change.
                instrumentation.uiAutomation.grantRuntimePermission(application.packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
            val notifications = requireNotNull(application.getSystemService(NotificationManager::class.java))
            assertTrue("Notifications must be enabled for the target fixture package", notifications.areNotificationsEnabled())
            val channel = requireNotNull(notifications.getNotificationChannel(MelonDSApplication.NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS))
            assertTrue("The import notification channel must not be blocked", channel.importance > NotificationManager.IMPORTANCE_NONE)
            assertFalse("An existing notification 100 must not be confused with the test import", hasImportNotification(notifications))

            // Keep the target UID foreground while WorkManager starts its actual foreground service.
            // Main settings reads preferences and does not enumerate ROMs or start an import.
            activity = ActivityScenario.launch(SettingsActivity::class.java)
            val blocked = fixture("cancel", 30000)
            val first = request(blocked, uniqueName)
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, first).result.awaitBounded()
            awaitState(workManager, first.id, WorkInfo.State.RUNNING)
            assertTrue("Production worker reached the real remote provider",
                withContext(Dispatchers.IO) { control("await-open", blocked).getBoolean("ready") })
            awaitCondition("The actual foreground notification must be posted before cancellation", 15000) {
                hasImportNotification(notifications, requireForeground = true)
            }

            val ignoredUri = fixture("valid")
            val ignored = request(ignoredUri, uniqueName)
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, ignored).result.awaitBounded()
            assertNull("KEEP must not enqueue a second request while the first is active",
                workManager.getWorkInfoById(ignored.id).awaitBounded())
            assertEquals(0, control("status", ignoredUri).getInt("opens"))
            assertEquals(originalRows, snapshot(database))

            workManager.cancelUniqueWork(uniqueName).result.awaitBounded()
            assertTrue("Real WorkManager cancellation must reach the remote provider",
                withContext(Dispatchers.IO) { control("await-cancel", blocked).getBoolean("ready") })
            awaitCondition("Cancellation must stop the provider open before cleanup", 5000) {
                control("status", blocked).getInt("active") == 0
            }
            awaitState(workManager, first.id, WorkInfo.State.CANCELLED)
            awaitCondition("WorkManager must remove the notification it posted", 5000) {
                !hasImportNotification(notifications)
            }
            assertEquals(originalRows, snapshot(database))

            val nextUri = fixture("valid")
            val next = request(nextUri, uniqueName)
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, next).result.awaitBounded()
            awaitState(workManager, next.id, WorkInfo.State.SUCCEEDED)
            assertTrue("The second KEEP request must reach the real provider", control("status", nextUri).getInt("opens") > 0)
            assertEquals(listOf("02000000 00000002", "02000000 00000003"), codes(database))
            awaitCondition("Successful completion must also remove the import notification", 5000) {
                !hasImportNotification(notifications)
            }
        } finally {
            try {
                withContext(NonCancellable) {
                    // Only this test's unique work is cancelled. Never cancelAllWork or pruneWork.
                    manager?.cancelUniqueWork(uniqueName)?.result?.awaitBounded()
                    fixtures.forEach { control("release", it) }
                    awaitCondition("Fixture opens must finish before teardown", 5000) {
                        fixtures.all { control("status", it).getInt("active") == 0 }
                    }
                    fixtures.forEach { control("remove", it) }
                }
            } finally {
                activity?.close()
                database.close()
            }
        }
    }

    private fun request(uri: Uri, tag: String) = OneTimeWorkRequestBuilder<CheatImportWorker>()
        .setInputData(workDataOf(CheatImportWorker.KEY_URI to uri.toString())).addTag(tag).build()

    private fun fixture(mode: String, openTimeout: Long = 10000): Uri {
        val response = requireNotNull(application.contentResolver.call(controlUri, "register", mode, Bundle().apply {
            putByteArray("bytes", xml)
            putLong("open_timeout_ms", openTimeout)
        }))
        assertNotEquals(Process.myPid(), response.getInt("pid"))
        return controlUri.buildUpon().appendPath("document").appendPath(requireNotNull(response.getString("id"))).build().also {
            fixtures += it
        }
    }

    private fun control(method: String, uri: Uri): Bundle = requireNotNull(
        application.contentResolver.call(controlUri, method, requireNotNull(uri.lastPathSegment), null))

    private fun hasImportNotification(manager: NotificationManager, requireForeground: Boolean = false): Boolean =
        manager.activeNotifications.any {
            it.id == 100 && it.notification.channelId == MelonDSApplication.NOTIFICATION_CHANNEL_ID_BACKGROUND_TASKS &&
                (!requireForeground || it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        }

    private suspend fun awaitState(manager: WorkManager, id: UUID, state: WorkInfo.State) {
        val info = withTimeout(5000) {
            manager.getWorkInfoByIdFlow(id).filterNotNull().first { it.state == state || it.state.isFinished }
        }
        assertEquals(state, info.state)
    }

    private suspend fun awaitCondition(message: String, timeout: Long, condition: () -> Boolean) {
        try {
            withTimeout(timeout) { while (!condition()) delay(50) }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError(message, error)
        }
    }

    private suspend fun <T> ListenableFuture<T>.awaitBounded(): T = withContext(Dispatchers.IO) {
        get(5, TimeUnit.SECONDS)
    }

    private suspend fun seed(database: MelonDatabase, repository: RoomCheatsRepository) {
        database.cheatDatabaseDao().insertCheatDatabase(CheatDatabaseEntity(0, "__custom_cheat_database"))
        val old = repository.addCheatDatabase("Original database")
        repository.addGameCheats(Game(null, "Original game", "OLD1", "12345678", listOf(
            CheatFolder(null, "Original folder", listOf(Cheat(null, old.id!!, "Old code", null, "02000000 00000001", false))),
            CheatFolder(null, "My folder", listOf(Cheat(null, 0, "My code", "Keep me", "02000000 00000002", true))),
        )))
        database.openHelper.writableDatabase.execSQL("UPDATE cheat SET enabled = 1 WHERE cheat_database_id = 0")
    }

    private fun snapshot(database: MelonDatabase): List<String> = listOf("cheat_database", "game", "cheat_folder", "cheat").flatMap { table ->
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(table + ":" + (0 until cursor.columnCount).joinToString("|") { cursor.getString(it) ?: "<null>" })
            }
        }
    }

    private fun codes(database: MelonDatabase): List<String> = database.openHelper.readableDatabase.query("SELECT code FROM cheat ORDER BY code").use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
}
