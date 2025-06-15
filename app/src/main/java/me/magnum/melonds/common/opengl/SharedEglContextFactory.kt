package me.magnum.melonds.common.opengl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext as EGL10Context
import javax.microedition.khronos.egl.EGLDisplay as EGL10Display
import javax.microedition.khronos.egl.EGLConfig as EGL10Config

/**
 * [GLSurfaceView.EGLContextFactory] that creates a context sharing resources
 * with the provided [sharedContext].
 */
class SharedEglContextFactory(private val sharedContext: EGL10Context) : GLSurfaceView.EGLContextFactory {
    override fun createContext(egl: EGL10, display: EGL10Display, config: EGL10Config): EGL10Context {
        val attribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE)
        return egl.eglCreateContext(display, config, sharedContext, attribs)
    }

    override fun destroyContext(egl: EGL10, display: EGL10Display, context: EGL10Context) {
        egl.eglDestroyContext(display, context)
    }
}
