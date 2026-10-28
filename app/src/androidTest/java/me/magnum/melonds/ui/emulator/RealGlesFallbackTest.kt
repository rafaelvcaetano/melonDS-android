package me.magnum.melonds.ui.emulator

// Opt-in whole-app regression; run only on a dedicated disposable low-GLES AVD.
// Reuses the existing test APK's BiosGuidedSetupTestProvider in normal mode.
// Asset required: androidTest/assets/fallback-homebrew.nds from tools/android-tests/renderer-fixture/generate.py.
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.magnum.melonds.MelonDSApplication
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.common.BiosGuidedSetupTestProvider
import me.magnum.melonds.common.runtime.ScreenshotFrameBufferProvider
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.model.rom.config.RomConfig
import me.magnum.melonds.ui.emulator.model.EmulatorState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealGlesFallbackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MelonDSApplication>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val authority by lazy {
        @Suppress("DEPRECATION")
        val info = instrumentation.context.packageManager.getPackageInfo(
            instrumentation.context.packageName, PackageManager.GET_PROVIDERS)
        requireNotNull(info.providers).single {
            it.name == BiosGuidedSetupTestProvider::class.java.name
        }.authority
    }
    private val controlUri get() = Uri.parse("content://$authority")

    @Test(timeout = 60000)
    fun realShaderFailureKeepsSoftwareFramesAndRequestedPreferences() {
        assumeTrue("Opt-in only on the dedicated disposable AVD",
            args.getString("realGlesFallback") == "true" && args.getString("dedicatedFixtureAvd") == "true")
        val request = args.getString("rendererRequest") ?: "compute"
        require(request in setOf("software", "compute", "opengl"))
        val requested = VideoRenderer.valueOf(request.uppercase())
        val token = "gles-${System.nanoTime()}"
        val output = File(app.filesDir, "real-gles-$token").apply { check(mkdirs()) }
        val egl = inspectEglClient3()
        File(output, "egl-probe.txt").writeText(egl)
        assumeTrue("Needs an actual GLES 3.0/3.1 context; GLES 3.2 does not cover this failure gate: $egl",
            Regex("OpenGL ES 3\\.[01](?:[^0-9]|$)").containsMatchIn(egl))
        val bytes = instrumentation.context.assets.open("fallback-homebrew.nds").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("e298b528668165047d8bb4d7dfa0df038ddb9a6d91289b6f7a84b55e8b2bd5f4", hash)
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val ownedKeys = setOf("use_custom_bios", "console_type", "enable_jit", "show_bios",
            "bios_dir", "dsi_bios_dir", "video_renderer", "video_internal_resolution",
            "video_filtering", "front_rendering", "enable_rewind", "sound_enabled", "mic_source", "use_rom_dir")
        val original = snapshot(prefs).filterKeys { it in ownedKeys }
        var fixtureId: String? = null
        var scenario: ActivityScenario<EmulatorActivity>? = null
        var viewModel: EmulatorViewModel? = null
        var granted = false
        val screenshots = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "real-gles-screenshot").apply { isDaemon = true }
        }
        try {
            grant(BiosGuidedSetupTestProvider.GrantReceiver.GRANT)
            granted = true
            val registered = requireNotNull(app.contentResolver.call(controlUri, "fixture-register", "normal",
                Bundle().apply { putString("name", "fallback-homebrew.nds"); putByteArray("bytes", bytes) }))
            fixtureId = requireNotNull(registered.getString("id"))
            val tree = requireNotNull(DocumentFile.fromTreeUri(app,
                DocumentsContract.buildTreeDocumentUri(authority, fixtureId)))
            val romDocument = requireNotNull(tree.findFile("fallback-homebrew.nds"))
            check(prefs.edit().putBoolean("use_custom_bios", false).putString("console_type", "ds")
                .putBoolean("enable_jit", false).putBoolean("show_bios", false)
                .remove("bios_dir").remove("dsi_bios_dir")
                .putString("video_renderer", request).putString("video_internal_resolution", "3")
                .putString("video_filtering", "none").putBoolean("front_rendering", false)
                .putBoolean("enable_rewind", false).putBoolean("sound_enabled", false)
                .putString("mic_source", "none").putBoolean("use_rom_dir", true).commit())
            runBlocking {
                withTimeout(5000) {
                    while (app.settingsRepository.getEmulatorConfiguration().rendererConfiguration.renderer != requested) delay(20)
                }
                val config = app.settingsRepository.getEmulatorConfiguration()
                assertFalse(config.useCustomBios)
                assertFalse(config.useJit)
                assertEquals(ConsoleType.DS, config.consoleType)
            }
            val expectedPreferences = snapshot(prefs)
            File(output, "preferences-before.txt").writeText(expectedPreferences.toSortedMap().toString())
            Log.i("RealGlesProbe", "BEGIN $token request=$request fixture=$hash")
            val rom = Rom(name = "GLES fallback probe", developerName = "Generated fixture",
                fileName = "fallback-homebrew.nds", uri = romDocument.uri, parentTreeUri = tree.uri,
                config = RomConfig.default(), isDsiWareTitle = false, retroAchievementsHash = "")
            val launched = ActivityScenario.launch<EmulatorActivity>(EmulatorActivity.getRomEmulatorActivityIntent(app, rom))
            scenario = launched
            launched.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[EmulatorViewModel::class.java]
            }
            val vm = requireNotNull(viewModel)
            await(10000, "normal Activity/JNI launch reaches RunningRom") {
                val state = vm.emulatorState.value
                check(state !is EmulatorState.RomLoadError && state !is EmulatorState.RomNotFoundError) { state.toString() }
                state is EmulatorState.RunningRom
            }
            // Access the actual ActivityRetained buffer already passed to native setup.
            // Reflection adds no fake renderer, factory, frame, or production test API.
            val buffer = EmulatorViewModel::class.java.getDeclaredField("screenshotFrameBufferProvider")
                .apply { isAccessible = true }.get(vm) as ScreenshotFrameBufferProvider
            val phases = mutableSetOf<Int>()
            val deadline = SystemClock.elapsedRealtime() + 7000
            while (phases.size < 2 && SystemClock.elapsedRealtime() < deadline) {
                val image = screenshots.submit<Bitmap> {
                    check(MelonEmulator.takeScreenshot()) { "Native screenshot stopped" }
                    buffer.getScreenshot()
                }.get(3, TimeUnit.SECONDS)
                try {
                    val phase = scenePhase(image)
                    if (phase != 0 && phases.add(phase)) {
                        File(output, "native-phase-$phase.png").outputStream().use {
                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                } finally { image.recycle() }
                SystemClock.sleep(50)
            }
            assertEquals("Both original homebrew phases must execute after initialization", setOf(1, 2), phases)
            val shown = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                File(output, "activity.png").outputStream().use { shown.compress(Bitmap.CompressFormat.PNG, 100, it) }
                var red = 0
                var blue = 0
                for (y in 0 until shown.height step 2) for (x in 0 until shown.width step 2) {
                    if (isRed(shown.getPixel(x, y))) red++
                    if (isBlue(shown.getPixel(x, y))) blue++
                }
                assertTrue("Presented Activity surface must contain both scene colors", red > 256 && blue > 256)
            } finally { shown.recycle() }
            val after = snapshot(prefs)
            File(output, "preferences-after.txt").writeText(after.toSortedMap().toString())
            assertEquals("Fallback must not rewrite preferences (including requested renderer/scale)", expectedPreferences, after)
            val logs = logcat().substringAfter("BEGIN $token", "")
            File(output, "process-logcat.txt").writeText(logs)
            assertTrue("Current process marker must be present", logs.isNotBlank())
            if (request != "software") {
                assertTrue("Must observe the real compositor shader compiler rejection",
                    logs.contains("failed to compile") && logs.contains("CompositorShader"))
                assertTrue("Factory must really return null and activate the production fallback",
                    logs.contains("Failed to initialize renderer ${requested.renderer}; using software rendering"))
            } else {
                assertFalse("Software is a non-failing fixture/presentation control", logs.contains("Failed to initialize renderer"))
            }
            File(output, "PASS.txt").writeText("request=$request phases=$phases preferences unchanged; host must independently attest APK identity\n")
        } finally {
            // stopEmulation wakes the native screenshot condition. A Java Future
            // cancellation alone cannot interrupt a blocked JNI wait. Root must
            // also impose an outer instrumentation timeout on the dedicated AVD.
            runCatching { File(output, "process-logcat-finally.txt").writeText(logcat()) }
            try {
                scenario?.onActivity { viewModel?.exitEmulator(force = true) }
                scenario?.close()
            } finally {
                screenshots.shutdown()
                val terminated = screenshots.awaitTermination(3, TimeUnit.SECONDS)
                if (!terminated) {
                    File(output, "CLEANUP_FAILED.txt").writeText("Native screenshot worker did not exit; stop this instrumentation process before another run.\n")
                }
                try {
                    restoreOwned(prefs, ownedKeys, original)
                    fixtureId?.let { app.contentResolver.call(controlUri, "fixture-remove", it, null) }
                } finally {
                    if (granted) grant(BiosGuidedSetupTestProvider.GrantReceiver.REVOKE)
                }
                assertTrue("Native screenshot worker must exit after stopping emulation", terminated)
            }
        }
    }

    private fun snapshot(prefs: SharedPreferences): Map<String, Any?> = prefs.all.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }
    private fun restoreOwned(prefs: SharedPreferences, keys: Set<String>, original: Map<String, Any?>) {
        val edit = prefs.edit()
        for (key in keys) {
            edit.remove(key)
            when (val value = original[key]) {
                null -> Unit
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.map { it as String }.toSet())
                else -> error("Unexpected preference value")
            }
        }
        check(edit.commit())
    }
    private fun await(timeout: Long, message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(20)
        }
        fail(message)
    }
    private fun isRed(pixel: Int) = ((pixel ushr 16) and 255) > 200 && (pixel and 255) < 40 && ((pixel ushr 8) and 255) < 40
    private fun isBlue(pixel: Int) = (pixel and 255) > 200 && ((pixel ushr 16) and 255) < 40 && ((pixel ushr 8) and 255) < 40
    private fun scenePhase(image: Bitmap): Int {
        assertEquals(256, image.width)
        assertEquals(384, image.height)
        val top = image.getPixel(128, 96)
        val bottom = image.getPixel(128, 288)
        return when {
            isRed(top) && isBlue(bottom) -> 1
            isBlue(top) && isRed(bottom) -> 2
            else -> 0
        }
    }
    private fun logcat(): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand("logcat -d -t 2000 --pid ${Process.myPid()} -v threadtime")
    ).bufferedReader().use { it.readText() }
    private fun grant(action: String) {
        val done = CountDownLatch(1)
        var result = Activity.RESULT_CANCELED
        val intent = Intent(action).apply {
            component = ComponentName(instrumentation.context.packageName,
                BiosGuidedSetupTestProvider.GrantReceiver::class.java.name)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        app.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { result = resultCode; done.countDown() }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null)
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(Activity.RESULT_OK, result)
    }

    // Capability probe only: it is not used to substitute for the real app failure.
    private fun inspectEglClient3(): String {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            return listOf(GLES20.glGetString(GLES20.GL_VERSION), GLES20.glGetString(GLES20.GL_RENDERER),
                GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION)).joinToString("\n")
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }
}
