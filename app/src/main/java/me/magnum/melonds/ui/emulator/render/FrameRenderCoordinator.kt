package me.magnum.melonds.ui.emulator.render

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import me.magnum.melonds.MelonDSAndroidInterface
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.EmulatorSurfaceView

class FrameRenderCoordinator {

    private val glContext: GlContext
    private val frameRenderThread = FrameRenderThread()
    private val presentFrameWrapper = PresentFrameWrapper()
    private val surfacesLock = Any()
    private val managedSurfaces = mutableListOf<EmulatorSurfaceView>()
    private val surfacesPendingRemoval = mutableListOf<EmulatorSurfaceView>()

    init {
        glContext = GlContext(MelonDSAndroidInterface.getEmulatorGlContext())
        frameRenderThread.start()
    }

    fun addSurface(surface: EmulatorSurfaceView) {
        synchronized(surfacesLock) {
            managedSurfaces.add(surface)
        }
    }

    fun removeSurface(surface: EmulatorSurfaceView) {
        synchronized(surfacesLock) {
            managedSurfaces.remove(surface)
            surfacesPendingRemoval.add(surface)
        }
    }

    fun renderFrame() {
        frameRenderThread.requestFrameRender()
    }

    fun stop() {
        frameRenderThread.requestStop()
        frameRenderThread.quitSafely()
        frameRenderThread.join()
    }

    private inner class FrameRenderThread : HandlerThread("FrameRenderThread") {

        private var handler: Handler? = null
        @Volatile private var running = true

        private val frameRenderCallback = object : FrameRenderCallback {
            override fun renderFrame(isValidFrame: Boolean, frameTextureId: Int) {
                presentFrameWrapper.apply {
                    this.isValidFrame = isValidFrame
                    this.textureId = frameTextureId
                }

                managedSurfaces.forEach {
                    it.doFrame(glContext, presentFrameWrapper)
                }
            }
        }

        override fun onLooperPrepared() {
            handler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_RENDER_FRAME -> renderFrame()
                        MSG_STOP -> stopThread()
                    }
                }
            }
        }

        fun requestFrameRender() {
            handler?.removeMessages(MSG_RENDER_FRAME)
            handler?.sendEmptyMessage(MSG_RENDER_FRAME)
        }

        fun requestStop() {
            running = false
            handler?.sendEmptyMessage(MSG_STOP)
        }

        private fun renderFrame() {
            if (!running)
                return

            synchronized(surfacesLock) {
                surfacesPendingRemoval.forEach {
                    it.stop(glContext)
                }
                surfacesPendingRemoval.clear()

                MelonEmulator.presentFrame(frameRenderCallback)
            }
        }

        private fun stopThread() {
            managedSurfaces.forEach {
                it.stop(glContext)
            }
            surfacesPendingRemoval.forEach {
                it.stop(glContext)
            }
            managedSurfaces.clear()
            surfacesPendingRemoval.clear()

            glContext.release()
            glContext.destroy()
        }
    }

    private companion object {
        const val MSG_RENDER_FRAME = 1
        const val MSG_STOP = 2
    }
}