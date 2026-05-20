package me.magnum.melonds.ui.emulator.render

import android.hardware.SyncFence
import android.opengl.EGL14
import android.opengl.EGL15
import android.opengl.EGLExt
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object SyncFenceFactory {

    private val EMPTY_ATTRIBUTES = longArrayOf(EGL14.EGL_NONE.toLong())

    /**
     * Creates a native [SyncFence] from an EGL native fence sync. The returned fence will signal when the GPU has completed all previously submitted GL commands, ensuring the
     * buffer content is fully written.
     *
     * @return A valid [SyncFence], or null if creation failed.
     */
    fun createNativeSyncFence(): SyncFence? {
        val display = EGL15.eglGetPlatformDisplay(
            EGL15.EGL_PLATFORM_ANDROID_KHR,
            EGL14.EGL_DEFAULT_DISPLAY.toLong(),
            EMPTY_ATTRIBUTES,
            0
        )
        if (display == EGL15.EGL_NO_DISPLAY) {
            return null
        }

        val eglSync = EGL15.eglCreateSync(
            display,
            EGLExt.EGL_SYNC_NATIVE_FENCE_ANDROID,
            EMPTY_ATTRIBUTES,
            0
        )
        if (eglSync == EGL15.EGL_NO_SYNC) {
            return null
        }

        // Flush to ensure the sync is inserted into the GPU command stream
        GLES20.glFlush()

        val syncFence = EGLExt.eglDupNativeFenceFDANDROID(display, eglSync)
        EGL15.eglDestroySync(display, eglSync)

        return syncFence
    }
}
