package me.magnum.melonds.ui.emulator.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

class GlContext(sharedGlContext: GlContext? = null) {

    private var display: EGLDisplay
    private var config: EGLConfig
    private var context: EGLContext

    val glContext get() = context
    val contextNativeHandle get() = context.nativeHandle

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw GlContextException("No display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw GlContextException("Unable to initialize EGL")
        }

        config = createGlConfig()

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(display, config, sharedGlContext?.context ?: EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            throw GlContextException("Failed to create context: ${EGL14.eglGetError()}")
        }
    }

    fun use(surface: EGLSurface) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw GlContextException("Failed to make current: ${EGL14.eglGetError()}")
        }

        // Allows immediate buffer swapping without waiting for VSync
        EGL14.eglSwapInterval(display, 0)
    }

    fun swapBuffers(surface: EGLSurface) {
        EGL14.eglSwapBuffers(display, surface)
    }

    fun release() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
    }

    fun destroy() {
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()

        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw GlContextException("Failed to create window surface: ${EGL14.eglGetError()}")
        }

        return eglSurface
    }

    fun destroyWindowSurface(eglSurface: EGLSurface) {
        EGL14.eglDestroySurface(display, eglSurface)
    }

    private fun createGlConfig(): EGLConfig {
        val attributeList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 24,
            EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE,
        )

        val eglConfig = arrayOfNulls<EGLConfig?>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributeList, 0, eglConfig, 0, 1, numConfigs, 0)) {
            throw GlContextException("Unable to choose config")
        }

        return eglConfig[0]!!
    }

    class GlContextException(message: String) : Exception(message)
}