package me.magnum.melonds.ui.emulator.render

import android.os.Build

object ChoreographerFrameRendererFactory {

    fun createFrameRenderer(frameRenderCoordinator: FrameRenderCoordinator): ChoreographerFrameRenderer {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ChoreographerVSyncFrameRenderer(frameRenderCoordinator)
        } else {
            ChoreographerOldFrameRenderer(frameRenderCoordinator)
        }
    }
}