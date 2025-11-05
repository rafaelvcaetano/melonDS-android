package me.magnum.melonds.ui.emulator.input

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.View
import me.magnum.melonds.MelonEmulator.onScreenRelease
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point

class ExternalTouchscreenInputHandler(
    inputListener: IInputListener,
    private val screenAspectRatio: Float?,
) : BaseInputHandler(inputListener) {

    private val touchPoint: Point = Point()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                inputListener.onKeyPress(Input.TOUCHSCREEN)
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_MOVE -> {
                inputListener.onTouch(normalizeTouchCoordinates(event, v.width, v.height))
            }
            MotionEvent.ACTION_UP -> {
                inputListener.onKeyReleased(Input.TOUCHSCREEN)
                onScreenRelease()
            }
        }
        return true
    }

    private fun normalizeTouchCoordinates(event: MotionEvent, viewWidth: Int, viewHeight: Int): Point {
        var avgX = 0f
        var avgY = 0f
        val coords = PointerCoords()

        for (i in 0 until event.pointerCount) {
            event.getPointerCoords(i, coords)
            avgX += coords.x
            avgY += coords.y
        }
        avgX /= event.pointerCount
        avgY /= event.pointerCount

        if (screenAspectRatio == null) {
            // No aspect ratio. Image fills the entire view
            touchPoint.x = (avgX / viewWidth * 256).toInt().coerceIn(0, 255)
            touchPoint.y = (avgY / viewHeight * 192).toInt().coerceIn(0, 191)
        } else {
            // Aspect ratio is enforced. Compute letterboxed image area
            val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()

            val screenWidth: Float
            val screenHeight: Float
            val offsetX: Float
            val offsetY: Float

            if (viewRatio > screenAspectRatio) {
                // View is wider than screen
                screenHeight = viewHeight.toFloat()
                screenWidth = screenHeight * screenAspectRatio
                offsetX = (viewWidth - screenWidth) / 2f
                offsetY = 0f
            } else {
                // View is taller than screen
                screenWidth = viewWidth.toFloat()
                screenHeight = screenWidth / screenAspectRatio
                offsetX = 0f
                offsetY = (viewHeight - screenHeight) / 2f
            }

            // Convert touch to screen space
            val screenX = ((avgX - offsetX) / screenWidth) * 256f
            val screenY = ((avgY - offsetY) / screenHeight) * 192f

            touchPoint.x = screenX.toInt().coerceIn(0, 255)
            touchPoint.y = screenY.toInt().coerceIn(0, 191)
        }

        return touchPoint
    }
}