package me.magnum.melonds.ui.emulator.render

import android.hardware.HardwareBuffer
import android.opengl.EGL14
import android.os.Build
import androidx.annotation.RequiresApi

object FrontBufferCapability {

    private const val EXTENSION_NATIVE_CLIENT_BUFFER = "EGL_ANDROID_get_native_client_buffer"

    fun isFrontBufferSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        if (!hasNativeClientBufferExtension()) {
            return false
        }

        if (!isHardwareBufferSupported()) {
            return false
        }

        return true
    }

    private fun hasNativeClientBufferExtension(): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            return false
        }

        val extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS) ?: return false
        return extensions.contains(EXTENSION_NATIVE_CLIENT_BUFFER)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun isHardwareBufferSupported(): Boolean {
        val usageFlags = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
                HardwareBuffer.USAGE_COMPOSER_OVERLAY or
                HardwareBuffer.USAGE_FRONT_BUFFER

        return try {
            HardwareBuffer.isSupported(1, 1, HardwareBuffer.RGBA_8888, 1, usageFlags)
        } catch (_: Exception) {
            false
        }
    }
}
