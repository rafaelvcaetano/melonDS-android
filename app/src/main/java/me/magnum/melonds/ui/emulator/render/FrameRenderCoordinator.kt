package me.magnum.melonds.ui.emulator.render

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import androidx.core.os.bundleOf
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
            frameRenderThread.requestSurfaceDestruction()
        }
    }

    fun renderFrame(frameDeadlineNanos: Long?) {
        frameRenderThread.requestFrameRender(frameDeadlineNanos)
    }

    fun stop() {
        frameRenderThread.requestStop()
        frameRenderThread.quitSafely()
        frameRenderThread.join()
    }

    private inner class FrameRenderThread : HandlerThread("FrameRenderThread") {

        private var handler: Handler? = null
        @Volatile private var running = true
        private val renderStatistics = RenderStatistics()

        private val frameRenderCallback = object : FrameRenderCallback {
            override fun renderFrame(isValidFrame: Boolean, frameTextureId: Int) {
                val renderStart = System.nanoTime()

                presentFrameWrapper.apply {
                    this.isValidFrame = isValidFrame
                    this.textureId = frameTextureId
                }

                managedSurfaces.forEach {
                    it.doFrame(glContext, presentFrameWrapper)
                }

                val renderDuration = System.nanoTime() - renderStart
                renderStatistics.trackRenderEvent(renderDuration)
            }
        }

        override fun onLooperPrepared() {
            handler = object : Handler(looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        MSG_RENDER_FRAME -> renderFrame(msg.data.getLong(MSG_RENDER_FRAME_FRAME_DEADLINE_NS))
                        MSG_DESTROY_SURFACES -> destroySurfaces()
                        MSG_STOP -> stopThread()
                    }
                }
            }
        }

        fun requestFrameRender(frameDeadlineNanos: Long?) {
            handler?.removeMessages(MSG_RENDER_FRAME)
            handler?.obtainMessage(MSG_RENDER_FRAME)?.let {
                it.data = bundleOf(MSG_RENDER_FRAME_FRAME_DEADLINE_NS to (frameDeadlineNanos ?: 0L))
                handler?.sendMessage(it)
            }
        }

        fun requestSurfaceDestruction() {
            handler?.removeMessages(MSG_DESTROY_SURFACES)
            handler?.sendEmptyMessage(MSG_DESTROY_SURFACES)
        }

        fun requestStop() {
            running = false
            handler?.sendEmptyMessage(MSG_STOP)
        }

        private fun renderFrame(frameDeadlineNanos: Long) {
            if (!running)
                return

            synchronized(surfacesLock) {
                val deadline = if (frameDeadlineNanos > 0) {
                    // Use 2 times the average render duration to be safe. A large margin is required because only the CPU time is being measured and the GPU is performing
                    // additional work, which is not being measured. As a future improvement, GPU time can be taken into account as well to obtain a more accurate deadline
                    frameDeadlineNanos - (renderStatistics.getMeanRenderDurationNs() * 2f).toLong()
                } else {
                    0L
                }
                MelonEmulator.presentFrame(deadline, frameRenderCallback)
            }
        }

        private fun destroySurfaces() {
            synchronized(surfacesLock) {
                surfacesPendingRemoval.forEach {
                    it.stop(glContext)
                }
                surfacesPendingRemoval.clear()
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

    private class RenderStatistics {
        private var meanRenderDurationNs = 0L
        private var collectedSamples = 0

        fun trackRenderEvent(durationNs: Long) {
            if (collectedSamples < 60) {
                collectedSamples++
            }

            meanRenderDurationNs = (meanRenderDurationNs * (collectedSamples - 1) + durationNs) / collectedSamples
        }

        fun getMeanRenderDurationNs(): Long {
            return meanRenderDurationNs
        }
    }

    private companion object {
        const val MSG_RENDER_FRAME = 1
        const val MSG_DESTROY_SURFACES = 2
        const val MSG_STOP = 3

        const val MSG_RENDER_FRAME_FRAME_DEADLINE_NS = "frame-deadline"
    }
}