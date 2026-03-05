package me.magnum.melonds.ui.emulator.render

interface ChoreographerFrameRenderer {

    companion object {
        /**
         * The frame period threshold from which deadlines can be applied. Frame periods under this value should not apply deadlines because the frame-rate is high enough that
         * we can miss some frames and keep a smooth experience.
         */
        const val DEADLINE_FRAME_TIME_THRESHOLD = 13333333L
    }

    fun startRendering()
    fun stopRendering()
}